package mera.mera_v2.ltkach;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.ltkach.dto.LtKhachApiDashboardDto;
import mera.mera_v2.ltkach.dto.RevenueGrowthDto;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/ltkach")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LtKhachApiRestController {
  private static final Logger log = LoggerFactory.getLogger(LtKhachApiRestController.class);

    private final LtKhachApiService ltKhachApiService;
    private final LtCalculationService ltCalculationService;

    @PersistenceContext
    private EntityManager em;

    @GetMapping("/dashboard")
    public ResponseEntity<LtKhachApiDashboardDto> getDashboard(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime fromDate,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss") LocalDateTime toDate,
            @RequestParam(required = false, defaultValue = "all") String source) {

        if (fromDate == null || toDate == null) {
            LocalDate now = LocalDate.now();
            fromDate = now.withDayOfMonth(1).atStartOfDay();
            toDate = now.atTime(23, 59, 59);
        }

        log.info("[LtKhachApiRest] GET /dashboard from={}, to={}, source={}", fromDate, toDate, source);

        LtKhachApiDashboardDto dashboard = ltKhachApiService.getDashboard(fromDate, toDate, source);
        return ResponseEntity.ok(dashboard);
    }

    @GetMapping("/revenue-growth")
    public ResponseEntity<RevenueGrowthDto> getRevenueGrowth(
            @RequestParam(required = false, defaultValue = "all") String source) {

        log.info("[LtKhachApiRest] GET /revenue-growth source={}", source);

        RevenueGrowthDto growth = ltKhachApiService.getRevenueGrowth(source);
        return ResponseEntity.ok(growth);
    }

    @GetMapping("/performance")
    public ResponseEntity<?> getPerformance(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false, defaultValue = "all") String source) {

        log.info("[LtKhachApiRest] GET /performance fromDate={}, toDate={}, source={}", fromDate, toDate, source);

        LocalDateTime from;
        LocalDateTime to;

        if (fromDate != null && toDate != null) {
            from = LocalDate.parse(fromDate).atStartOfDay();
            to = LocalDate.parse(toDate).atTime(23, 59, 59);
        } else {
            LocalDate now = LocalDate.now();
            from = now.withDayOfMonth(1).atStartOfDay();
            to = now.atTime(23, 59, 59);
        }

        List<?> rows = ltKhachApiService.getPerformanceReport(from, to, source);
        return ResponseEntity.ok(rows);
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        return ResponseEntity.ok(Map.of(
            "status", "ok",
            "service", "lt-khach-api",
            "timestamp", System.currentTimeMillis()
        ));
    }

    @GetMapping("/order-details")
    public ResponseEntity<?> getOrderDetails(
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String toDate,
            @RequestParam(required = false, defaultValue = "false") Boolean zaloOnly,
            @RequestParam(required = false) String creatorId,
            @RequestParam(required = false, defaultValue = "false") Boolean useInsertedAtForDataReceived,
            @RequestParam(required = false, defaultValue = "500") Integer limit) {

        log.info("[LtKhachApiRest] GET /order-details from={}, to={}, zaloOnly={}, creatorId={}", fromDate, toDate, zaloOnly, creatorId);

        LocalDateTime from;
        LocalDateTime to;
        try {
            from = (fromDate != null && fromDate.contains("T"))
                ? LocalDateTime.parse(fromDate)
                : (fromDate != null) ? LocalDate.parse(fromDate).atStartOfDay()
                : LocalDate.now().withDayOfMonth(1).atStartOfDay();
            to = (toDate != null && toDate.contains("T"))
                ? LocalDateTime.parse(toDate)
                : (toDate != null) ? LocalDate.parse(toDate).atTime(23, 59, 59)
                : LocalDate.now().atTime(23, 59, 59);
        } catch (DateTimeParseException e) {
            log.error("Date parse error: from={}, to={}", fromDate, toDate, e);
            return ResponseEntity.badRequest().body(Map.of("error", "Định dạng ngày không hợp lệ. Dùng yyyy-MM-dd hoặc yyyy-MM-ddTHH:mm:ss"));
        }

        List<?> orders = ltKhachApiService.getOrderDetails(from, to, creatorId, useInsertedAtForDataReceived, zaloOnly, limit);
        return ResponseEntity.ok(Map.of("orders", orders));
    }

    /**
     * Recalculate LT cho tất cả orders.
     * POST /api/ltkach/recalculate
     */
    @PostMapping("/recalculate")
    public ResponseEntity<Map<String, Object>> recalculateAllLT() {
        log.info("[LtKhachApiRest] POST /recalculate - recalculating all LT");

        try {
            // Lấy tất cả orders có status = 3 (completed)
            @SuppressWarnings("unchecked")
            List<Object[]> orderRows = em.createNativeQuery(
                "SELECT o.id, o.status FROM orders o WHERE o.status = 3 ORDER BY o.inserted_at"
            ).getResultList();

            List<Long> orderIds = orderRows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .collect(Collectors.toList());

            Set<Long> completedOrderIds = orderRows.stream()
                .map(row -> ((Number) row[0]).longValue())
                .collect(Collectors.toSet());

            int total = orderIds.size();
            int success = 0;
            int failed = 0;
            List<Map<String, Object>> errors = new ArrayList<>();

            for (Long orderId : orderIds) {
                try {
                    ltCalculationService.calculateForOrder(orderId, true);
                    success++;
                } catch (Exception e) {
                    failed++;
                    if (errors.size() < 10) {
                        errors.add(Map.of("orderId", orderId, "error", e.getMessage()));
                    }
                    log.warn("Failed to calculate LT for order {}: {}", orderId, e.getMessage());
                }
            }

            Map<String, Object> result = new HashMap<>();
            result.put("totalOrders", total);
            result.put("success", success);
            result.put("failed", failed);
            result.put("errors", errors);
            result.put("message", String.format("Đã recalculate %d/%d orders", success, total));

            log.info("[LtKhachApiRest] Recalculate completed: total={}, success={}, failed={}", total, success, failed);
            return ResponseEntity.ok(result);

        } catch (Exception e) {
            log.error("[LtKhachApiRest] Error during recalculate: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Lỗi khi recalculate: " + e.getMessage()
            ));
        }
    }

    /**
     * Recalculate LT cho một customer cụ thể.
     * POST /api/ltkach/recalculate/customer/{customerId}
     */
    @PostMapping("/recalculate/customer/{customerId}")
    public ResponseEntity<Map<String, Object>> recalculateCustomerLT(@PathVariable String customerId) {
        log.info("[LtKhachApiRest] POST /recalculate/customer/{} - recalculating customer LT", customerId);

        try {
            int ltCount = ltCalculationService.recalculateForCustomer(customerId);

            return ResponseEntity.ok(Map.of(
                "customerId", customerId,
                "ltCount", ltCount,
                "message", "Đã recalculate cho customer"
            ));

        } catch (Exception e) {
            log.error("[LtKhachApiRest] Error recalculating customer {}: {}", customerId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Lỗi khi recalculate customer: " + e.getMessage()
            ));
        }
    }

    /**
     * Recalculate LT cho một order cụ thể.
     * POST /api/ltkach/recalculate/order/{orderId}
     */
    @PostMapping("/recalculate/order/{orderId}")
    public ResponseEntity<Map<String, Object>> recalculateOrderLT(@PathVariable Long orderId) {
        log.info("[LtKhachApiRest] POST /recalculate/order/{} - recalculating order LT", orderId);

        try {
            // Lấy status hiện tại của order
            Object[] orderRow = (Object[]) em.createNativeQuery(
                "SELECT id, status FROM orders WHERE id = :orderId"
            ).setParameter("orderId", orderId).getSingleResult();

            boolean isCompleted = orderRow != null && ((Number) orderRow[1]).intValue() == 3;

            LtCalculationService.LtResult result = ltCalculationService.calculateForOrder(orderId, isCompleted);

            return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "ltType", result.ltType(),
                "ltCount", result.ltCount(),
                "changed", result.changed(),
                "message", "Đã recalculate cho order"
            ));

        } catch (Exception e) {
            log.error("[LtKhachApiRest] Error recalculating order {}: {}", orderId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "error", "Lỗi khi recalculate order: " + e.getMessage()
            ));
        }
    }
}
