package mera.mera_v2.pos.sync.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.sync.service.*;
import mera.mera_v2.lark.token.LarkTokenService;
import mera.mera_v2.pos.sync.service.EmployeeSyncResult;
import mera.mera_v2.pos.sync.service.EmployeeSyncService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DongBoController {

    private final EmployeeSyncService employeeSyncService;
    private final LarkSyncOrchestratorService larkSyncOrchestratorService;
    private final LarkAttendanceSyncService larkAttendanceSyncService;
    private final LarkTokenService larkTokenService;

    @GetMapping("/dongbo")
    public String dongBoPage() {
        return "dongbo";
    }

    @GetMapping("/api/employees/sync")
    @ResponseBody
    public ResponseEntity<?> syncEmployees() {
        log.info("Employee sync API called");
        try {
            EmployeeSyncResult result = employeeSyncService.syncAllEmployees();
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.error("Employee sync failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ============== LARK SYNC APIs ==============

    @PostMapping("/api/lark/sync/departments")
    @ResponseBody
    public ResponseEntity<?> syncLarkDepartments(HttpSession session) {
        log.info("[API] Sync Lark departments");
        try {
            String userToken = larkTokenService.getAccessToken(session, false);
            log.info("[DEBUG] Got userToken for departments: mask={}", maskToken(userToken));
            DepartmentSyncResult result = larkSyncOrchestratorService.syncDepartments(userToken);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "inserted", result.getInserted(),
                "updated", result.getUpdated(),
                "skippedDeleted", result.getSkippedDeleted(),
                "message", result.getMessage()
            ));
        } catch (Exception e) {
            log.error("[API] Lark department sync failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/api/lark/sync/employees")
    @ResponseBody
    public ResponseEntity<?> syncLarkEmployees(HttpSession session) {
        log.info("[API] Sync Lark employees");
        try {
            String userToken = larkTokenService.getAccessToken(session, false);
            log.info("[DEBUG] Got userToken for employees: mask={}", maskToken(userToken));
            LarkEmployeeSyncResult result = larkSyncOrchestratorService.syncEmployees(userToken);
            return ResponseEntity.ok(Map.of(
                "success", true,
                "totalFromApi", result.getTotalFromApi(),
                "inserted", result.getInserted(),
                "updated", result.getUpdated(),
                "message", result.getMessage()
            ));
        } catch (Exception e) {
            log.error("[API] Lark employee sync failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/api/lark/sync/full")
    @ResponseBody
    public ResponseEntity<?> syncLarkFull(HttpSession session) {
        log.info("[API] Full Lark sync (departments + employees)");
        try {
            String userToken = larkTokenService.getAccessToken(session, false);
            log.info("[DEBUG] Got userToken for full sync: mask={}", maskToken(userToken));
            SyncResult result = larkSyncOrchestratorService.runFullSync(userToken);

            Map<String, Object> deptResult = Map.of(
                "inserted", result.getDepartmentResult().getInserted(),
                "updated", result.getDepartmentResult().getUpdated(),
                "skippedDeleted", result.getDepartmentResult().getSkippedDeleted()
            );

            Map<String, Object> empResult = Map.of(
                "totalFromApi", result.getLarkEmployeeResult().getTotalFromApi(),
                "inserted", result.getLarkEmployeeResult().getInserted(),
                "updated", result.getLarkEmployeeResult().getUpdated()
            );

            return ResponseEntity.ok(Map.of(
                "success", true,
                "departments", deptResult,
                "employees", empResult,
                "message", "Full sync completed"
            ));
        } catch (Exception e) {
            log.error("[API] Lark full sync failed: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                .body(Map.of("success", false, "error", e.getMessage()));
        }
    }

    @PostMapping("/api/lark/sync/attendance")
    @ResponseBody
    public ResponseEntity<?> syncLarkAttendance(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Boolean force) {

        log.info("[API] Sync Lark attendance: {} to {}", startDate, endDate);
        try {
            mera.mera_v2.lark.sync.dto.AttendanceSyncRequest request =
                new mera.mera_v2.lark.sync.dto.AttendanceSyncRequest();
            request.setStartDate(startDate);
            request.setEndDate(endDate);
            request.setForce(force != null && force);

            AttendanceSyncResult result = larkAttendanceSyncService.syncAttendance(request, "manual", null);
            log.info("[API] Sync result: success={}, status={}, message={}", 
                    result.isSuccess(), result.getStatus(), result.getMessage());

            Map<String, Object> response = new HashMap<>();
            response.put("success", result.isSuccess());
            response.put("jobId", result.getJobId() != null ? result.getJobId() : "");
            response.put("status", result.getStatus());
            response.put("totalEmployees", result.getTotalEmployees());
            response.put("totalRequests", result.getTotalRequests());
            response.put("totalSuccessEmployees", result.getTotalSuccessEmployees());
            response.put("totalInvalidEmployees", result.getTotalInvalidEmployees());
            response.put("totalFailedRequests", result.getTotalFailedRequests());
            response.put("message", result.getMessage());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] Lark attendance sync failed: {}", e.getMessage(), e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("error", e.getMessage() != null ? e.getMessage() : "Unknown error");
            return ResponseEntity.internalServerError().body(errorResponse);
        }
    }

    private String maskToken(String token) {
        if (token == null || token.isBlank()) return "(null)";
        int len = token.length();
        if (len <= 16) return "***";
        return token.substring(0, 8) + "..." + token.substring(len - 6);
    }
}
