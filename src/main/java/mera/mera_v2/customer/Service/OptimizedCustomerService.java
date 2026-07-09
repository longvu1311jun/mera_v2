package mera.mera_v2.customer.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import jakarta.annotation.PreDestroy;
import mera.mera_v2.customer.DTO.OptimizedCustomerResult;
import mera.mera_v2.customer.DTO.OptimizedCustomerRow;
import mera.mera_v2.customer.DTO.OrderPresenceView;
import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import mera.mera_v2.customer.DTO.ProblemCustomerRow;
import mera.mera_v2.entity.OptimizedCustomer;
import mera.mera_v2.entity.ProblemCustomerTracking;
import mera.mera_v2.repository.OptimizedCustomerRepository;
import mera.mera_v2.repository.ProblemCustomerRepository;
import mera.mera_v2.repository.ProblemCustomerTrackingRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Đối soát danh sách "Số thả nổi" theo chu kỳ để phát hiện khách đã được tối ưu.
 *
 * <p>Trang Số thả nổi tính động (query nặng) nên không phát ra sự kiện "khách rời danh sách".
 * Service này chụp snapshot tập thành viên hiện tại ({@link ProblemCustomerTracking}); mỗi lần
 * chạy nó so sánh với snapshot lần trước:</p>
 * <ul>
 *   <li>Khách mới xuất hiện → thêm vào snapshot (ghi mốc first_seen_at).</li>
 *   <li>Khách vừa rời danh sách → kiểm tra hiện có đơn đang xử lý / đã nhận không. Nếu có →
 *       coi là "đã tối ưu", ghi vào {@link OptimizedCustomer}. Nếu không (rời vì xoá ghi chú,
 *       hết cửa sổ nhóm B…) → chỉ xoá khỏi snapshot, không lưu.</li>
 * </ul>
 *
 * <p>Dùng lại {@link ProblemCustomerCache} (bộ lọc mặc định) để không chạy thêm query nặng.
 * Nếu kết quả lỗi hoặc chạm trần (capped) thì bỏ qua lượt đối soát để tránh nhận nhầm khách
 * "biến mất do cắt trần" thành đã rời danh sách.</p>
 */
@Service
public class OptimizedCustomerService {

    private static final Logger log = LoggerFactory.getLogger(OptimizedCustomerService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    /** Tự tạo bảng (Flyway chưa được nối trong dự án, ddl-auto=none). */
    private static final String DDL_TRACKING = """
        CREATE TABLE IF NOT EXISTS problem_customer_tracking (
            customer_id            VARCHAR(64)  NOT NULL PRIMARY KEY,
            name                   VARCHAR(255),
            phone                  VARCHAR(32),
            note_count             INT          NOT NULL DEFAULT 0,
            order_count            INT          NOT NULL DEFAULT 0,
            succeed_order_count    INT          NOT NULL DEFAULT 0,
            reason                 VARCHAR(500),
            customer_created_text  VARCHAR(32),
            first_seen_at          DATETIME,
            last_seen_at           DATETIME,
            INDEX idx_pct_last_seen (last_seen_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;
    private static final String DDL_OPTIMIZED = """
        CREATE TABLE IF NOT EXISTS optimized_customer (
            id                     BIGINT       AUTO_INCREMENT PRIMARY KEY,
            customer_id            VARCHAR(64)  NOT NULL UNIQUE,
            name                   VARCHAR(255),
            phone                  VARCHAR(32),
            note_count             INT          NOT NULL DEFAULT 0,
            reason                 VARCHAR(500),
            customer_created_text  VARCHAR(32),
            order_count_before     INT          NOT NULL DEFAULT 0,
            succeed_before         INT          NOT NULL DEFAULT 0,
            order_count_after      INT          NOT NULL DEFAULT 0,
            succeed_after          INT          NOT NULL DEFAULT 0,
            first_seen_at          DATETIME,
            optimized_at           DATETIME,
            INDEX idx_oc_optimized_at (optimized_at)
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
        """;

    private final ProblemCustomerCache cache;
    private final ProblemCustomerRepository problemRepository;
    private final ProblemCustomerTrackingRepository trackingRepository;
    private final OptimizedCustomerRepository optimizedRepository;
    private final JdbcTemplate jdbcTemplate;

    /** Chạy đối soát trên luồng riêng để không giữ luồng scheduler dùng chung (query có thể lâu). */
    private final ExecutorService reconcilePool = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "optimized-reconcile");
        t.setDaemon(true);
        return t;
    });
    /** Chống chạy chồng khi lượt trước chưa xong. */
    private final AtomicBoolean running = new AtomicBoolean(false);

    public OptimizedCustomerService(ProblemCustomerCache cache,
                                    ProblemCustomerRepository problemRepository,
                                    ProblemCustomerTrackingRepository trackingRepository,
                                    OptimizedCustomerRepository optimizedRepository,
                                    JdbcTemplate jdbcTemplate) {
        this.cache = cache;
        this.problemRepository = problemRepository;
        this.trackingRepository = trackingRepository;
        this.optimizedRepository = optimizedRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureSchema() {
        try {
            jdbcTemplate.execute(DDL_TRACKING);
            jdbcTemplate.execute(DDL_OPTIMIZED);
            log.info("Bảng lưu trữ khách đã tối ưu đã sẵn sàng.");
        } catch (Exception e) {
            log.error("Không tạo được bảng lưu trữ khách đã tối ưu: {}", e.getMessage());
        }
    }

    /**
     * Kích hoạt đối soát định kỳ (mỗi 1 giờ, lần đầu sau 90 giây để cache kịp hâm nóng).
     * Chỉ đẩy việc sang luồng nền rồi trả về ngay — không giữ luồng scheduler.
     */
    @Scheduled(fixedDelayString = "3600000", initialDelayString = "90000")
    public void scheduleReconcile() {
        if (!running.compareAndSet(false, true)) {
            log.info("Bỏ qua lượt đối soát khách tối ưu: lượt trước chưa hoàn tất.");
            return;
        }
        reconcilePool.submit(() -> {
            try {
                reconcile();
            } finally {
                running.set(false);
            }
        });
    }

    /** Thân đối soát (chạy trên luồng nền). */
    void reconcile() {
        try {
            ProblemCustomerResult result = cache.get(
                    ProblemCustomerService.DEFAULT_MIN_NOTES, ProblemCustomerService.DEFAULT_HOURS,
                    ProblemCustomerService.DEFAULT_MAX_DAYS, ProblemCustomerService.DEFAULT_STALE_MONTHS,
                    null, null);

            if (result == null || result.getErrorMessage() != null || result.getRows() == null) {
                log.warn("Bỏ qua đối soát khách tối ưu: kết quả Số thả nổi không khả dụng.");
                return;
            }
            // Khi danh sách chạm trần, khách nằm dưới ngưỡng có thể tạm biến mất rồi quay lại (first_seen
            // bị đặt lại). Không gây archive sai vì việc ghi nhận đã được checkOrderPresence xác thực
            // (chỉ archive khi khách thực sự đã có đơn), nên vẫn tiếp tục đối soát.
            if (result.isCapped()) {
                log.warn("Danh sách Số thả nổi chạm trần {} dòng khi đối soát khách tối ưu — mốc 'vào danh sách' của khách gần ngưỡng có thể bị đặt lại.",
                        ProblemCustomerService.FETCH_CAP);
            }

            LocalDateTime now = LocalDateTime.now();
            List<ProblemCustomerRow> rows = result.getRows();
            Set<String> currentIds = rows.stream()
                    .map(ProblemCustomerRow::getCustomerId)
                    .collect(Collectors.toCollection(HashSet::new));

            // Snapshot trước đó
            Map<String, ProblemCustomerTracking> previous = trackingRepository.findAll().stream()
                    .collect(Collectors.toMap(ProblemCustomerTracking::getCustomerId, t -> t, (a, b) -> a));

            // 1) Cập nhật snapshot cho tập hiện tại
            List<ProblemCustomerTracking> toUpsert = new ArrayList<>(rows.size());
            for (ProblemCustomerRow r : rows) {
                ProblemCustomerTracking t = previous.get(r.getCustomerId());
                if (t == null) {
                    t = new ProblemCustomerTracking();
                    t.setCustomerId(r.getCustomerId());
                    t.setFirstSeenAt(now);
                }
                t.setName(r.getName());
                t.setPhone(r.getPhone());
                t.setNoteCount(r.getNoteCount());
                t.setOrderCount(r.getOrderCount());
                t.setSucceedOrderCount(r.getSucceedOrderCount());
                t.setReason(r.getReason());
                t.setCustomerCreatedText(r.getInsertedAt());
                t.setLastSeenAt(now);
                toUpsert.add(t);
            }
            trackingRepository.saveAll(toUpsert);

            // 2) Khách đã rời danh sách = có trong snapshot cũ nhưng không còn trong tập hiện tại
            List<ProblemCustomerTracking> departed = previous.values().stream()
                    .filter(t -> !currentIds.contains(t.getCustomerId()))
                    .toList();
            if (departed.isEmpty()) {
                return;
            }

            processDeparted(departed, now);
        } catch (Exception e) {
            log.error("Lỗi đối soát khách đã tối ưu", e);
        }
    }

    /** Với tập khách vừa rời danh sách: ai đã có đơn thì lưu archive, còn lại chỉ xoá snapshot. */
    private void processDeparted(List<ProblemCustomerTracking> departed, LocalDateTime now) {
        List<String> ids = departed.stream().map(ProblemCustomerTracking::getCustomerId).toList();

        Map<String, OrderPresenceView> presence = problemRepository.checkOrderPresence(ids).stream()
                .collect(Collectors.toMap(OrderPresenceView::getCustomerId, v -> v, (a, b) -> a));

        List<OptimizedCustomer> toArchive = new ArrayList<>();
        int optimizedCount = 0;
        for (ProblemCustomerTracking t : departed) {
            OrderPresenceView pv = presence.get(t.getCustomerId());
            int active = (pv != null && pv.getActiveCount() != null) ? pv.getActiveCount() : 0;
            int received = (pv != null && pv.getReceivedCount() != null) ? pv.getReceivedCount() : 0;
            int total = (pv != null && pv.getTotalCount() != null) ? pv.getTotalCount() : 0;

            if (active > 0 || received > 0) {
                OptimizedCustomer oc = optimizedRepository.findByCustomerId(t.getCustomerId())
                        .orElseGet(OptimizedCustomer::new);
                oc.setCustomerId(t.getCustomerId());
                oc.setName(t.getName());
                oc.setPhone(t.getPhone());
                oc.setNoteCount(t.getNoteCount());
                oc.setReason(t.getReason());
                oc.setCustomerCreatedText(t.getCustomerCreatedText());
                oc.setOrderCountBefore(t.getOrderCount());
                oc.setSucceedBefore(t.getSucceedOrderCount());
                oc.setOrderCountAfter(total);
                oc.setSucceedAfter(received);
                oc.setFirstSeenAt(t.getFirstSeenAt());
                oc.setOptimizedAt(now);
                toArchive.add(oc);
                optimizedCount++;
            }
        }

        if (!toArchive.isEmpty()) {
            optimizedRepository.saveAll(toArchive);
        }
        // Dù đã tối ưu hay rời vì lý do khác, khách đều không còn trong danh sách → xoá snapshot
        trackingRepository.deleteAllById(ids);

        log.info("Đối soát Số thả nổi: {} khách rời danh sách, trong đó {} khách đã tối ưu (lên đơn).",
                departed.size(), optimizedCount);
    }

    @PreDestroy
    public void shutdown() {
        reconcilePool.shutdownNow();
    }

    /** Dữ liệu cho trang hiển thị "Khách đã tối ưu" (toàn bộ, client tự phân trang/tìm kiếm). */
    public OptimizedCustomerResult listOptimized() {
        OptimizedCustomerResult result = new OptimizedCustomerResult();
        LocalDateTime now = LocalDateTime.now();
        result.setGeneratedAt(now.format(DATE_FMT));
        try {
            LocalDateTime startOfToday = LocalDate.now().atStartOfDay();
            List<OptimizedCustomer> entities = optimizedRepository.findAllByOrderByOptimizedAtDesc();
            List<OptimizedCustomerRow> rows = new ArrayList<>(entities.size());
            int optimizedToday = 0;
            for (OptimizedCustomer oc : entities) {
                OptimizedCustomerRow row = new OptimizedCustomerRow();
                row.setCustomerId(oc.getCustomerId());
                row.setName(oc.getName());
                row.setPhone(oc.getPhone());
                row.setNoteCount(oc.getNoteCount());
                row.setReason(oc.getReason());
                row.setCustomerCreatedText(oc.getCustomerCreatedText() != null ? oc.getCustomerCreatedText() : "—");
                row.setOrderCountBefore(oc.getOrderCountBefore());
                row.setSucceedBefore(oc.getSucceedBefore());
                row.setOrderCountAfter(oc.getOrderCountAfter());
                row.setSucceedAfter(oc.getSucceedAfter());
                row.setFirstSeenAt(oc.getFirstSeenAt() != null ? oc.getFirstSeenAt().format(DATE_FMT) : "—");
                row.setOptimizedAt(oc.getOptimizedAt() != null ? oc.getOptimizedAt().format(DATE_FMT) : "—");
                rows.add(row);
                if (oc.getOptimizedAt() != null && !oc.getOptimizedAt().isBefore(startOfToday)) {
                    optimizedToday++;
                }
            }
            result.setRows(rows);
            result.setTotal(rows.size());
            result.setOptimizedToday(optimizedToday);
        } catch (Exception e) {
            log.error("Lỗi tải danh sách khách đã tối ưu", e);
            result.setRows(List.of());
            result.setTotal(0);
            result.setOptimizedToday(0);
            result.setErrorMessage("Không tải được dữ liệu: " + e.getMessage());
        }
        return result;
    }
}
