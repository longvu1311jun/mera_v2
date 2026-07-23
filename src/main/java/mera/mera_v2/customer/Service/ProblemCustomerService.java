package mera.mera_v2.customer.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import mera.mera_v2.customer.DTO.ProblemCustomerResult;
import mera.mera_v2.customer.DTO.ProblemCustomerRow;
import mera.mera_v2.customer.DTO.ProblemCustomerView;
import mera.mera_v2.repository.ProblemCustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Logic trang "Số thả nổi": validate biên, tính mốc thời gian, đọc candidate từ bảng facts
 * precompute ({@code problem_customer_facts}, làm mới bởi {@link ProblemCustomerFactsService}),
 * phân nhóm A/B/C và phân trang. Đọc chỉ là 1 SELECT phẳng đã đánh index (giới hạn bởi
 * {@link #FETCH_CAP}); phân trang thực hiện trên list đã phân nhóm để giữ chính xác các số
 * đếm nhóm trên toàn bộ tập kết quả.
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

    // ---- Biểu thức phân nhóm trên bảng facts (dùng cho query đếm + query trang server-side) ----
    // Khách bị xóa khỏi nhóm A/B/C được lưu ở problem_customer_group_removed; JOIN dưới đây phơi ra
    // các cờ rmA/rmB/rmC (1 = đã xóa khỏi nhóm đó) để loại khỏi predicate + số đếm. Nhóm D không dùng
    // bảng này (xóa nhóm D = xóa khỏi refused_care, xem ProblemGroupRemovalService).
    private static final String FROM_FACTS =
        "problem_customer_facts LEFT JOIN ("
        + "SELECT customer_id AS rid,"
        + " MAX(CASE WHEN grp = 'A' THEN 1 ELSE 0 END) rmA,"
        + " MAX(CASE WHEN grp = 'B' THEN 1 ELSE 0 END) rmB,"
        + " MAX(CASE WHEN grp = 'C' THEN 1 ELSE 0 END) rmC"
        + " FROM problem_customer_group_removed GROUP BY customer_id"
        + ") rm ON rm.rid = customer_id";

    private static final String DATE_PRED = "inserted_at >= :fromDate AND inserted_at < :toDate";
    private static final String PRED_A =
        "(refused = 0 AND active_order_count = 0 AND has_received = 0 AND note_count >= :minNotes "
        + "AND COALESCE(rmA, 0) = 0)";
    private static final String PRED_B =
        "(refused = 0 AND active_order_count = 0 AND has_received = 0 "
        + "AND inserted_at < :threshold AND inserted_at >= :oldest AND note_count >= 1 "
        + "AND COALESCE(rmB, 0) = 0)";
    private static final String PRED_C =
        "(refused = 0 AND active_order_count = 0 AND has_received = 1 AND last_received_at < :stale "
        + "AND COALESCE(rmC, 0) = 0)";
    private static final String PRED_D =
        "(refused = 1 AND (last_received_at IS NULL OR last_received_at <= refused_uploaded_at))";
    private static final String PRED_ALL = "(" + PRED_A + " OR " + PRED_B + " OR " + PRED_C + " OR " + PRED_D + ")";

    /** Cột được phép sort (whitelist — chống SQL injection ở ORDER BY). */
    private static final Map<String, String> SORT_COLS = Map.of(
        "noteCount", "note_count",
        "orderCount", "order_count",
        "succeedOrderCount", "succeed_order_count",
        "insertedAt", "inserted_at",
        "lastNoteAt", "last_note_at",
        "lastReceivedAt", "last_received_at");

    private final ProblemCustomerRepository repository;
    private final NamedParameterJdbcTemplate namedJdbc;

    public ProblemCustomerService(ProblemCustomerRepository repository,
                                  NamedParameterJdbcTemplate namedJdbc) {
        this.repository = repository;
        this.namedJdbc = namedJdbc;
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
                    repository.findProblemCustomerFacts(minNotes, threshold, oldest, stale, fromBound, toBound, FETCH_CAP);
            boolean mainCapped = views.size() >= FETCH_CAP;

            // Nhóm D "Từ chối chăm" đè A/B/C: khách trong danh sách này chỉ hiện ở nhóm D
            java.util.Set<String> refusedIds = new java.util.HashSet<>(repository.findRefusedCareIds());

            List<ProblemCustomerRow> rows = new ArrayList<>(views.size());
            int groupACount = 0, groupBCount = 0, groupCCount = 0, groupDCount = 0;

            for (ProblemCustomerView v : views) {
                if (refusedIds.contains(v.getCustomerId())) continue; // để nhóm D xử lý

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

            // Nhóm D "Từ chối chăm": làm giàu thông tin và thêm vào danh sách (không cần điều kiện A/B/C)
            if (!refusedIds.isEmpty()) {
                List<ProblemCustomerView> refused =
                        repository.findRefusedCareCustomers(fromBound, toBound, FETCH_CAP);
                for (ProblemCustomerView v : refused) {
                    LocalDateTime insertedAt = v.getInsertedAt();
                    LocalDateTime lastReceivedAt = v.getLastReceivedAt();
                    ProblemCustomerRow row = new ProblemCustomerRow();
                    row.setCustomerId(v.getCustomerId());
                    row.setName(v.getName());
                    row.setPhone(v.getPhone());
                    row.setNoteCount(v.getNoteCount() != null ? v.getNoteCount() : 0);
                    row.setOrderCount(v.getOrderCount() != null ? v.getOrderCount() : 0);
                    row.setSucceedOrderCount(v.getSucceedOrderCount() != null ? v.getSucceedOrderCount() : 0);
                    row.setInsertedAt(insertedAt != null ? insertedAt.format(DATE_FMT) : "—");
                    row.setLastNoteAt(v.getLastNoteAt() != null ? v.getLastNoteAt().format(DATE_FMT) : "—");
                    row.setLastReceivedAt(lastReceivedAt != null ? lastReceivedAt.format(DATE_FMT) : "—");
                    row.setGroupD(true);
                    row.setReason("Từ chối chăm");
                    rows.add(row);
                    groupDCount++;
                }
                // Giữ thứ tự mặc định nhất quán: số ghi chú giảm dần
                rows.sort((a, b) -> Integer.compare(b.getNoteCount(), a.getNoteCount()));
            }

            result.setRows(rows);
            result.setTotal(rows.size());
            result.setGroupACount(groupACount);
            result.setGroupBCount(groupBCount);
            result.setGroupCCount(groupCCount);
            result.setGroupDCount(groupDCount);
            result.setCapped(mainCapped);
        } catch (Exception e) {
            log.error("Lỗi truy vấn khách hàng cảnh báo", e);
            result.setRows(List.of());
            result.setTotal(0);
            result.setGroupACount(0);
            result.setGroupBCount(0);
            result.setGroupCCount(0);
            result.setGroupDCount(0);
            result.setCapped(false);
            result.setErrorMessage("Không truy vấn được dữ liệu: " + e.getMessage());
        }

        return result;
    }

    /**
     * Đọc 1 TRANG (phân trang server-side) từ bảng facts precompute — nhẹ vì bảng phẳng có index.
     * Trả về: rows của trang hiện tại, số đếm nhóm A/B/C/D + tổng (theo khoảng ngày, cho stat card),
     * và {@code matched}/{@code page}/{@code pageSize}/{@code totalPages} cho bộ lọc hiện tại.
     */
    public ProblemCustomerResult computePage(int minNotes, int hours, int maxDays, int months,
                                             String fromDate, String toDate, String group, String search,
                                             String sort, String dir, int page, int pageSize) {
        if (minNotes < 1) minNotes = 1;
        if (hours < 1) hours = 1;
        if (maxDays < 1) maxDays = 1;
        if (months < 1) months = 1;
        if ((long) maxDays * 24 <= hours) maxDays = (hours / 24) + 1;
        if (page < 1) page = 1;
        if (!PAGE_SIZE_OPTIONS.contains(pageSize)) pageSize = DEFAULT_PAGE_SIZE;

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
        result.setPageSize(pageSize);

        try {
            MapSqlParameterSource base = new MapSqlParameterSource()
                    .addValue("fromDate", Timestamp.valueOf(fromBound))
                    .addValue("toDate", Timestamp.valueOf(toBound))
                    .addValue("minNotes", minNotes)
                    .addValue("threshold", Timestamp.valueOf(threshold))
                    .addValue("oldest", Timestamp.valueOf(oldest))
                    .addValue("stale", Timestamp.valueOf(stale));

            // 1) Đếm nhóm A/B/C/D + tổng (theo khoảng ngày) cho stat card
            String countsSql = "SELECT "
                    + "SUM(CASE WHEN " + PRED_A + " THEN 1 ELSE 0 END) a,"
                    + "SUM(CASE WHEN " + PRED_B + " THEN 1 ELSE 0 END) b,"
                    + "SUM(CASE WHEN " + PRED_C + " THEN 1 ELSE 0 END) c,"
                    + "SUM(CASE WHEN " + PRED_D + " THEN 1 ELSE 0 END) d,"
                    + "SUM(CASE WHEN " + PRED_ALL + " THEN 1 ELSE 0 END) t "
                    + "FROM " + FROM_FACTS + " WHERE " + DATE_PRED;
            namedJdbc.query(countsSql, base, rs -> {
                result.setGroupACount(rs.getInt("a"));
                result.setGroupBCount(rs.getInt("b"));
                result.setGroupCCount(rs.getInt("c"));
                result.setGroupDCount(rs.getInt("d"));
                result.setTotal(rs.getInt("t"));
            });

            // 2) Bộ lọc hiện tại: nhóm + tìm kiếm
            String where = " WHERE " + DATE_PRED + " AND " + groupPredicate(group);
            MapSqlParameterSource params = new MapSqlParameterSource().addValues(base.getValues());
            String term = (search == null) ? "" : search.trim();
            if (!term.isEmpty()) {
                where += " AND (name LIKE :like OR phone LIKE :like)";
                params.addValue("like", "%" + term + "%");
            }

            Integer matched = namedJdbc.queryForObject(
                    "SELECT COUNT(*) FROM " + FROM_FACTS + where, params, Integer.class);
            int total = matched != null ? matched : 0;
            int totalPages = Math.max(1, (int) Math.ceil((double) total / pageSize));
            if (page > totalPages) page = totalPages;
            int offset = (page - 1) * pageSize;

            // 3) Trang dữ liệu
            MapSqlParameterSource pageParams = new MapSqlParameterSource().addValues(params.getValues())
                    .addValue("limit", pageSize).addValue("offset", offset);
            final int fMinNotes = minNotes, fHours = hours, fMonths = months;
            final LocalDateTime fThreshold = threshold, fOldest = oldest, fStale = stale;
            List<ProblemCustomerRow> rows = namedJdbc.query(
                    "SELECT customer_id, name, phone, inserted_at, order_count, succeed_order_count, "
                    + "note_count, last_note_at, last_received_at, has_received, refused, "
                    + "COALESCE(rmA, 0) rmA, COALESCE(rmB, 0) rmB, COALESCE(rmC, 0) rmC "
                    + "FROM " + FROM_FACTS + where
                    + " ORDER BY " + sortClause(sort, dir) + " LIMIT :limit OFFSET :offset",
                    pageParams,
                    (rs, i) -> mapRow(rs, fMinNotes, fHours, fMonths, fThreshold, fOldest, fStale));

            result.setRows(rows);
            result.setMatched(total);
            result.setPage(page);
            result.setTotalPages(totalPages);
        } catch (Exception e) {
            log.error("Lỗi truy vấn trang khách hàng cảnh báo", e);
            result.setRows(List.of());
            result.setMatched(0);
            result.setPage(1);
            result.setTotalPages(1);
            result.setErrorMessage("Không truy vấn được dữ liệu: " + e.getMessage());
        }
        return result;
    }

    /** Điều kiện WHERE theo nhóm đang chọn (all|A|B|C|D). */
    private String groupPredicate(String group) {
        if (group == null) return PRED_ALL;
        switch (group) {
            case "A": return PRED_A;
            case "B": return PRED_B;
            case "C": return PRED_C;
            case "D": return PRED_D;
            default:  return PRED_ALL;
        }
    }

    /** ORDER BY an toàn (whitelist cột + hướng); rỗng đẩy cuối; tiebreak theo customer_id để phân trang ổn định. */
    private String sortClause(String sort, String dir) {
        String col = (sort == null) ? null : SORT_COLS.get(sort);
        String d = "asc".equalsIgnoreCase(dir) ? "ASC" : "DESC";
        if (col == null) {
            return "note_count DESC, inserted_at ASC, customer_id ASC"; // mặc định
        }
        // Cột ngày có thể NULL → đẩy xuống cuối
        boolean dateCol = col.equals("inserted_at") || col.equals("last_note_at") || col.equals("last_received_at");
        String nullsLast = dateCol ? "(" + col + " IS NULL) ASC, " : "";
        return nullsLast + col + " " + d + ", customer_id ASC";
    }

    /** Map 1 dòng facts → ProblemCustomerRow (tính cờ nhóm + lý do theo ngưỡng + now). */
    private ProblemCustomerRow mapRow(ResultSet rs, int minNotes, int hours, int months,
                                      LocalDateTime threshold, LocalDateTime oldest, LocalDateTime stale)
            throws SQLException {
        ProblemCustomerRow row = new ProblemCustomerRow();
        row.setCustomerId(rs.getString("customer_id"));
        row.setName(rs.getString("name"));
        row.setPhone(rs.getString("phone"));
        int noteCount = rs.getInt("note_count");
        LocalDateTime insertedAt = toLdt(rs.getTimestamp("inserted_at"));
        LocalDateTime lastNoteAt = toLdt(rs.getTimestamp("last_note_at"));
        LocalDateTime lastReceivedAt = toLdt(rs.getTimestamp("last_received_at"));
        boolean hasReceived = rs.getInt("has_received") > 0;
        boolean refused = rs.getInt("refused") > 0;

        row.setNoteCount(noteCount);
        row.setOrderCount(rs.getInt("order_count"));
        row.setSucceedOrderCount(rs.getInt("succeed_order_count"));
        row.setInsertedAt(insertedAt != null ? insertedAt.format(DATE_FMT) : "—");
        row.setLastNoteAt(lastNoteAt != null ? lastNoteAt.format(DATE_FMT) : "—");
        row.setLastReceivedAt(lastReceivedAt != null ? lastReceivedAt.format(DATE_FMT) : "—");

        if (refused) {
            row.setGroupD(true);
            row.setReason("Từ chối chăm");
        } else {
            // Cờ loại trừ (đã xóa khỏi nhóm) để badge/lý do khớp với lọc & số đếm ở SQL.
            boolean rmA = rs.getInt("rmA") > 0, rmB = rs.getInt("rmB") > 0, rmC = rs.getInt("rmC") > 0;
            applyGroupsABC(row, noteCount, insertedAt, hasReceived, lastReceivedAt,
                    minNotes, hours, months, threshold, oldest, stale, rmA, rmB, rmC);
        }
        return row;
    }

    /** Set cờ nhóm A/B/C + lý do lên row (nhóm D xử lý riêng); bỏ nhóm đã bị xóa (rmA/rmB/rmC). */
    private boolean applyGroupsABC(ProblemCustomerRow row, int noteCount, LocalDateTime insertedAt,
                                   boolean hasReceived, LocalDateTime lastReceivedAt,
                                   int minNotes, int hours, int months,
                                   LocalDateTime threshold, LocalDateTime oldest, LocalDateTime stale,
                                   boolean rmA, boolean rmB, boolean rmC) {
        boolean groupA = !rmA && !hasReceived && noteCount >= minNotes;
        boolean groupB = !rmB && !hasReceived && insertedAt != null
                && insertedAt.isBefore(threshold) && !insertedAt.isBefore(oldest) && noteCount >= 1;
        boolean groupC = !rmC && hasReceived && lastReceivedAt != null && lastReceivedAt.isBefore(stale);
        List<String> reasons = new ArrayList<>(2);
        if (groupA) reasons.add("Chăm " + noteCount + " ghi chú nhưng chưa có đơn xử lý / đã nhận");
        if (groupB) reasons.add("Quá " + hours + "h chưa có đơn xử lý / đã nhận");
        if (groupC) reasons.add("Đơn đã nhận gần nhất quá " + months + " tháng");
        row.setGroupA(groupA);
        row.setGroupB(groupB);
        row.setGroupC(groupC);
        row.setReason(String.join(" · ", reasons));
        return groupA || groupB || groupC;
    }

    private static LocalDateTime toLdt(Timestamp ts) { return ts != null ? ts.toLocalDateTime() : null; }

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
