package mera.mera_v2.pos.sync.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.service.EmployeeSyncResult;
import mera.mera_v2.pos.sync.service.EmployeeSyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.Map;

@Slf4j
@Controller
@RequiredArgsConstructor
public class DongBoController {

    private final EmployeeSyncService employeeSyncService;

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
}
