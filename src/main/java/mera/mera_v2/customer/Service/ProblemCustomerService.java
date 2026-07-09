package mera.mera_v2.customer.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import mera.mera_v2.customer.DTO.ProblemCustomerRow;
import mera.mera_v2.customer.DTO.ProblemCustomerView;
import mera.mera_v2.repository.ProblemCustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Logic trang "Số thả nổi": validate biên, tính mốc thời gian, truy vấn candidate,
 * phân nhóm A/B/C và phân trang. Truy vấn native chạy 1 lần cho toàn bộ tập (giới hạn
 * bởi {@link #FETCH_CAP}); phân trang được thực hiện trên list đã phân nhóm để giữ
 * chính xác các số đếm nhóm trên toàn bộ tập kết quả.
 */
@Service
public class ProblemCustomerService {

    private static final Logger log = LoggerFactory.getLogger(ProblemCustomerService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    public static final int DEFAULT_MIN_NOTES = 5;
    public static final int DEFAULT_HOURS = 72;
    public static final int DEFAULT_MAX_DAYS = 30;
    public static final int DEFAULT_STALE_MONTHS = 5;

    /** Trần số bản ghi lấy về từ DB cho 1 lần truy vấn (bảo vệ query nặng). */
    public static final int FETCH_CAP = 5000;

    /** Biên mặc định cho khoảng Ngày tạo KH khi người dùng không chọn. */
    private static final LocalDateTime DATE_LOWER_BOUND = LocalDate.of(1970, 1, 1).atStartOfDay();
    private static final LocalDateTime DATE_UPPER_BOUND = LocalDate.of(2999, 12, 31).atStartOfDay();

    /** Kích thước trang cho phép (khớp dropdown ở UI). */
    public static final List<Integer> PAGE_SIZE_OPTIONS = List.of(30, 50, 100, 150, 200, 500, 1000);
    public static final int DEFAULT_PAGE_SIZE = 50;

    private final ProblemCustomerRepository repository;

    public ProblemCustomerService(ProblemCustomerRepository repository) {
        this.repository = repository;
    }

    /** Truy vấn + phân nhóm toàn bộ tập cảnh báo (giới hạn {@link #FETCH_CAP}). */
    public ProblemCustomerResult compute(int minNotes, int hours, int maxDays, int months,
                                         String fromDate, String toDate) {
        // Validate biên
        if (minNotes < 1) minNotes = 1;
        if (hours < 1) hours = 1;
        if (maxDays < 1) maxDays = 1;
        if (months < 1) months = 1;
        // Cửa sổ nhóm B phải rộng hơn ngưỡng giờ
        if ((long) maxDays * 24 <= hours) {
            maxDays = (hours / 24) + 1;
        }

        // Khoảng Ngày tạo KH: from = đầu ngày; to = đầu ngày kế tiếp (bao trọn ngày chọn)
        LocalDate from = parseDate(fromDate);
        LocalDate to = parseDate(toDate);
        LocalDateTime fromBound = (from != null) ? from.atStartOfDay() : DATE_LOWER_BOUND;
        LocalDateTime toBound = (to != null) ? to.plusDays(1).atStartOfDay() : DATE_UPPER_BOUND;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime threshold = now.minusHours(hours);
        LocalDateTime oldest = now.minusDays(maxDays);
        LocalDateTime stale = now.minusMonths(months);

        ProblemCustomerResult result = new ProblemCustomerResult();
        result.setMinNotes(minNotes);
        result.setHours(hours);
        result.setMaxDays(maxDays);
        result.setMonths(months);
        result.setFromDate(from != null ? from.toString() : "");
        result.setToDate(to != null ? to.toString() : "");
        result.setGeneratedAt(now.format(DATE_FMT));

        try {
            List<ProblemCustomerView> views =
                    repository.findProblemCustomers(minNotes, threshold, oldest, stale, fromBound, toBound, FETCH_CAP);

            List<ProblemCustomerRow> rows = new ArrayList<>(views.size());
            int groupACount = 0, groupBCount = 0, groupCCount = 0;

            for (ProblemCustomerView v : views) {
                int noteCount = v.getNoteCount() != null ? v.getNoteCount() : 0;
                LocalDateTime insertedAt = v.getInsertedAt();
                boolean hasReceived = v.getHasReceivedOrder() != null && v.getHasReceivedOrder() > 0;
                LocalDateTime lastReceivedAt = v.getLastReceivedAt();

                boolean groupA = !hasReceived && noteCount >= minNotes;
                boolean groupB = !hasReceived
                        && insertedAt != null
                        && insertedAt.isBefore(threshold)
                        && !insertedAt.isBefore(oldest)
                        && noteCount >= 1;
                boolean groupC = hasReceived && lastReceivedAt != null && lastReceivedAt.isBefore(stale);

                if (groupA) groupACount++;
                if (groupB) groupBCount++;
                if (groupC) groupCCount++;

                List<String> reasons = new ArrayList<>(2);
                if (groupA) reasons.add("Chăm " + noteCount + " ghi chú nhưng chưa có đơn xử lý / đã nhận");
                if (groupB) reasons.add("Quá " + hours + "h chưa có đơn xử lý / đã nhận");
                if (groupC) reasons.add("Đơn đã nhận gần nhất quá " + months + " tháng");

                ProblemCustomerRow row = new ProblemCustomerRow();
                row.setCustomerId(v.getCustomerId());
                row.setName(v.getName());
                row.setPhone(v.getPhone());
                row.setNoteCount(noteCount);
                row.setOrderCount(v.getOrderCount() != null ? v.getOrderCount() : 0);
                row.setSucceedOrderCount(v.getSucceedOrderCount() != null ? v.getSucceedOrderCount() : 0);
                row.setInsertedAt(insertedAt != null ? insertedAt.format(DATE_FMT) : "—");
                row.setLastNoteAt(v.getLastNoteAt() != null ? v.getLastNoteAt().format(DATE_FMT) : "—");
                row.setLastReceivedAt(lastReceivedAt != null ? lastReceivedAt.format(DATE_FMT) : "—");
                row.setGroupA(groupA);
                row.setGroupB(groupB);
                row.setGroupC(groupC);
                row.setReason(String.join(" · ", reasons));
                rows.add(row);
            }

            result.setRows(rows);
            result.setTotal(rows.size());
            result.setGroupACount(groupACount);
            result.setGroupBCount(groupBCount);
            result.setGroupCCount(groupCCount);
            result.setCapped(rows.size() >= FETCH_CAP);
        } catch (Exception e) {
            log.error("Lỗi truy vấn khách hàng cảnh báo", e);
            result.setRows(List.of());
            result.setTotal(0);
            result.setGroupACount(0);
            result.setGroupBCount(0);
            result.setGroupCCount(0);
            result.setCapped(false);
            result.setErrorMessage("Không truy vấn được dữ liệu: " + e.getMessage());
        }

        return result;
    }

    /** Parse chuỗi ngày ISO (yyyy-MM-dd) từ input type=date; trả null nếu rỗng/không hợp lệ. */
    private LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value.trim());
        } catch (Exception e) {
            log.warn("Bỏ qua ngày lọc không hợp lệ: {}", value);
            return null;
        }
    }
}
