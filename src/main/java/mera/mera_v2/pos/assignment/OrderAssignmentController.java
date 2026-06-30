package mera.mera_v2.pos.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.EmployeeMapping;
import mera.mera_v2.entity.OrderAssignment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/api/order-assignment")
@RequiredArgsConstructor
public class OrderAssignmentController {

    private final OrderAssignmentService orderAssignmentService;

    @GetMapping("/today")
    public ResponseEntity<Map<String, Object>> getTodayAssignments() {
        log.info("GET /api/order-assignment/today");

        List<OrderAssignment> assignments = orderAssignmentService.getTodayAssignments();

        List<Map<String, Object>> assignmentList = assignments.stream()
                .map(a -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", a.getId());
                    item.put("orderId", a.getOrderId());
                    item.put("employeeName", a.getLarkEmployeeName());
                    item.put("customerName", a.getCustomerName());
                    item.put("customerPhone", a.getCustomerPhone());
                    item.put("assignedAt", a.getAssignedAt() != null ? a.getAssignedAt().toString() : null);
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", assignmentList);
        response.put("total", assignmentList.size());
        response.put("date", java.time.LocalDate.now().toString());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getTodayStats() {
        log.info("GET /api/order-assignment/stats");

        Map<String, Object> stats = orderAssignmentService.getTodayStats();
        return ResponseEntity.ok(stats);
    }

    @GetMapping("/employees")
    public ResponseEntity<Map<String, Object>> getCheckedInEmployees() {
        log.info("GET /api/order-assignment/employees");

        List<EmployeeMapping> employees = orderAssignmentService.getTodayCheckedInEmployeesSorted();

        List<Map<String, Object>> employeeList = employees.stream()
                .map(em -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", em.getId());
                    item.put("larkEmployeeId", em.getLarkEmployeeId());
                    item.put("larkEmployeeName", em.getLarkEmployeeName());
                    item.put("posUserId", em.getPosUserId());
                    item.put("hireDate", em.getHireDate() != null ? em.getHireDate().toString() : null);
                    item.put("checkinTime", orderAssignmentService.getCheckinTime(em.getLarkEmployeeId()));
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("data", employeeList);
        response.put("total", employeeList.size());
        response.put("date", java.time.LocalDate.now().toString());

        return ResponseEntity.ok(response);
    }
}
