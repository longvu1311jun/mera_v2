package mera.mera_v2.pos.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.EmployeeMapping;
import mera.mera_v2.entity.OrderAssignment;
import mera.mera_v2.pos.sync.client.OrderApiClient;
import mera.mera_v2.pos.sync.dto.CustomerDTO;
import mera.mera_v2.pos.sync.dto.OrderApiDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/order-assignment")
@RequiredArgsConstructor
public class OrderAssignmentController {

    private final OrderAssignmentService orderAssignmentService;
    private final OrderApiClient orderApiClient;

    /**
     * Helper method to extract phone number from CustomerDTO.
     */
    private String getCustomerPhone(CustomerDTO customer) {
        if (customer == null) {
            return null;
        }
        var phoneNumbers = customer.getPhoneNumbers();
        if (phoneNumbers != null && !phoneNumbers.isEmpty()) {
            return phoneNumbers.get(0);
        }
        return null;
    }

    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayAssignments() {
        LocalDate today = LocalDate.now();
        log.info("GET /api/order-assignment/today");

        List<OrderAssignment> assignments = orderAssignmentService.getAssignmentsByDate(today);

        List<Map<String, Object>> assignmentList = assignments.stream()
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("orderId", a.getOrderId());
                    item.put("orderCode", a.getOrderCode());
                    item.put("employeeName", a.getLarkEmployeeName());
                    item.put("employeeId", a.getLarkEmployeeId());
                    item.put("customerName", a.getCustomerName());
                    item.put("customerPhone", a.getCustomerPhone());
                    item.put("assignedAt", a.getAssignedAt() != null ? a.getAssignedAt().toString() : null);
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", assignmentList);
        response.put("total", assignmentList.size());
        response.put("date", today.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Lay danh sach assignment theo ngay.
     * @param date Ngay can xem (yyyy-MM-dd), mac dinh hom nay
     */
    @GetMapping("/by-date")
    public ResponseEntity<Map<String, Object>> getAssignmentsByDate(
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now();

        log.info("GET /api/order-assignment/by-date - date: {}", targetDate);

        List<OrderAssignment> assignments = orderAssignmentService.getAssignmentsByDate(targetDate);

        List<Map<String, Object>> assignmentList = assignments.stream()
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("orderId", a.getOrderId());
                    item.put("orderCode", a.getOrderCode());
                    item.put("employeeName", a.getLarkEmployeeName());
                    item.put("employeeId", a.getLarkEmployeeId());
                    item.put("customerName", a.getCustomerName());
                    item.put("customerPhone", a.getCustomerPhone());
                    item.put("assignedAt", a.getAssignedAt() != null ? a.getAssignedAt().toString() : null);
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", assignmentList);
        response.put("total", assignmentList.size());
        response.put("date", targetDate.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Lay danh sach assignment chi tiet theo ngay va larkEmployeeId (dung cho modal chi tiet).
     */
    @GetMapping("/employee-detail")
    public ResponseEntity<Map<String, Object>> getEmployeeAssignmentDetail(
            @RequestParam String larkEmployeeId,
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now();

        log.info("GET /api/order-assignment/employee-detail - employeeId: {}, date: {}",
                larkEmployeeId, targetDate);

        List<OrderAssignment> assignments =
                orderAssignmentService.getAssignmentsByDateAndEmployeeId(targetDate, larkEmployeeId);

        List<Map<String, Object>> assignmentList = assignments.stream()
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("orderId", a.getOrderId());
                    item.put("orderCode", a.getOrderCode());
                    item.put("customerName", a.getCustomerName());
                    item.put("customerPhone", a.getCustomerPhone());
                    item.put("assignedAt", a.getAssignedAt() != null ? a.getAssignedAt().toString() : null);
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", assignmentList);
        response.put("total", assignmentList.size());
        response.put("date", targetDate.toString());
        response.put("larkEmployeeId", larkEmployeeId);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTodayStats() {
        return ResponseEntity.ok(orderAssignmentService.getStatsByDate(LocalDate.now()));
    }

    @GetMapping("/stats-by-date")
    public ResponseEntity<Map<String, Object>> getStatsByDate(
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now();

        log.info("GET /api/order-assignment/stats-by-date - date: {}", targetDate);
        return ResponseEntity.ok(orderAssignmentService.getStatsByDate(targetDate));
    }

    @GetMapping("/employees")
    public ResponseEntity<Map<String, Object>> getCheckedInEmployees() {
        return getCheckedInEmployeesByDate(java.time.LocalDate.now().toString());
    }

    /**
     * Lay danh sach nhan vien diem danh theo ngay.
     * @param date Ngay can xem (yyyy-MM-dd), mac dinh hom nay
     */
    @GetMapping("/employees-by-date")
    public ResponseEntity<Map<String, Object>> getCheckedInEmployeesByDate(
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now();

        log.info("GET /api/order-assignment/employees-by-date - date: {}", targetDate);

        List<OrderAssignmentService.EmployeeWithPunchStatus> employeesWithStatus =
                orderAssignmentService.getCheckedInEmployeesWithStatusByDate(targetDate);

        List<Map<String, Object>> employeeList = employeesWithStatus.stream()
                .map(ems -> {
                    EmployeeMapping em = ems.getEmployee();
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", em.getId());
                    item.put("larkEmployeeId", em.getLarkEmployeeId());
                    item.put("larkEmployeeName", em.getLarkEmployeeName());
                    item.put("posUserId", em.getPosUserId());
                    item.put("hireDate", em.getHireDate() != null ? em.getHireDate().toString() : null);
                    item.put("checkinTime", ems.getCheckinTime());
                    item.put("onTime", ems.isOnTime());
                    item.put("group", ems.getGroup().name());
                    item.put("todayAssignments", ems.getTodayAssignments());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", employeeList);
        response.put("total", employeeList.size());
        response.put("date", targetDate.toString());

        return ResponseEntity.ok(response);
    }

    /**
     * Lấy các đơn hàng chưa được phân công từ POS API.
     * So sánh với danh sách đã chia để tìm đơn miss.
     *
     * @param date Ngày cần kiểm tra (format: yyyy-MM-dd), mặc định hôm nay
     */
    @GetMapping("/unassigned")
    public ResponseEntity<Map<String, Object>> getUnassignedOrders(
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now();

        log.info("GET /api/order-assignment/unassigned - date: {}", targetDate);

        // Lấy thời gian bắt đầu và kết thúc ngày
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.plusDays(1).atStartOfDay();

        // Chuyển sang timestamp (giây)
        long startTimestamp = startOfDay.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endTimestamp = endOfDay.atZone(ZoneId.systemDefault()).toEpochSecond();

        // Lấy danh sách đơn đã chia trong ngày
        Set<Long> assignedOrderIds = orderAssignmentService.getAssignmentsByDate(targetDate).stream()
                .map(OrderAssignment::getOrderId)
                .collect(Collectors.toSet());

        log.info("Đã phân công {} đơn trong ngày", assignedOrderIds.size());

        // Gọi POS API để lấy đơn trong ngày
        List<OrderApiDto> allOrders = new ArrayList<>();
        int page = 1;
        int pageSize = 200;
        int totalFetched = 0;

        try {
            while (true) {
                var response = orderApiClient.fetchOrdersPage(
                        startTimestamp, endTimestamp, page, pageSize, "inserted_at", null);

                if (response.getData() == null || response.getData().isEmpty()) {
                    break;
                }

                allOrders.addAll(response.getData());
                totalFetched += response.getData().size();

                log.info("Fetched page {}, total orders: {}", page, totalFetched);

                if (page >= response.getTotalPages()) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi POS API: {}", e.getMessage());
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("error", "Không thể lấy đơn từ POS API");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }

        // Lọc đơn chưa phân công (assigning_care_id = null hoặc 0)
        List<Map<String, Object>> unassignedList = allOrders.stream()
                .filter(order -> {
                    // Đơn chưa phân công nếu assigning_care_id null hoặc rỗng
                    String careId = order.getAssigningCareId();
                    return careId == null || careId.isBlank() || careId.equals("0");
                })
                .filter(order -> {
                    // Loại trừ đơn đã có trong danh sách phân công
                    Long orderId = order.getSystemId();
                    return orderId != null && !assignedOrderIds.contains(orderId);
                })
                .map(order -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("systemId", order.getSystemId());
                    item.put("orderCode", order.getOrderCode());
                    item.put("customerName", order.getCustomer() != null ? order.getCustomer().getName() : null);
                    item.put("customerPhone", getCustomerPhone(order.getCustomer()));
                    item.put("totalPrice", order.getTotalPrice());
                    item.put("status", order.getStatus());
                    item.put("statusName", order.getStatusName());
                    item.put("insertedAt", order.getInsertedAt());
                    return item;
                })
                .collect(Collectors.toList());

        log.info("Tìm thấy {} đơn chưa phân công", unassignedList.size());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", targetDate.toString());
        response.put("totalOrders", allOrders.size());
        response.put("assignedCount", assignedOrderIds.size());
        response.put("unassignedCount", unassignedList.size());
        response.put("data", unassignedList);

        return ResponseEntity.ok(response);
    }

    /**
     * Resync - phân công tất cả đơn chưa được xử lý trong một khoảng thời gian.
     *
     * @param date Ngày cần resync (format: yyyy-MM-dd), mặc định hôm nay
     */
    @PostMapping("/resync")
    public ResponseEntity<Map<String, Object>> resyncUnassignedOrders(
            @RequestParam(required = false) String date) {

        LocalDate targetDate = (date != null && !date.isBlank())
                ? LocalDate.parse(date)
                : LocalDate.now();

        log.info("POST /api/order-assignment/resync - date: {}", targetDate);

        // Lấy thời gian bắt đầu và kết thúc ngày
        LocalDateTime startOfDay = targetDate.atStartOfDay();
        LocalDateTime endOfDay = targetDate.plusDays(1).atStartOfDay();

        long startTimestamp = startOfDay.atZone(ZoneId.systemDefault()).toEpochSecond();
        long endTimestamp = endOfDay.atZone(ZoneId.systemDefault()).toEpochSecond();

        // Lấy danh sách đơn đã chia trong ngày
        Set<Long> assignedOrderIds = orderAssignmentService.getAssignmentsByDate(targetDate).stream()
                .map(OrderAssignment::getOrderId)
                .collect(Collectors.toSet());

        // Gọi POS API để lấy đơn trong ngày
        List<OrderApiDto> allOrders = new ArrayList<>();
        int page = 1;
        int pageSize = 200;

        try {
            while (true) {
                var response = orderApiClient.fetchOrdersPage(
                        startTimestamp, endTimestamp, page, pageSize, "inserted_at", null);

                if (response.getData() == null || response.getData().isEmpty()) {
                    break;
                }

                allOrders.addAll(response.getData());

                if (page >= response.getTotalPages()) {
                    break;
                }
                page++;
            }
        } catch (Exception e) {
            log.error("Lỗi khi gọi POS API: {}", e.getMessage());
            Map<String, Object> errorResponse = new LinkedHashMap<>();
            errorResponse.put("error", "Không thể lấy đơn từ POS API");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.status(500).body(errorResponse);
        }

        // Lọc đơn chưa phân công
        List<OrderApiDto> unassignedOrders = allOrders.stream()
                .filter(order -> {
                    String careId = order.getAssigningCareId();
                    return careId == null || careId.isBlank() || careId.equals("0");
                })
                .filter(order -> {
                    Long orderId = order.getSystemId();
                    return orderId != null && !assignedOrderIds.contains(orderId);
                })
                .filter(order -> isFacebookOrder(order))
                .collect(Collectors.toList());

        log.info("Tìm thấy {} đơn chưa phân công cần xử lý", unassignedOrders.size());

        // Xử lý từng đơn
        List<Map<String, Object>> results = new ArrayList<>();
        int successCount = 0;
        int failCount = 0;

        for (OrderApiDto order : unassignedOrders) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("orderId", order.getSystemId());
            result.put("orderCode", order.getOrderCode());

            try {
                // Build customer info from OrderApiDto
                Map<String, String> customerInfo = new HashMap<>();
                customerInfo.put("name", order.getCustomer() != null ? order.getCustomer().getName() : null);
                customerInfo.put("phone", getCustomerPhone(order.getCustomer()));

                var assignResult = orderAssignmentService.assignOrderToNextCskh(
                        order.getSystemId(), order.getOrderCode(), customerInfo, null);
                result.put("assignedTo", assignResult.getEmployee().getLarkEmployeeName());
                result.put("status", "success");
                successCount++;
            } catch (Exception e) {
                log.error("Lỗi khi phân công đơn {}: {}", order.getSystemId(), e.getMessage());
                result.put("error", e.getMessage());
                result.put("status", "failed");
                failCount++;
            }

            results.add(result);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("date", targetDate.toString());
        response.put("totalUnassigned", unassignedOrders.size());
        response.put("successCount", successCount);
        response.put("failCount", failCount);
        response.put("results", results);

        log.info("Resync hoàn thành: {} thành công, {} thất bại", successCount, failCount);

        return ResponseEntity.ok(response);
    }

    /**
     * Kiem tra don hang co nguon Facebook hay khong.
     * Loc theo order_sources_name (uu tien) va page_id (fallback).
     */
    private boolean isFacebookOrder(OrderApiDto order) {
        if (order == null) return false;

        // Kiem tra order_sources_name truoc (chinh xac nhat)
        String orderSourcesName = order.getOrderSourcesName();
        if (orderSourcesName != null && !orderSourcesName.isBlank()) {
            String lower = orderSourcesName.toLowerCase().trim();
            // Neu ten nguon chua "zalo", "shopee", "lazada"... thi KHONG phai Facebook
            if (lower.contains("zalo") || lower.contains("shopee")
                    || lower.contains("lazada") || lower.contains("tiktok")
                    || lower.contains("tiki") || lower.contains("sendo")
                    || lower.contains("website") || lower.contains("tong_dai")) {
                return false;
            }
            // Neu ten chua "facebook" hoac "fb" thi la Facebook
            if (lower.contains("facebook") || lower.contains("fb") || lower.contains("mess")) {
                return true;
            }
        }

        // Fallback: kiem tra page_id format (Facebook)
        String pageId = order.getPageId();
        if (pageId == null || pageId.isBlank()) return false;
        return pageId.startsWith("pzl_") || pageId.startsWith("fb_");
    }
}
