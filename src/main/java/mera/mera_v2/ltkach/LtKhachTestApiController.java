package mera.mera_v2.ltkach;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.client.OrderApiClient;
import mera.mera_v2.pos.sync.dto.OrderApiDto;
import mera.mera_v2.pos.sync.dto.OrderListResponseDto;
import mera.mera_v2.ltkach.LtCalculationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/ltkhach-test")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LtKhachTestApiController {
  private static final Logger log = LoggerFactory.getLogger(LtKhachTestApiController.class);

    @PersistenceContext
    private EntityManager em;

    private final OrderApiClient orderApiClient;
    private final LtCalculationService ltCalculationService;
    private final LtKhachBackfillService ltKhachBackfillService;

    private static final DateTimeFormatter DT_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @GetMapping("/dashboard")
    public ResponseEntity<Map<String, Object>> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime toDate,
            @RequestParam(required = false, defaultValue = "all") String source) {

        long startMs = System.currentTimeMillis();

        if (fromDate == null || toDate == null) {
            LocalDate now = LocalDate.now();
            fromDate = now.withDayOfMonth(1).atStartOfDay();
            toDate = now.atTime(23, 59, 59);
        }

        long startTs = fromDate.toEpochSecond(ZoneOffset.of("+07:00"));
        long endTs = toDate.toEpochSecond(ZoneOffset.of("+07:00"));

        log.info("TEST API - fetching from {} to {} (ts: {} to {}), source={}",
                fromDate, toDate, startTs, endTs, source);

        // Calculate stats using optimized API calls
        DashboardResult result;
        if ("zalo".equals(source.toLowerCase())) {
            result = calculateZaloDashboardFromApi(startTs, endTs);
        } else {
            result = calculateAllFacebookDashboardFromApi(startTs, endTs, source);
        }

        long elapsed = System.currentTimeMillis() - startMs;

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("totalOrders", result.totalOrders);
        response.put("completedOrders", result.completedOrders);
        response.put("totalRevenue", result.revenue);
        response.put("conversionRate", result.conversionRate);
        response.put("previousTotalOrders", result.prevTotalOrders);
        response.put("previousCompletedOrders", result.prevCompletedOrders);
        response.put("previousRevenue", result.prevRevenue);
        response.put("apiCallTimeMs", elapsed);
        response.put("source", source);
        response.put("fromDate", fromDate.format(DT_FORMATTER));
        response.put("toDate", toDate.format(DT_FORMATTER));

        return ResponseEntity.ok(response);
    }

    /**
     * Dashboard for All/Facebook - optimized with parallel API calls.
     * Uses pagination with status filter for efficiency.
     */
    private DashboardResult calculateAllFacebookDashboardFromApi(long startTs, long endTs, String source) {
        DashboardResult result = new DashboardResult();
        long totalOrders = 0;
        long completedOrders = 0;
        double revenue = 0;
        int maxPages = 50; // Limit pages for performance

        // Fetch all orders (no status filter) to get totalOrders
        int page = 1;
        int pageSize = 200;
        String sourceFilter = source.toLowerCase();

        while (page <= maxPages) {
            OrderListResponseDto resp = orderApiClient.fetchOrdersPage(
                    startTs, endTs, page, pageSize, "inserted_at", null
            );

            if (resp.getData() == null || resp.getData().isEmpty()) {
                break;
            }

            for (OrderApiDto o : resp.getData()) {
                // Filter by source if not "all"
                if (!"all".equals(sourceFilter)) {
                    String sourcesName = o.getOrderSourcesName();
                    if (sourcesName == null || !sourcesName.toLowerCase().contains(sourceFilter)) {
                        continue;
                    }
                }

                totalOrders++;

                Integer status = o.getStatus();
                if (status != null && status == 3) {
                    completedOrders++;
                    Double cod = o.getCod();
                    Double prepaid = o.getPrepaid();
                    revenue += (cod != null ? cod : 0) + (prepaid != null ? prepaid : 0);
                }
            }

            log.info("TEST API - All/Facebook page {}, total so far: {}, completed: {}",
                    page, totalOrders, completedOrders);

            if (resp.getData().size() < pageSize) {
                break;
            }
            page++;

            if (resp.getTotalPages() != null && page > resp.getTotalPages()) {
                break;
            }
        }

        result.totalOrders = totalOrders;
        result.completedOrders = completedOrders;
        result.revenue = BigDecimal.valueOf(revenue);
        result.conversionRate = totalOrders > 0
                ? BigDecimal.valueOf(completedOrders * 100.0 / totalOrders).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return result;
    }

    /**
     * Dashboard for Zalo - needs statusHistory to determine confirmed date.
     */
    private DashboardResult calculateZaloDashboardFromApi(long startTs, long endTs) {
        DashboardResult result = new DashboardResult();
        Map<String, OrderApiDto> confirmedOrders = new LinkedHashMap<>();
        Set<String> completedOrderIds = new HashSet<>();
        int maxPages = 50;

        // Fetch orders and check statusHistory for Zalo
        int page = 1;
        int pageSize = 200;

        while (page <= maxPages) {
            OrderListResponseDto resp = orderApiClient.fetchOrdersPage(
                    startTs, endTs, page, pageSize, "inserted_at", null
            );

            if (resp.getData() == null || resp.getData().isEmpty()) {
                break;
            }

            for (OrderApiDto o : resp.getData()) {
                // Check if Zalo order
                String sourcesName = o.getOrderSourcesName();
                if (sourcesName == null || !sourcesName.toLowerCase().contains("zalo")) {
                    continue;
                }

                List<mera.mera_v2.pos.sync.dto.StatusHistoryDto> history = o.getStatusHistory();
                boolean confirmedInRange = false;

                if (history != null && !history.isEmpty()) {
                    for (mera.mera_v2.pos.sync.dto.StatusHistoryDto h : history) {
                        if (h.getNewStatus() != null && h.getNewStatus() == 1) {
                            String confirmedAt = h.getUpdatedAt();
                            if (confirmedAt != null && isInRange(confirmedAt, startTs, endTs)) {
                                confirmedInRange = true;
                                confirmedOrders.put(o.getId(), o);
                                break;
                            }
                        }
                    }
                }

                // Check if completed
                Integer status = o.getStatus();
                if (status != null && status == 3) {
                    completedOrderIds.add(o.getId());
                }
            }

            log.info("TEST API - Zalo page {}, confirmed so far: {}, completed: {}",
                    page, confirmedOrders.size(), completedOrderIds.size());

            if (resp.getData().size() < pageSize) {
                break;
            }
            page++;

            if (resp.getTotalPages() != null && page > resp.getTotalPages()) {
                break;
            }
        }

        // Calculate revenue from completed confirmed orders
        long completedOrders = 0;
        double revenue = 0;

        for (OrderApiDto o : confirmedOrders.values()) {
            if (completedOrderIds.contains(o.getId())) {
                completedOrders++;
                Double cod = o.getCod();
                Double prepaid = o.getPrepaid();
                revenue += (cod != null ? cod : 0) + (prepaid != null ? prepaid : 0);
            }
        }

        result.totalOrders = confirmedOrders.size();
        result.completedOrders = completedOrders;
        result.revenue = BigDecimal.valueOf(revenue);
        result.conversionRate = result.totalOrders > 0
                ? BigDecimal.valueOf(completedOrders * 100.0 / result.totalOrders).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;

        return result;
    }

    private boolean isInRange(String dateTimeStr, long startTs, long endTs) {
        try {
            // Try multiple date formats that Pancake API might return
            String[] formats = {
                    "yyyy-MM-dd HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss",
                    "yyyy-MM-dd'T'HH:mm:ss.SSS",
                    "yyyy-MM-dd'T'HH:mm:ssZ"
            };

            for (String fmt : formats) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern(fmt);
                    LocalDateTime dt = LocalDateTime.parse(dateTimeStr, formatter);
                    long ts = dt.toEpochSecond(ZoneOffset.of("+07:00"));
                    return ts >= startTs && ts < endTs;
                } catch (Exception ignored) {}
            }

            // Try parsing as Instant
            try {
                Instant instant = Instant.parse(dateTimeStr);
                long ts = instant.getEpochSecond();
                return ts >= startTs && ts < endTs;
            } catch (Exception ignored) {}

            // Try epoch milliseconds
            try {
                long ms = Long.parseLong(dateTimeStr);
                long ts = ms / 1000;
                return ts >= startTs && ts < endTs;
            } catch (Exception ignored) {}

        } catch (Exception e) {
            log.warn("Failed to parse date string: {}", dateTimeStr, e);
        }
        return false;
    }

    private static class DashboardResult {
        long totalOrders;
        long completedOrders;
        BigDecimal revenue;
        BigDecimal conversionRate;
        long prevTotalOrders;
        long prevCompletedOrders;
        BigDecimal prevRevenue;
    }

    @GetMapping("/db-schema")
    public ResponseEntity<Map<String, Object>> getDbSchema(@RequestParam(required = false) String table) {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            if (table != null && !table.isBlank()) {
                List<Object[]> cols = em.createNativeQuery("DESCRIBE " + table).getResultList();
                result.put("table", table);
                result.put("columns", cols.stream().map(row -> {
                    Map<String, Object> col = new LinkedHashMap<>();
                    col.put("field", row[0]);
                    col.put("type", row[1]);
                    col.put("null", row[2]);
                    col.put("key", row[3]);
                    col.put("default", row[4]);
                    col.put("extra", row[5]);
                    return col;
                }).toList());
            } else {
                List<Object[]> tables = em.createNativeQuery("SHOW TABLES").getResultList();
                result.put("tables", tables.stream().map(row -> row[0]).toList());
            }
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    // ============ LT Recalculate APIs ============

    /**
     * Recalculate LT cho tất cả orders.
     * Gọi khi cần migrate data hoặc fix sai lt_type.
     * Log tiến trình: đã xử lý X / Y orders.
     */
    @PostMapping("/lt/recalculate-all")
    public ResponseEntity<Map<String, Object>> recalculateAll() {
        log.info("=== BẮT ĐẦU RECALCULATE LT CHO TẤT CẢ ORDERS ===");

        try {
            // 1. Đếm tổng orders
            Long totalOrders = ((Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM orders WHERE lt_type IS NULL OR lt_type = -1"
            ).getSingleResult()).longValue();

            log.info("Tổng orders cần recalculate: {}", totalOrders);

            // 2. Lấy tất cả order IDs
            List<Object[]> orderRows = em.createNativeQuery(
                "SELECT id, customer_id, status FROM orders WHERE lt_type IS NULL OR lt_type = -1"
            ).getResultList();

            int processed = 0;
            int success = 0;
            int ltTrueCount = 0;
            int ltFalseCount = 0;

            // Load combo & substitution 1 lần (cache)
            Map<String, Set<String>> comboGroups = ltCalculationService.loadComboGroups();
            Map<String, Set<String>> substitutionGroups = ltCalculationService.loadSubstitutionGroups();

            for (Object[] row : orderRows) {
                Long orderId = ((Number) row[0]).longValue();
                processed++;

                try {
                    // Lấy items
                    List<Object[]> items = em.createNativeQuery(
                        "SELECT oi.product_id, oi.variation_id, oi.quantity FROM order_items oi WHERE oi.order_id = :orderId"
                    ).setParameter("orderId", orderId).getResultList();

                    Map<String, Integer> orderProductMap = new HashMap<>();
                    for (Object[] item : items) {
                        String key = ltCalculationService.buildKey((String) item[0], (String) item[1]);
                        if (key != null) {
                            int qty = item[2] != null ? ((Number) item[2]).intValue() : 1;
                            orderProductMap.merge(key, qty, Integer::sum);
                        }
                    }

                    // Check combo
                    boolean isFullCombo = ltCalculationService.checkIsFullCombo(orderProductMap, comboGroups, substitutionGroups);
                    String customerId = (String) row[1];
                    Integer status = ((Number) row[2]).intValue();

                    // Update lt_type
                    em.createNativeQuery(
                        "UPDATE orders SET lt_type = :ltType WHERE id = :orderId"
                    ).setParameter("ltType", isFullCombo ? 1 : 0)
                     .setParameter("orderId", orderId).executeUpdate();

                    // Tính lt_count_snapshot: đếm số đơn combo thành công của khách trước thời điểm đơn này
                    Object insertedAtObj = null;
                    try {
                        Object[] orderInfo = (Object[]) em.createNativeQuery(
                            "SELECT customer_id, inserted_at FROM orders WHERE id = :orderId"
                        ).setParameter("orderId", orderId).getSingleResult();
                        String custId = (String) orderInfo[0];
                        insertedAtObj = orderInfo[1];

                        if (custId != null && insertedAtObj != null) {
                            Integer snapshot = ((Number) em.createNativeQuery(
                                "SELECT COUNT(*) FROM orders " +
                                "WHERE customer_id = :customerId " +
                                "AND status IN (3, 16) " +
                                "AND lt_type = 1 " +
                                "AND inserted_at < :insertedAt"
                            ).setParameter("customerId", custId)
                             .setParameter("insertedAt", insertedAtObj)
                             .getSingleResult()).intValue();

                            em.createNativeQuery(
                                "UPDATE orders SET lt_count_snapshot = :snapshot WHERE id = :orderId"
                            ).setParameter("snapshot", snapshot)
                             .setParameter("orderId", orderId).executeUpdate();
                        }
                    } catch (Exception ex) {
                        log.warn("  Order {}: không thể tính snapshot: {}", orderId, ex.getMessage());
                    }

                    // Nếu lt_type = TRUE và status = 3 → +1 lt_count
                    if (isFullCombo && status == 3 && customerId != null) {
                        em.createNativeQuery(
                            "UPDATE customers SET lt_count = lt_count + 1 WHERE id = :customerId"
                        ).setParameter("customerId", customerId).executeUpdate();
                        log.info("  Order {}: lt_type=TRUE, status=3 → +1 lt_count (customer={})", orderId, customerId);
                    }

                    if (isFullCombo) {
                        ltTrueCount++;
                    } else {
                        ltFalseCount++;
                    }
                    success++;

                    // Log tiến trình mỗi 100 orders
                    if (processed % 100 == 0) {
                        log.info("Tiến trình: {}/{} orders (lt_true={}, lt_false={})",
                            processed, totalOrders, ltTrueCount, ltFalseCount);
                    }

                } catch (Exception e) {
                    log.error("Lỗi xử lý order {}: {}", orderId, e.getMessage());
                }
            }

            log.info("=== HOÀN THÀNH RECALCULATE ===");
            log.info("Tổng: {}, Thành công: {}, lt_type=TRUE: {}, lt_type=FALSE: {}",
                processed, success, ltTrueCount, ltFalseCount);

            return ResponseEntity.ok(Map.of(
                "total", processed,
                "success", success,
                "ltTrue", ltTrueCount,
                "ltFalse", ltFalseCount,
                "message", "Recalculate hoàn tất!"
            ));

        } catch (Exception e) {
            log.error("Lỗi recalculate: {}", e.getMessage(), e);
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/lt/recalculate-order/{orderId}")
    public ResponseEntity<Map<String, Object>> recalculateOrderLt(@PathVariable Long orderId) {
        try {
            LtCalculationService.LtResult result = ltCalculationService.calculateForOrder(orderId, true);
            return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "ltType", result.ltType(),
                "ltCount", result.ltCount(),
                "changed", result.changed()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/lt/recalculate-customer/{customerId}")
    public ResponseEntity<Map<String, Object>> recalculateCustomerLt(@PathVariable String customerId) {
        try {
            int ltCount = ltCalculationService.recalculateForCustomer(customerId);
            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "ltCount", ltCount
            ));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Backfill lt_count_snapshot cho tất cả đơn cũ chưa có giá trị này.
     * Mỗi đơn được gán giá trị customers.lt_count tại thời điểm đơn được tạo
     * bằng cách: đếm số đơn combo thành công (lt_type=1, status=3) CỦA KHÁCH ĐÓ
     * có inserted_at TRƯỚC hoặc BẰNG inserted_at của đơn này.
     *
     * Cách tính:
     * - Với mỗi đơn, đếm số đơn combo thành công của cùng khách
     *   có inserted_at <= đơn này
     * - Đó chính là lt_count_snapshot của đơn đó
     */
    @PostMapping("/lt/backfill-snapshot")
    public ResponseEntity<Map<String, Object>> backfillSnapshot() {
        log.info("=== BẮT ĐẦU BACKFILL lt_count_snapshot ===");
        Map<String, Object> result = ltKhachBackfillService.backfillSnapshot();
        return ResponseEntity.ok(result);
    }

    /**
     * Tạo index để tối ưu query LT performance.
     */
    @PostMapping("/db/check-lt-data")
    public ResponseEntity<Map<String, Object>> checkLtData() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Đếm theo lt_type
            List<Object[]> byLtType = em.createNativeQuery("""
                SELECT lt_type, COUNT(*) as cnt, COUNT(DISTINCT customer_id) as cust_cnt
                FROM orders
                WHERE status = 3
                  AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%'
                GROUP BY lt_type
                """).getResultList();

            List<Map<String, Object>> ltTypeStats = new ArrayList<>();
            for (Object[] row : byLtType) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("lt_type", row[0]);
                m.put("cnt", row[1]);
                m.put("cust_cnt", row[2]);
                ltTypeStats.add(m);
            }
            result.put("byLtType", ltTypeStats);

            // Sample 5 đơn zalo status=3
            List<Object[]> samples = em.createNativeQuery("""
                SELECT id, customer_id, lt_type, lt_count_snapshot, status, order_sources_name, creator_id
                FROM orders
                WHERE status = 3
                  AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%'
                LIMIT 5
                """).getResultList();

            List<Map<String, Object>> sampleList = new ArrayList<>();
            for (Object[] row : samples) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", row[0]);
                m.put("customer_id", row[1]);
                m.put("lt_type", row[2]);
                m.put("lt_count_snapshot", row[3]);
                m.put("status", row[4]);
                m.put("order_sources_name", row[5]);
                m.put("creator_id", row[6]);
                sampleList.add(m);
            }
            result.put("samples", sampleList);

            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/db/test-lt-query")
    public ResponseEntity<Map<String, Object>> testLtQuery() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Test query y hệt code nhưng hardcode creator_id
            String creatorId = "ae055044-293d-45a7-b428-7a51b305c586";
            LocalDateTime start = LocalDateTime.of(2026, 5, 31, 17, 0);
            LocalDateTime end = LocalDateTime.of(2026, 6, 30, 17, 0);

            // Y hệt query trong code
            String sql = """
                SELECT
                    o.creator_id,
                    COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND h.new_status = 1 THEN o.customer_id END) as lt_le_customers,
                    COUNT(DISTINCT CASE WHEN o.lt_type = 1 AND o.lt_count_snapshot = 0 AND h.new_status = 1 THEN o.customer_id END) as lt1_customers,
                    COUNT(DISTINCT CASE WHEN o.lt_type = 1 AND o.lt_count_snapshot = 1 AND h.new_status = 1 THEN o.customer_id END) as lt2_customers,
                    COUNT(DISTINCT CASE WHEN o.lt_type = 1 AND o.lt_count_snapshot = 2 AND h.new_status = 1 THEN o.customer_id END) as lt3_customers,
                    COUNT(DISTINCT CASE WHEN o.lt_type = 1 AND o.lt_count_snapshot >= 3 AND h.new_status = 1 THEN o.customer_id END) as lt4_customers
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1
                AND h.updated_at >= :start
                AND h.updated_at < :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                AND o.creator_id = :creatorId
                GROUP BY o.creator_id
                """;

            Query q = em.createNativeQuery(sql);
            q.setParameter("start", start);
            q.setParameter("end", end);
            q.setParameter("creatorId", creatorId);
            List<Object[]> rows = q.getResultList();

            List<Map<String, Object>> list = new ArrayList<>();
            for (Object[] row : rows) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("creator_id", row[0]);
                m.put("lt_le_customers", row[1]);
                m.put("lt1_customers", row[2]);
                m.put("lt2_customers", row[3]);
                m.put("lt3_customers", row[4]);
                m.put("lt4_customers", row[5]);
                list.add(m);
            }
            result.put("exactQueryResult", list);

            // Đếm thử không filter status để so sánh
            String sql2 = """
                SELECT COUNT(DISTINCT o.customer_id)
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1
                AND h.updated_at >= :start
                AND h.updated_at < :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                AND o.creator_id = :creatorId
                AND o.lt_type = 0
                """;
            Query q2 = em.createNativeQuery(sql2);
            q2.setParameter("start", start);
            q2.setParameter("end", end);
            q2.setParameter("creatorId", creatorId);
            result.put("lt_le_no_status_filter", q2.getSingleResult());

            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/db/lt-sum-check")
    public ResponseEntity<Map<String, Object>> ltSumCheck() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String creatorId = "ae055044-293d-45a7-b428-7a51b305c586";
            LocalDateTime start = LocalDateTime.of(2026, 5, 31, 17, 0);
            LocalDateTime end = LocalDateTime.of(2026, 6, 30, 17, 0);

            // 1) Tổng số đơn (theo completedOrders = 144)
            String q1 = "SELECT COUNT(DISTINCT o.id) FROM orders o INNER JOIN order_status_histories h ON o.id = h.order_id WHERE h.new_status = 1 AND h.updated_at >= :start AND h.updated_at < :end AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%' AND o.creator_id = :cid AND o.status = 3";
            Query qq1 = em.createNativeQuery(q1);
            qq1.setParameter("start", start); qq1.setParameter("end", end); qq1.setParameter("cid", creatorId);
            result.put("total_completed", qq1.getSingleResult());

            // 2) Phân bố lt_type
            String q2 = "SELECT lt_type, COUNT(DISTINCT o.id) FROM orders o INNER JOIN order_status_histories h ON o.id = h.order_id WHERE h.new_status = 1 AND h.updated_at >= :start AND h.updated_at < :end AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%' AND o.creator_id = :cid AND o.status = 3 GROUP BY lt_type";
            Query qq2 = em.createNativeQuery(q2);
            qq2.setParameter("start", start); qq2.setParameter("end", end); qq2.setParameter("cid", creatorId);
            List<Object[]> typeRows = qq2.getResultList();
            List<Map<String,Object>> typeStats = new ArrayList<>();
            for (Object[] r : typeRows) { Map<String,Object> m = new LinkedHashMap<>(); m.put("lt_type", r[0]); m.put("cnt", r[1]); typeStats.add(m); }
            result.put("by_lt_type", typeStats);

            // 3) Phân bố lt_count_snapshot cho lt_type=1
            String q3 = "SELECT COALESCE(lt_count_snapshot, -1) as snap, COUNT(DISTINCT o.id) as cnt FROM orders o INNER JOIN order_status_histories h ON o.id = h.order_id WHERE h.new_status = 1 AND h.updated_at >= :start AND h.updated_at < :end AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%' AND o.creator_id = :cid AND o.status = 3 AND o.lt_type = 1 GROUP BY lt_count_snapshot ORDER BY snap";
            Query qq3 = em.createNativeQuery(q3);
            qq3.setParameter("start", start); qq3.setParameter("end", end); qq3.setParameter("cid", creatorId);
            List<Object[]> snapRows = qq3.getResultList();
            List<Map<String,Object>> snapStats = new ArrayList<>();
            for (Object[] r : snapRows) { Map<String,Object> m = new LinkedHashMap<>(); m.put("snapshot", r[0]); m.put("cnt", r[1]); snapStats.add(m); }
            result.put("by_snapshot", snapStats);

            // 4) Đếm số row history có new_status=1 cho mỗi order (xem có đơn bị double count không)
            String q4 = "SELECT cnt_history, COUNT(*) as num_orders FROM (SELECT o.id, COUNT(*) as cnt_history FROM orders o INNER JOIN order_status_histories h ON o.id = h.order_id WHERE h.new_status = 1 AND h.updated_at >= :start AND h.updated_at < :end AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%' AND o.creator_id = :cid AND o.status = 3 GROUP BY o.id) as sub GROUP BY cnt_history ORDER BY cnt_history";
            Query qq4 = em.createNativeQuery(q4);
            qq4.setParameter("start", start); qq4.setParameter("end", end); qq4.setParameter("cid", creatorId);
            List<Object[]> histRows = qq4.getResultList();
            List<Map<String,Object>> histStats = new ArrayList<>();
            for (Object[] r : histRows) { Map<String,Object> m = new LinkedHashMap<>(); m.put("history_rows", r[0]); m.put("num_orders", r[1]); histStats.add(m); }
            result.put("history_distribution", histStats);

            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    @PostMapping("/db/lt-sum-by-snapshot")
    public ResponseEntity<Map<String, Object>> ltSumBySnapshot() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String creatorId = "ae055044-293d-45a7-b428-7a51b305c586";
            // 1) Phân bố snapshot đầy đủ
            String q1 = "SELECT COALESCE(lt_count_snapshot, -1) as snap, COUNT(*) as cnt FROM orders WHERE creator_id = :cid AND status = 3 AND lt_count_snapshot IS NOT NULL AND inserted_at >= '2026-05-31 17:00:00' AND inserted_at <= '2026-06-30 17:00:00' AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%' GROUP BY lt_count_snapshot ORDER BY snap";
            Query qq1 = em.createNativeQuery(q1);
            qq1.setParameter("cid", creatorId);
            List<Object[]> snapRows = qq1.getResultList();
            List<Map<String,Object>> snapStats = new ArrayList<>();
            for (Object[] r : snapRows) { Map<String,Object> m = new LinkedHashMap<>(); m.put("snapshot", r[0]); m.put("cnt", r[1]); snapStats.add(m); }
            result.put("by_snapshot", snapStats);

            // 2) Phân bố lt_type
            String q2 = "SELECT COALESCE(lt_type, -1) as lt_type, COUNT(*) as cnt FROM orders WHERE creator_id = :cid AND status = 3 AND lt_count_snapshot IS NOT NULL AND inserted_at >= '2026-05-31 17:00:00' AND inserted_at <= '2026-06-30 17:00:00' AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%' GROUP BY lt_type";
            Query qq2 = em.createNativeQuery(q2);
            qq2.setParameter("cid", creatorId);
            List<Object[]> typeRows = qq2.getResultList();
            List<Map<String,Object>> typeStats = new ArrayList<>();
            for (Object[] r : typeRows) { Map<String,Object> m = new LinkedHashMap<>(); m.put("lt_type", r[0]); m.put("cnt", r[1]); typeStats.add(m); }
            result.put("by_lt_type", typeStats);

            // 3) Sample 10 dòng để xem dữ liệu thực
            String q3 = "SELECT id, lt_type, lt_count_snapshot, status, inserted_at, cod, prepaid FROM orders WHERE creator_id = :cid AND status = 3 AND lt_count_snapshot IS NOT NULL AND inserted_at >= '2026-05-31 17:00:00' AND inserted_at <= '2026-06-30 17:00:00' AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%' ORDER BY inserted_at DESC LIMIT 15";
            Query qq3 = em.createNativeQuery(q3);
            qq3.setParameter("cid", creatorId);
            List<Object[]> sample = qq3.getResultList();
            List<Map<String,Object>> samples = new ArrayList<>();
            for (Object[] r : sample) { Map<String,Object> m = new LinkedHashMap<>(); m.put("id", r[0]); m.put("lt_type", r[1]); m.put("snapshot", r[2]); m.put("status", r[3]); m.put("inserted_at", String.valueOf(r[4])); m.put("cod", r[5]); m.put("prepaid", r[6]); samples.add(m); }
            result.put("samples", samples);

            // 4) Tổng snapshot IS NULL (đơn không phải combo)
            String q4 = "SELECT COUNT(*) FROM orders WHERE creator_id = :cid AND status = 3 AND lt_count_snapshot IS NULL AND inserted_at >= '2026-05-31 17:00:00' AND inserted_at <= '2026-06-30 17:00:00' AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%'";
            Query qq4 = em.createNativeQuery(q4);
            qq4.setParameter("cid", creatorId);
            result.put("null_snapshot", qq4.getSingleResult());

            // 5) Tổng tất cả
            String q5 = "SELECT COUNT(*) FROM orders WHERE creator_id = :cid AND status = 3 AND inserted_at >= '2026-05-31 17:00:00' AND inserted_at <= '2026-06-30 17:00:00' AND LOWER(IFNULL(order_sources_name, '')) LIKE '%zalo%'";
            Query qq5 = em.createNativeQuery(q5);
            qq5.setParameter("cid", creatorId);
            result.put("total_with_status3", qq5.getSingleResult());

            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }

    public ResponseEntity<Map<String, Object>> createLtIndexes() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            // Thử tạo index, nếu đã tồn tại thì bỏ qua
            try {
                em.createNativeQuery("CREATE INDEX idx_osh_order_status_date ON order_status_histories(order_id, new_status, updated_at)").executeUpdate();
                result.put("idx_osh_order_status_date", "CREATED");
            } catch (Exception e) {
                result.put("idx_osh_order_status_date", "ALREADY EXISTS or error: " + e.getMessage());
            }

            try {
                em.createNativeQuery("CREATE INDEX idx_orders_creator_status_lt ON orders(creator_id, status, lt_type, lt_count_snapshot)").executeUpdate();
                result.put("idx_orders_creator_status_lt", "CREATED");
            } catch (Exception e) {
                result.put("idx_orders_creator_status_lt", "ALREADY EXISTS or error: " + e.getMessage());
            }

            result.put("success", true);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            result.put("success", false);
            result.put("error", e.getMessage());
            return ResponseEntity.status(500).body(result);
        }
    }
}
