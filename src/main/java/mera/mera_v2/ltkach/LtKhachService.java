package mera.mera_v2.ltkach;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import mera.mera_v2.ltkach.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class LtKhachService {

    private static final Logger log = LoggerFactory.getLogger(LtKhachService.class);

    @PersistenceContext
    private EntityManager em;

    private static final BigDecimal MIN_LT_AMOUNT = new BigDecimal("1500000");
    private static final int MIN_LT_PRODUCTS = 3;
    private static final String SHIPPING_FEE_PRODUCT = "Cước vận chuyển";

    /**
     * Dashboard theo nguồn (Zalo/Facebook/All).
     * - Facebook: đơn tạo mới (status=0), base date = inserted_at
     * - Zalo: đơn xác nhận (status=1 trong order_status_histories), base date = ngày xác nhận
     * - All: tất cả đơn tạo mới (status=0)
     * 
     * @param fromDate LocalDateTime - đã chuyển UTC+7 → UTC+0 (VD: 2026-06-01 00:00 VN = 2026-05-31 17:00)
     * @param toDate LocalDateTime - đã chuyển UTC+7 → UTC+0 (VD: 2026-06-30 17:00 VN = 2026-06-30 10:00)
     */
    @Transactional(readOnly = true)
    public DashboardDto getDashboard(LocalDateTime fromDate, LocalDateTime toDate, String source) {
        DashboardDto dto = new DashboardDto();
        
        try {
            // Frontend đã chuyển đổi UTC, dùng trực tiếp
            LocalDateTime startDate = fromDate;
            LocalDateTime endDate = toDate;
            
            // Tính kỳ trước: cùng độ dài, lùi lại 1 tháng
            long daysInRange = java.time.temporal.ChronoUnit.DAYS.between(fromDate.toLocalDate(), toDate.toLocalDate());
            LocalDateTime prevStartDate = fromDate.minusMonths(1);
            LocalDateTime prevEndDate = prevStartDate.plusDays(daysInRange);

            long totalOrders;
            long completedOrders;
            BigDecimal revenue;

            long prevTotalOrders;
            long prevCompletedOrders;
            BigDecimal prevRevenue;

            // Xác định filter nguồn
            String sourceFilter = getSourceFilter(source);

            // Tính cho kỳ hiện tại
            DashboardStats current = calculateDashboardStats(startDate, endDate, source, sourceFilter);
            totalOrders = current.totalOrders;
            completedOrders = current.completedOrders;
            revenue = current.revenue;

            // Tính cho kỳ trước
            DashboardStats previous = calculateDashboardStats(prevStartDate, prevEndDate, source, sourceFilter);
            prevTotalOrders = previous.totalOrders;
            prevCompletedOrders = previous.completedOrders;
            prevRevenue = previous.revenue;

            dto.setTotalOrders(totalOrders);
            dto.setCompletedOrders(completedOrders);
            dto.setTotalRevenue(revenue);
            dto.setConversionRate(totalOrders > 0 
                ? BigDecimal.valueOf(completedOrders * 100.0 / totalOrders).setScale(1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO);
            dto.setPreviousTotalOrders(prevTotalOrders);
            dto.setPreviousCompletedOrders(prevCompletedOrders);
            dto.setPreviousRevenue(prevRevenue);

        } catch (Exception e) {
            log.error("Error getting dashboard", e);
        }
        
        return dto;
    }

    private String getSourceFilter(String source) {
        if ("zalo".equalsIgnoreCase(source)) {
            return "zalo";
        } else if ("facebook".equalsIgnoreCase(source)) {
            return "facebook";
        }
        return "all";
    }

    private DashboardStats calculateDashboardStats(LocalDateTime startDate, LocalDateTime endDate, String source, String sourceFilter) {
        DashboardStats stats = new DashboardStats();
        
        if ("zalo".equals(sourceFilter)) {
            // Zalo: đơn đã xác nhận (status=1), base = ngày xác nhận trong order_status_histories
            stats.totalOrders = countConfirmedOrdersInRange(startDate, endDate, sourceFilter);
            stats.completedOrders = countCompletedOrdersInRange(startDate, endDate, sourceFilter);
            stats.revenue = calculateRevenueInRange(startDate, endDate, sourceFilter);
        } else {
            // Facebook hoặc All: đơn tạo mới (status=0), base = inserted_at
            stats.totalOrders = countCreatedOrdersInRange(startDate, endDate, sourceFilter);
            stats.completedOrders = countCompletedOrdersFromCreatedInRange(startDate, endDate, sourceFilter);
            stats.revenue = calculateRevenueFromCreatedInRange(startDate, endDate, sourceFilter);
        }
        
        return stats;
    }

    private static class DashboardStats {
        long totalOrders;
        long completedOrders;
        BigDecimal revenue;
    }

    /**
     * Đếm đơn tạo mới (status=0) - dùng cho Facebook.
     * "All": đếm tất cả đơn (không filter status), match với SELECT COUNT(*) FROM orders.
     */
    private long countCreatedOrdersInRange(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("facebook".equals(source)) {
            sql = """
                SELECT COUNT(DISTINCT o.id)
                FROM orders o
                WHERE o.status = 0
                AND o.inserted_at >= :start
                AND o.inserted_at < :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                """;
        } else {
            // "all": đếm tất cả đơn, không filter status (giống SELECT COUNT(*) FROM orders)
            sql = """
                SELECT COUNT(*) FROM orders o
                WHERE o.inserted_at >= :start
                AND o.inserted_at < :end
                """;
        }
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Đếm đơn hoàn thành (status=3) - dùng cho Facebook và All.
     */
    private long countCompletedOrdersFromCreatedInRange(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("facebook".equals(source)) {
            sql = """
                SELECT COUNT(DISTINCT o.id)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start
                AND o.inserted_at < :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                """;
        } else {
            sql = """
                SELECT COUNT(DISTINCT o.id)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start
                AND o.inserted_at < :end
                """;
        }
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Tính doanh thu từ đơn tạo mới - dùng cho Facebook và All.
     * Doanh thu = cod + prepaid của đơn status=3
     */
    private BigDecimal calculateRevenueFromCreatedInRange(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("facebook".equals(source)) {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start 
                AND o.inserted_at < :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                """;
        } else {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start 
                AND o.inserted_at < :end
                """;
        }
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        Object result = query.getSingleResult();
        return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    }

    /**
     * Đếm đơn xác nhận (status=1) cho Zalo - dùng cho base date = ngày xác nhận.
     */
    private long countConfirmedOrdersInRange(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("zalo".equals(source)) {
            sql = """
                SELECT COUNT(DISTINCT o.id) 
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1 
                AND h.updated_at >= :start 
                AND h.updated_at < :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                """;
        } else {
            sql = """
                SELECT COUNT(DISTINCT o.id) 
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1 
                AND h.updated_at >= :start 
                AND h.updated_at < :end
                """;
        }
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Đếm đơn hoàn thành (status=3) cho Zalo - dùng cho base date = ngày xác nhận.
     */
    private long countCompletedOrdersInRange(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("zalo".equals(source)) {
            sql = """
                SELECT COUNT(DISTINCT o.id) 
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1 
                AND h.updated_at >= :start 
                AND h.updated_at < :end
                AND o.status = 3
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                """;
        } else {
            sql = """
                SELECT COUNT(DISTINCT o.id) 
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1 
                AND h.updated_at >= :start 
                AND h.updated_at < :end
                AND o.status = 3
                """;
        }
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        return ((Number) query.getSingleResult()).longValue();
    }

    /**
     * Tính doanh thu cho Zalo - dùng cho base date = ngày xác nhận.
     * Doanh thu = cod + prepaid của đơn status=3
     */
    private BigDecimal calculateRevenueInRange(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("zalo".equals(source)) {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1 
                AND h.updated_at >= :start 
                AND h.updated_at < :end
                AND o.status = 3
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                """;
        } else {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start 
                AND o.inserted_at < :end
                """;
        }
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        Object result = query.getSingleResult();
        return result != null ? new BigDecimal(result.toString()) : BigDecimal.ZERO;
    }

    @Transactional(readOnly = true)
    public List<EmployeePerformanceDto> getTopEmployees(LocalDateTime fromDate, LocalDateTime toDate, int limit, String source) {
        List<EmployeePerformanceDto> result = new ArrayList<>();

        try {
            // Chuyển UTC+7 (frontend) sang UTC+0 (DB): trừ 7 tiếng
            LocalDateTime startDateUtc = fromDate.minusHours(7);
            LocalDateTime endDateUtc = toDate.minusHours(7);
            String sourceFilter = getSourceFilter(source);

            // Query báo cáo chính (không bao gồm LT metrics)
            String sql;
            if ("zalo".equals(sourceFilter)) {
                // Zalo: theo ngày xác nhận (status=1 trong history)
                sql = """
                    SELECT
                        o.creator_id,
                        COALESCE(NULLIF(TRIM(pu.name), ''),
                                 NULLIF(TRIM(o.last_editor_name), ''),
                                 NULLIF(TRIM(o.assigning_care_id), ''),
                                 o.creator_id) as name,
                        COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status = 3 THEN o.id END) as data_received,
                        COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status = 3 THEN o.id END) as orders_received,
                        COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status = 3 THEN o.id END) as completed,
                        COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status IN (5, 6, 7) THEN o.id END) as cancelled,
                        (SELECT rev FROM (
                            SELECT creator_id, SUM(tong_tien) as rev
                            FROM (
                                SELECT o2.creator_id, o2.id, MAX(COALESCE(o2.cod, 0) + COALESCE(o2.prepaid, 0)) as tong_tien
                                FROM orders o2
                                INNER JOIN order_status_histories h2 ON o2.id = h2.order_id
                                WHERE h2.new_status = 1
                                  AND o2.status = 3
                                  AND h2.updated_at >= :start
                                  AND h2.updated_at <= :end
                                  AND LOWER(IFNULL(o2.order_sources_name, '')) LIKE '%zalo%'
                                GROUP BY o2.creator_id, o2.id
                            ) as revenue_calc
                            GROUP BY creator_id
                        ) as rev_table WHERE rev_table.creator_id = o.creator_id LIMIT 1) as revenue
                    FROM orders o
                    INNER JOIN order_status_histories h ON o.id = h.order_id
                    LEFT JOIN pos_users pu ON pu.id = o.creator_id
                    WHERE h.new_status = 1
                    AND h.updated_at >= :start
                    AND h.updated_at <= :end
                    AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                    AND o.creator_id IS NOT NULL AND o.creator_id != ''
                    GROUP BY o.creator_id, pu.name
                    ORDER BY revenue DESC
                    """;
            } else if ("facebook".equals(sourceFilter)) {
                // Facebook: theo ngày tạo (inserted_at), đơn status=0
                sql = """
                    SELECT
                        o.creator_id,
                        COALESCE(NULLIF(TRIM(pu.name), ''),
                                 NULLIF(TRIM(o.last_editor_name), ''),
                                 NULLIF(TRIM(o.assigning_care_id), ''),
                                 o.creator_id) as name,
                        COUNT(DISTINCT CASE WHEN o.status = 0 THEN o.id END) as data_received,
                        COUNT(DISTINCT CASE WHEN o.status = 3 THEN o.id END) as orders_received,
                        COUNT(DISTINCT CASE WHEN o.status = 3 THEN o.id END) as completed,
                        COUNT(DISTINCT CASE WHEN o.status IN (5, 6, 7) THEN o.id END) as cancelled,
                        COALESCE(SUM(CASE WHEN o.status = 3 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as revenue
                    FROM orders o
                    LEFT JOIN pos_users pu ON pu.id = o.creator_id
                    WHERE o.inserted_at >= :start
                    AND o.inserted_at <= :end
                    AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                    AND o.creator_id IS NOT NULL AND o.creator_id != ''
                    GROUP BY o.creator_id, pu.name
                    ORDER BY revenue DESC
                    """;
            } else {
                // All: tất cả đơn status=0 theo ngày tạo
                sql = """
                    SELECT
                        o.creator_id,
                        COALESCE(NULLIF(TRIM(pu.name), ''),
                                 NULLIF(TRIM(o.last_editor_name), ''),
                                 NULLIF(TRIM(o.assigning_care_id), ''),
                                 o.creator_id) as name,
                        COUNT(DISTINCT CASE WHEN o.status = 0 THEN o.id END) as data_received,
                        COUNT(DISTINCT CASE WHEN o.status = 3 THEN o.id END) as orders_received,
                        COUNT(DISTINCT CASE WHEN o.status = 3 THEN o.id END) as completed,
                        COUNT(DISTINCT CASE WHEN o.status IN (5, 6, 7) THEN o.id END) as cancelled,
                        COALESCE(SUM(CASE WHEN o.status = 3 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as revenue
                    FROM orders o
                    LEFT JOIN pos_users pu ON pu.id = o.creator_id
                    WHERE o.inserted_at >= :start
                    AND o.inserted_at <= :end
                    AND o.creator_id IS NOT NULL AND o.creator_id != ''
                    GROUP BY o.creator_id, pu.name
                    ORDER BY revenue DESC
                    """;
            }

            Query query = em.createNativeQuery(sql);
            query.setParameter("start", startDateUtc);
            query.setParameter("end", endDateUtc);
            query.setMaxResults(limit);

            List<Object[]> rows = query.getResultList();
            int rank = 1;

            for (Object[] row : rows) {
                EmployeePerformanceDto emp = new EmployeePerformanceDto();
                emp.setRank(rank++);
                emp.setUserId((String) row[0]);
                emp.setFullName(row[1] != null ? (String) row[1] : (String) row[0]);
                emp.setPhone((String) row[0]);
                emp.setTotalDataReceived(row[2] != null ? ((Number) row[2]).intValue() : 0);
                emp.setTotalOrdersReceived(row[3] != null ? ((Number) row[3]).intValue() : 0);
                emp.setCompletedOrders(row[4] != null ? ((Number) row[4]).intValue() : 0);
                emp.setCancelledOrders(row[5] != null ? ((Number) row[5]).intValue() : 0);

                BigDecimal rev = row[6] != null ? new BigDecimal(row[6].toString()) : BigDecimal.ZERO;
                emp.setRevenue(rev);

                int dataReceived = emp.getTotalDataReceived();
                int completed = emp.getCompletedOrders();
                emp.setCompletionRate(dataReceived > 0
                    ? BigDecimal.valueOf(completed * 100.0 / dataReceived).setScale(1, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO);

                // LT metrics - tạm thời set 0, sẽ query riêng bên dưới
                emp.setLt(0);
                emp.setLt1(0);
                emp.setLt2(0);
                emp.setLt3(0);
                emp.setLt4(0);

                result.add(emp);
            }

            // Query LT metrics riêng cho từng nhân viên
            if (!result.isEmpty()) {
                Map<String, EmployeePerformanceDto> empMap = result.stream()
                    .collect(Collectors.toMap(EmployeePerformanceDto::getUserId, e -> e));

                // Build date filter tương ứng với source
                // Dùng placeholder :creatorIds để tránh lỗi quote concatenation
                String ltSql;
                if ("zalo".equals(sourceFilter)) {
                    ltSql = """
                        SELECT
                            o.creator_id,
                            -- LT lẻ: đơn lt_type=1 (đơn lẻ)
                            COUNT(DISTINCT CASE WHEN o.lt_type = 1 THEN o.id END) as lt_le_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 1 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt_le,
                            -- LT 1: lt_type=0 (đơn combo), lt_count_snapshot = 1
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 1 THEN o.id END) as lt1_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 1 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt1,
                            -- LT 2: lt_type=0, lt_count_snapshot = 2
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 2 THEN o.id END) as lt2_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 2 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt2,
                            -- LT 3: lt_type=0, lt_count_snapshot = 3
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 3 THEN o.id END) as lt3_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 3 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt3,
                            -- LT 4: lt_type=0, lt_count_snapshot >= 4
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot >= 4 THEN o.id END) as lt4_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot >= 4 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt4
                        FROM orders o
                        WHERE o.status = 3
                        AND o.inserted_at >= :start
                        AND o.inserted_at <= :end
                        AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                        AND o.creator_id IN (:creatorIds)
                        GROUP BY o.creator_id
                        """;
                } else if ("facebook".equals(sourceFilter)) {
                    ltSql = """
                        SELECT
                            o.creator_id,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 1 THEN o.id END) as lt_le_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 1 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt_le,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 1 THEN o.id END) as lt1_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 1 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt1,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 2 THEN o.id END) as lt2_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 2 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt2,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 3 THEN o.id END) as lt3_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 3 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt3,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot >= 4 THEN o.id END) as lt4_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot >= 4 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt4
                        FROM orders o
                        WHERE o.status = 3
                        AND o.inserted_at >= :start
                        AND o.inserted_at <= :end
                        AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                        AND o.creator_id IN (:creatorIds)
                        GROUP BY o.creator_id
                        """;
                } else {
                    ltSql = """
                        SELECT
                            o.creator_id,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 1 THEN o.id END) as lt_le_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 1 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt_le,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 1 THEN o.id END) as lt1_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 1 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt1,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 2 THEN o.id END) as lt2_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 2 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt2,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 3 THEN o.id END) as lt3_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot = 3 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt3,
                            COUNT(DISTINCT CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot >= 4 THEN o.id END) as lt4_customers,
                            COALESCE(SUM(CASE WHEN o.lt_type = 0 AND o.lt_count_snapshot >= 4 THEN COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END), 0) as dt_lt4
                        FROM orders o
                        WHERE o.status = 3
                        AND o.inserted_at >= :start
                        AND o.inserted_at <= :end
                        AND o.creator_id IN (:creatorIds)
                        GROUP BY o.creator_id
                        """;
                }

                Query ltQuery = em.createNativeQuery(ltSql);
                ltQuery.setParameter("start", startDateUtc);
                ltQuery.setParameter("end", endDateUtc);
                ltQuery.setParameter("creatorIds", result.stream().map(EmployeePerformanceDto::getUserId).collect(Collectors.toList()));

                @SuppressWarnings("unchecked")
                List<Object[]> ltRows = ltQuery.getResultList();
                log.info("LT query returned {} rows for {} employees", ltRows.size(), result.size());
                for (Object[] ltRow : ltRows) {
                    String creatorId = (String) ltRow[0];
                    EmployeePerformanceDto emp = empMap.get(creatorId);
                    if (emp == null) {
                        log.warn("LT row creator_id {} not found in empMap", creatorId);
                        continue;
                    }

                    int ltLeOrders = ltRow[1] != null ? ((Number) ltRow[1]).intValue() : 0;
                    BigDecimal dtLtLe = ltRow[2] != null ? new BigDecimal(ltRow[2].toString()) : BigDecimal.ZERO;
                    int lt1Orders = ltRow[3] != null ? ((Number) ltRow[3]).intValue() : 0;
                    BigDecimal dtLt1 = ltRow[4] != null ? new BigDecimal(ltRow[4].toString()) : BigDecimal.ZERO;
                    int lt2Orders = ltRow[5] != null ? ((Number) ltRow[5]).intValue() : 0;
                    BigDecimal dtLt2 = ltRow[6] != null ? new BigDecimal(ltRow[6].toString()) : BigDecimal.ZERO;
                    int lt3Orders = ltRow[7] != null ? ((Number) ltRow[7]).intValue() : 0;
                    BigDecimal dtLt3 = ltRow[8] != null ? new BigDecimal(ltRow[8].toString()) : BigDecimal.ZERO;
                    int lt4Orders = ltRow[9] != null ? ((Number) ltRow[9]).intValue() : 0;
                    BigDecimal dtLt4 = ltRow[10] != null ? new BigDecimal(ltRow[10].toString()) : BigDecimal.ZERO;

                    emp.setLtLe(ltLeOrders);
                    emp.setDtLtLe(dtLtLe);
                    emp.setDtLt1(dtLt1);
                    emp.setDtLt2(dtLt2);
                    emp.setDtLt3(dtLt3);
                    emp.setDtLt4(dtLt4);
                    emp.setLt(lt1Orders + lt2Orders + lt3Orders + lt4Orders);
                    emp.setLt1(lt1Orders);
                    emp.setLt2(lt2Orders);
                    emp.setLt3(lt3Orders);
                    emp.setLt4(lt4Orders);
                }
            }

        } catch (Exception e) {
            log.error("Error getting top employees", e);
        }

        return result;
    }

    @Transactional(readOnly = true)
    public ReportDto getReport(LocalDateTime fromDate, LocalDateTime toDate, String source) {
        ReportDto report = new ReportDto();
        report.setFromDate(fromDate.toLocalDate().toString());
        report.setToDate(toDate.toLocalDate().toString());
        
        List<EmployeePerformanceDto> employees = getTopEmployees(fromDate, toDate, Integer.MAX_VALUE, source);
        
        report.setData(employees);
        report.setTotalEmployees(employees.size());
        report.setTotalDataReceived(employees.stream().mapToLong(EmployeePerformanceDto::getTotalDataReceived).sum());
        report.setTotalRevenue(employees.stream().map(EmployeePerformanceDto::getRevenue).reduce(BigDecimal.ZERO, BigDecimal::add));
        
        return report;
    }

    @Transactional(readOnly = true)
    public ChartDataDto getChartData(int year, int month, String source) {
        ChartDataDto dto = new ChartDataDto();
        List<String> labels = new ArrayList<>();
        List<Double> revenues = new ArrayList<>();
        
        try {
            LocalDate firstDay = LocalDate.of(year, month, 1);
            int daysInMonth = firstDay.lengthOfMonth();
            String sourceFilter = getSourceFilter(source);
            
            for (int day = 1; day <= daysInMonth; day++) {
                LocalDate date = LocalDate.of(year, month, day);
                LocalDateTime dayStart = date.atStartOfDay();
                LocalDateTime dayEnd = date.atTime(LocalTime.MAX);
                
                BigDecimal dailyRevenue = sumConfirmedReceivedRevenue(dayStart, dayEnd, sourceFilter);
                revenues.add(dailyRevenue.doubleValue());
                labels.add(String.format("%02d", day));
            }
            
            dto.setLabels(labels);
            dto.setRevenues(revenues);
            
        } catch (Exception e) {
            log.error("Error getting chart data", e);
        }
        
        return dto;
    }

    /**
     * Tăng trưởng doanh thu theo từng ngày (hoặc theo tháng với preset=month6).
     * Trả về: { items: [{ key, label, revenue }], total }
     * - reportYear, reportMonth: dùng với preset=month6 để build 6 tháng từ tháng đang chọn.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRevenueGrowth(LocalDateTime fromDay, LocalDateTime toDay, String preset, Integer reportYear, Integer reportMonth, String source) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String sourceFilter = getSourceFilter(source);

        try {
            boolean useMonth = "month6".equalsIgnoreCase(preset);

            if (fromDay == null || toDay == null) {
                if (useMonth && reportYear != null && reportMonth != null) {
                    // Build 6 tháng từ tháng đang chọn (reportYear, reportMonth)
                    LocalDate baseMonth = LocalDate.of(reportYear, reportMonth, 1);
                    for (int i = 5; i >= 0; i--) {
                        LocalDate first = baseMonth.minusMonths(i).withDayOfMonth(1);
                        items.add(buildMonthBucket(first, sourceFilter));
                    }
                } else if (useMonth) {
                    LocalDate now = LocalDate.now();
                    for (int i = 5; i >= 0; i--) {
                        LocalDate first = now.minusMonths(i).withDayOfMonth(1);
                        items.add(buildMonthBucket(first, sourceFilter));
                    }
                } else {
                    LocalDate today = LocalDate.now();
                    for (int i = 6; i >= 0; i--) {
                        LocalDate d = today.minusDays(i);
                        items.add(buildDayBucket(d, sourceFilter));
                    }
                }
            } else {
                LocalDate from = fromDay.toLocalDate();
                LocalDate to = toDay.toLocalDate();
                if (from.isAfter(to)) {
                    LocalDate tmp = from;
                    from = to;
                    to = tmp;
                }
                long span = java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1;
                if (span > 366) {
                    result.put("error", "Khoảng ngày tối đa 366 ngày.");
                    result.put("items", items);
                    result.put("total", 0);
                    return result;
                }
                if (span >= 28) {
                    LocalDate cursor = from.withDayOfMonth(1);
                    LocalDate endMonth = to.withDayOfMonth(1);
                    while (!cursor.isAfter(endMonth)) {
                        items.add(buildMonthBucket(cursor, sourceFilter));
                        cursor = cursor.plusMonths(1);
                    }
                } else {
                    for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                        items.add(buildDayBucket(d, sourceFilter));
                    }
                }
            }

            for (Map<String, Object> it : items) {
                BigDecimal rev = (BigDecimal) it.get("revenue");
                if (rev != null) total = total.add(rev);
            }
        } catch (Exception e) {
            log.error("Error getting revenue growth", e);
            result.put("error", e.getMessage());
        }

        result.put("items", items);
        result.put("total", total);
        return result;
    }

    private Map<String, Object> buildDayBucket(LocalDate day, String source) {
        Map<String, Object> it = new LinkedHashMap<>();
        LocalDateTime start = day.atStartOfDay();
        LocalDateTime end = day.atTime(LocalTime.MAX);
        BigDecimal rev = sumConfirmedReceivedRevenue(start, end, source);
        String key = day.toString();
        String label = String.format("%02d/%02d", day.getDayOfMonth(), day.getMonthValue());
        it.put("key", key);
        it.put("label", label);
        it.put("revenue", rev);
        return it;
    }

    private Map<String, Object> buildMonthBucket(LocalDate monthStart, String source) {
        Map<String, Object> it = new LinkedHashMap<>();
        LocalDate monthEnd = monthStart.withDayOfMonth(monthStart.lengthOfMonth());
        LocalDateTime start = monthStart.atStartOfDay();
        LocalDateTime end = monthEnd.atTime(LocalTime.MAX);
        BigDecimal rev = sumConfirmedReceivedRevenue(start, end, source);
        String key = monthStart.toString().substring(0, 7);
        String label = String.format("%02d/%d", monthStart.getMonthValue(), monthStart.getYear());
        it.put("key", key);
        it.put("label", label);
        it.put("revenue", rev);
        return it;
    }

    /**
     * Sum revenue với filter source - dùng cho revenue-growth.
     * Doanh thu = cod + prepaid của đơn status=3
     * - Facebook/All: base date = inserted_at
     * - Zalo: base date = h.updated_at (ngày xác nhận)
     */
    private BigDecimal sumConfirmedReceivedRevenue(LocalDateTime start, LocalDateTime end, String source) {
        String sql;
        if ("zalo".equals(source)) {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1
                AND h.updated_at >= :start
                AND h.updated_at <= :end
                AND o.status = 3
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                """;
        } else if ("facebook".equals(source)) {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start
                AND o.inserted_at <= :end
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                """;
        } else {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start
                AND o.inserted_at <= :end
                """;
        }
        Query q = em.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("end", end);
        Object r = q.getSingleResult();
        return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
    }

    /**
     * Doanh thu từng ngày của 1 nhân viên trong khoảng fromDay -> toDay.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getEmployeeRevenueBreakdown(String creatorId, LocalDateTime fromDay, LocalDateTime toDay, String source) {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        String sourceFilter = getSourceFilter(source);

        try {
            LocalDate startDate = fromDay.toLocalDate();
            LocalDate endDate = toDay.toLocalDate();
            if (startDate.isAfter(endDate)) {
                LocalDate tmp = startDate;
                startDate = endDate;
                endDate = tmp;
            }
            for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                Map<String, Object> it = new LinkedHashMap<>();
                LocalDateTime dayStart = d.atStartOfDay();
                LocalDateTime dayEnd = d.atTime(LocalTime.MAX);
                BigDecimal rev = sumEmployeeRevenue(dayStart, dayEnd, creatorId, sourceFilter);

                it.put("key", d.toString());
                it.put("label", String.format("%02d/%02d", d.getDayOfMonth(), d.getMonthValue()));
                it.put("revenue", rev);
                items.add(it);
                total = total.add(rev);
            }
        } catch (Exception e) {
            log.error("Error getting employee revenue breakdown", e);
            result.put("error", e.getMessage());
        }

        result.put("items", items);
        result.put("total", total);
        return result;
    }

    private BigDecimal sumEmployeeRevenue(LocalDateTime start, LocalDateTime end, String creatorId, String source) {
        String sql;
        if ("zalo".equals(source)) {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                INNER JOIN order_status_histories h ON o.id = h.order_id
                WHERE h.new_status = 1
                AND h.updated_at >= :start
                AND h.updated_at <= :end
                AND o.status = 3
                AND o.creator_id = :creatorId
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%zalo%'
                """;
        } else if ("facebook".equals(source)) {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start
                AND o.inserted_at <= :end
                AND o.creator_id = :creatorId
                AND LOWER(IFNULL(o.order_sources_name, '')) LIKE '%facebook%'
                """;
        } else {
            sql = """
                SELECT COALESCE(SUM(COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0)), 0)
                FROM orders o
                WHERE o.status = 3
                AND o.inserted_at >= :start
                AND o.inserted_at <= :end
                AND o.creator_id = :creatorId
                """;
        }
        Query q = em.createNativeQuery(sql);
        q.setParameter("start", start);
        q.setParameter("end", end);
        q.setParameter("creatorId", creatorId);
        Object r = q.getSingleResult();
        return r != null ? new BigDecimal(r.toString()) : BigDecimal.ZERO;
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> getActiveUsers() {
        // Lấy users từ orders thay vì bảng users
        String sql = """
            SELECT DISTINCT o.creator_id as user_id, 
                   COALESCE(MAX(o.last_editor_name), MAX(o.assigning_care_id), o.creator_id) as name
            FROM orders o 
            WHERE o.creator_id IS NOT NULL AND o.creator_id != ''
            GROUP BY o.creator_id
            ORDER BY name
            """;
        Query query = em.createNativeQuery(sql);
        return query.getResultList();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> getOrderStatsByUser(LocalDateTime start, LocalDateTime end) {
        String sql = """
            SELECT 
                o.creator_id,
                COUNT(DISTINCT CASE WHEN h.new_status = 1 THEN o.id END) as data_received,
                COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status = 3 THEN o.id END) as orders_received,
                COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status = 3 THEN o.id END) as completed,
                COUNT(DISTINCT CASE WHEN h.new_status = 1 AND o.status IN (5, 6, 7) THEN o.id END) as cancelled,
                SUM(CASE WHEN h.new_status = 1 AND o.status = 3 THEN COALESCE(o.total_price, 0) + COALESCE(o.cod, 0) + COALESCE(o.prepaid, 0) ELSE 0 END) as revenue
            FROM orders o
            INNER JOIN order_status_histories h ON o.id = h.order_id
            WHERE h.new_status = 1 
            AND h.updated_at >= :start 
            AND h.updated_at <= :end
            AND o.creator_id IS NOT NULL
            GROUP BY o.creator_id
            """;
        Query query = em.createNativeQuery(sql);
        query.setParameter("start", start);
        query.setParameter("end", end);
        
        List<Object[]> rows = query.getResultList();
        List<Map<String, Object>> results = new ArrayList<>();
        
        for (Object[] row : rows) {
            Map<String, Object> map = new HashMap<>();
            map.put("creator_id", row[0]);
            map.put("data_received", row[1]);
            map.put("orders_received", row[2]);
            map.put("completed", row[3]);
            map.put("cancelled", row[4]);
            map.put("revenue", row[5]);
            results.add(map);
        }
        
        return results;
    }

    @SuppressWarnings("unchecked")
    public int calculateLT(LocalDateTime start, LocalDateTime end, String creatorId) {
        String ordersSql = """
            SELECT o.id, o.total_price
            FROM orders o
            INNER JOIN order_status_histories h ON o.id = h.order_id
            WHERE h.new_status = 1 
            AND h.updated_at >= :start 
            AND h.updated_at <= :end
            AND o.status = 3
            AND (:creatorId IS NULL OR o.creator_id = :creatorId)
            AND o.total_price >= :minAmount
            """;
        
        Query ordersQuery = em.createNativeQuery(ordersSql);
        ordersQuery.setParameter("start", start);
        ordersQuery.setParameter("end", end);
        ordersQuery.setParameter("creatorId", creatorId);
        ordersQuery.setParameter("minAmount", MIN_LT_AMOUNT.doubleValue());
        
        List<Object[]> orders = ordersQuery.getResultList();
        int ltCount = 0;
        
        for (Object[] orderRow : orders) {
            Long orderId = ((Number) orderRow[0]).longValue();
            
            String itemsSql = """
                SELECT p.name 
                FROM order_items oi
                LEFT JOIN products p ON oi.product_id = p.id
                WHERE oi.order_id = :orderId
                """;
            
            Query itemsQuery = em.createNativeQuery(itemsSql);
            itemsQuery.setParameter("orderId", orderId);
            
            List<String> productNames = itemsQuery.getResultList();
            
            boolean hasShippingFee = productNames.stream()
                .anyMatch(name -> name != null && name.toLowerCase().contains(SHIPPING_FEE_PRODUCT.toLowerCase()));
            
            if (hasShippingFee) continue;
            
            long distinctProducts = productNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .distinct()
                .count();
            
            if (distinctProducts >= MIN_LT_PRODUCTS) {
                ltCount++;
            }
        }

        return ltCount;
    }

    /**
     * Lấy danh sách đơn hàng chi tiết trong khoảng thời gian.
     * - useInsertedAt: nếu true lọc theo orders.inserted_at; false lọc theo order_status_histories.updated_at (status=1).
     * - zaloOnly: nếu true lọc theo order_sources_name chứa 'zalo'.
     * - creatorId: tuỳ chọn (filter theo orders.creator_id).
     */
    @Transactional(readOnly = true)
    public List<OrderDetailDto> getOrderDetails(LocalDateTime fromDate, LocalDateTime toDate, String creatorId, Boolean useInsertedAt, Boolean zaloOnly, int limit) {
        List<OrderDetailDto> result = new ArrayList<>();
        try {
            StringBuilder sql = new StringBuilder();
            boolean needCreatorParam = creatorId != null && !creatorId.isBlank();

            // Chỉ lấy đơn status = 3 (completed)
            if (Boolean.TRUE.equals(useInsertedAt)) {
                sql.append("SELECT o.id, o.order_code, o.status, o.status_name, o.total_price, ")
                   .append("o.inserted_at, o.creator_id, u.name AS creator_name, o.order_sources_name, ")
                   .append("o.bill_full_name, o.bill_phone_number, c.lt_count ")
                   .append("FROM orders o ")
                   .append("LEFT JOIN customers c ON o.customer_id = c.id ")
                   .append("LEFT JOIN pos_users u ON o.creator_id = u.id ")
                   .append("WHERE o.inserted_at >= :start AND o.inserted_at <= :end ")
                   .append("AND o.status = 3 ");
            } else {
                sql.append("SELECT o.id, o.order_code, o.status, o.status_name, o.total_price, ")
                   .append("o.inserted_at, o.creator_id, u.name AS creator_name, o.order_sources_name, ")
                   .append("o.bill_full_name, o.bill_phone_number, c.lt_count ")
                   .append("FROM orders o ")
                   .append("LEFT JOIN customers c ON o.customer_id = c.id ")
                   .append("LEFT JOIN pos_users u ON o.creator_id = u.id ")
                   .append("WHERE EXISTS (SELECT 1 FROM order_status_histories h ")
                   .append("WHERE h.order_id = o.id AND h.new_status = 1 ")
                   .append("AND h.updated_at >= :start AND h.updated_at <= :end) ")
                   .append("AND o.status = 3 ");
            }
            if (needCreatorParam) sql.append("AND o.creator_id = :creatorId ");
            sql.append("ORDER BY o.id DESC");

            Query q = em.createNativeQuery(sql.toString());
            q.setParameter("start", fromDate);
            q.setParameter("end", toDate);
            if (needCreatorParam) q.setParameter("creatorId", creatorId);

            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();

            // Lấy products cho tất cả orders (batch)
            Set<Long> orderIds = rows.stream()
                .map(row -> row[0] != null ? ((Number) row[0]).longValue() : 0L)
                .collect(Collectors.toSet());
            Map<Long, String> productMap = loadOrderProducts(orderIds);

            for (Object[] row : rows) {
                OrderDetailDto dto = new OrderDetailDto();
                dto.setId(row[0] != null ? ((Number) row[0]).longValue() : 0L);
                dto.setOrderCode(row[1] != null ? String.valueOf(row[1]) : null);
                if (row[2] != null) dto.setStatus(((Number) row[2]).intValue());
                dto.setStatusName(row[3] != null ? String.valueOf(row[3]) : null);
                dto.setTotalPrice(toBigDecimal(row[4]));
                if (row[5] != null) {
                    Object t = row[5];
                    if (t instanceof java.sql.Timestamp) dto.setInsertedAt(((java.sql.Timestamp) t).toLocalDateTime());
                    else if (t instanceof java.time.LocalDateTime) dto.setInsertedAt((java.time.LocalDateTime) t);
                }
                dto.setCreatorId(row[6] != null ? String.valueOf(row[6]) : null);
                dto.setCreatorName(row[7] != null ? String.valueOf(row[7]) : null);
                dto.setOrderSourcesName(row[8] != null ? String.valueOf(row[8]) : null);
                dto.setCustomerName(row[9] != null ? String.valueOf(row[9]) : null);
                dto.setCustomerPhone(row[10] != null ? String.valueOf(row[10]) : null);
                // lt_count từ customers (cột 11)
                dto.setLtCount(row[11] != null ? ((Number) row[11]).intValue() : 0);
                // products từ order_items
                dto.setProducts(productMap.get(dto.getId()));
                result.add(dto);
            }
        } catch (Exception e) {
            log.error("Error getting order details", e);
        }
        return result;
    }

    /**
     * Load products cho nhiều orders (batch query)
     */
    private Map<Long, String> loadOrderProducts(Set<Long> orderIds) {
        Map<Long, String> result = new HashMap<>();
        if (orderIds == null || orderIds.isEmpty()) return result;

        try {
            // Dùng product_name trực tiếp từ order_items (không cần join products)
            String productSql = "SELECT oi.order_id, GROUP_CONCAT(oi.product_name ORDER BY oi.id SEPARATOR ', ') AS products " +
                "FROM order_items oi " +
                "WHERE oi.order_id IN (:orderIds) " +
                "GROUP BY oi.order_id";

            Query q = em.createNativeQuery(productSql);
            q.setParameter("orderIds", orderIds);
            @SuppressWarnings("unchecked")
            List<Object[]> rows = q.getResultList();
            for (Object[] row : rows) {
                Long orderId = row[0] != null ? ((Number) row[0]).longValue() : 0L;
                String products = row[1] != null ? String.valueOf(row[1]) : "";
                result.put(orderId, products);
            }
        } catch (Exception e) {
            log.warn("Error loading order products: {}", e.getMessage());
        }
        return result;
    }

    private static java.time.LocalDateTime convertToLocalDateTime(Object v) {
        if (v == null) return null;
        if (v instanceof java.time.LocalDateTime) return (java.time.LocalDateTime) v;
        if (v instanceof java.sql.Timestamp) return ((java.sql.Timestamp) v).toLocalDateTime();
        if (v instanceof java.util.Date) return ((java.util.Date) v).toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        return null;
    }

    private static java.math.BigDecimal toBigDecimal(Object v) {
        if (v == null) return java.math.BigDecimal.ZERO;
        if (v instanceof java.math.BigDecimal) return (java.math.BigDecimal) v;
        if (v instanceof Number) return java.math.BigDecimal.valueOf(((Number) v).doubleValue());
        return new java.math.BigDecimal(v.toString());
    }

    /**
     * Báo cáo hiệu suất: chi tiết từng nhân viên trong kỳ.
     * Trả về danh sách EmployeePerformanceDto đã đầy đủ (giống top-employees).
     */
    @Transactional(readOnly = true)
    public List<EmployeePerformanceDto> getPerformanceReport(LocalDateTime fromDate, LocalDateTime toDate, String source) {
        return getTopEmployees(fromDate, toDate, Integer.MAX_VALUE, source);
    }
}
