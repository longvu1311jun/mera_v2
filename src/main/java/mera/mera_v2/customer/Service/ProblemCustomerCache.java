package mera.mera_v2.customer.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import jakarta.annotation.PreDestroy;
import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Cache TTL cho kết quả "Số thả nổi".
 *
 * <p>Query gốc rất nặng và DB sập khi nhiều query chạy song song (mỗi khoảng ngày là một
 * cache key mới → mỗi lần đổi filter là một query nặng). Vì vậy ngoài cache theo key,
 * component này ép <b>toàn hệ thống chỉ chạy 1 query nặng tại một thời điểm</b>
 * ({@link #heavyQueryPermit}): các request key mới xếp hàng tuần tự thay vì đè nhau làm
 * DB nghẽn; chờ quá {@link #ACQUIRE_TIMEOUT_S} giây thì trả thông báo "hệ thống bận" thay
 * vì treo vô hạn. Query cũng có timeout 90s ở tầng repository.</p>
 *
 * <p>Áp dụng <b>stale-while-revalidate</b>: khi hết hạn TTL, trả kết quả cũ ngay và làm mới
 * ở luồng nền → người dùng không phải chờ (trừ lần đầu cho mỗi bộ lọc). Bộ lọc mặc định được
 * hâm nóng sẵn lúc khởi động.</p>
 */
@Component
public class ProblemCustomerCache {

    private static final Logger log = LoggerFactory.getLogger(ProblemCustomerCache.class);

    /** Thời gian coi kết quả là "tươi" (2 phút). */
    private static final long TTL_MS = 120_000;
    /** Xoá entry không được truy cập lâu hơn mốc này (20 phút) để giới hạn bộ nhớ. */
    private static final long EVICT_IDLE_MS = 20 * 60_000;
    private static final int MAX_ENTRIES = 500;
    /** Chờ tối đa đến lượt chạy query nặng trước khi trả "hệ thống bận" (giây). */
    private static final long ACQUIRE_TIMEOUT_S = 120;

    private final ProblemCustomerService service;
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /** Chỉ 1 query nặng được chạy tại một thời điểm trên toàn hệ thống (cold + refresh). */
    private final Semaphore heavyQueryPermit = new Semaphore(1, true);

    private final ExecutorService refreshPool = Executors.newFixedThreadPool(1, r -> {
        Thread t = new Thread(r, "pc-cache-refresh");
        t.setDaemon(true);
        return t;
    });

    public ProblemCustomerCache(ProblemCustomerService service) {
        this.service = service;
    }

    private static final class Entry {
        volatile ProblemCustomerResult result;
        volatile long computedAt;
        volatile long lastAccess;
        final AtomicBoolean refreshing = new AtomicBoolean(false);
    }

    /** Lưu kết quả; nếu là lỗi thì đánh dấu hết hạn ngay để lần sau thử làm mới sớm. */
    private static void store(Entry e, ProblemCustomerResult r) {
        e.result = r;
        e.computedAt = (r.getErrorMessage() != null) ? 0L : System.currentTimeMillis();
    }

    /** Chạy query nặng dưới permit toàn cục — đảm bảo không bao giờ có 2 query chạy song song. */
    private ProblemCustomerResult computeSerialized(int minNotes, int hours, int maxDays, int months,
                                                    String fromDate, String toDate) {
        boolean acquired = false;
        try {
            acquired = heavyQueryPermit.tryAcquire(ACQUIRE_TIMEOUT_S, TimeUnit.SECONDS);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            ProblemCustomerResult busy = new ProblemCustomerResult();
            busy.setRows(List.of());
            busy.setErrorMessage("Hệ thống đang bận truy vấn dữ liệu, vui lòng thử lại sau ít phút.");
            return busy;
        }
        try {
            return service.compute(minNotes, hours, maxDays, months, fromDate, toDate);
        } finally {
            heavyQueryPermit.release();
        }
    }

    public ProblemCustomerResult get(int minNotes, int hours, int maxDays, int months,
                                     String fromDate, String toDate) {
        final String key = minNotes + "|" + hours + "|" + maxDays + "|" + months
                + "|" + (fromDate == null ? "" : fromDate) + "|" + (toDate == null ? "" : toDate);
        Entry e = cache.computeIfAbsent(key, k -> new Entry());
        e.lastAccess = System.currentTimeMillis();

        if (e.result == null) {
            // Cold start: tính đồng bộ nhưng chỉ 1 thread tính cho mỗi key (single-flight),
            // và toàn hệ thống chỉ 1 query nặng chạy tại một thời điểm.
            synchronized (e) {
                if (e.result == null) {
                    ProblemCustomerResult r = computeSerialized(minNotes, hours, maxDays, months, fromDate, toDate);
                    // Không cache kết quả "bận" như kết quả thật — cho phép thử lại ngay
                    if (r.getErrorMessage() != null) {
                        return r;
                    }
                    store(e, r);
                }
            }
            return e.result;
        }

        // Đã có kết quả; nếu hết hạn -> trả kết quả cũ ngay + làm mới ở nền (chỉ 1 lần/key)
        if (System.currentTimeMillis() - e.computedAt >= TTL_MS
                && e.refreshing.compareAndSet(false, true)) {
            refreshPool.submit(() -> {
                try {
                    ProblemCustomerResult r = computeSerialized(minNotes, hours, maxDays, months, fromDate, toDate);
                    if (r.getErrorMessage() == null) {
                        store(e, r);
                    }
                } catch (Exception ex) {
                    log.warn("Làm mới cache số thả nổi lỗi: {}", ex.getMessage());
                } finally {
                    e.refreshing.set(false);
                }
            });
        }
        return e.result;
    }

    /** Hâm nóng bộ lọc mặc định lúc khởi động để user đầu tiên không phải chờ cold start. */
    @EventListener(ApplicationReadyEvent.class)
    public void warmUpDefault() {
        refreshPool.submit(() -> {
            try {
                log.info("Hâm nóng cache số thả nổi (bộ lọc mặc định)...");
                get(ProblemCustomerService.DEFAULT_MIN_NOTES, ProblemCustomerService.DEFAULT_HOURS,
                        ProblemCustomerService.DEFAULT_MAX_DAYS, ProblemCustomerService.DEFAULT_STALE_MONTHS,
                        null, null);
                log.info("Hâm nóng cache số thả nổi xong.");
            } catch (Exception ex) {
                log.warn("Hâm nóng cache số thả nổi lỗi: {}", ex.getMessage());
            }
        });
    }

    /** Dọn entry lâu không dùng + chặn map phình quá lớn (nhiều khoảng ngày khác nhau). */
    @Scheduled(fixedRate = 300_000)
    public void cleanup() {
        long now = System.currentTimeMillis();
        cache.entrySet().removeIf(en -> now - en.getValue().lastAccess > EVICT_IDLE_MS);
        int over = cache.size() - MAX_ENTRIES;
        if (over > 0) {
            cache.entrySet().stream()
                    .sorted((a, b) -> Long.compare(a.getValue().lastAccess, b.getValue().lastAccess))
                    .limit(over)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(cache::remove);
        }
    }

    @PreDestroy
    public void shutdown() {
        refreshPool.shutdownNow();
    }
}
