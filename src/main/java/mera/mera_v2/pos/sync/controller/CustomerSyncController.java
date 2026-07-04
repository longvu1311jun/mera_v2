package mera.mera_v2.pos.sync.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.dto.CustomerSyncRequest;
import mera.mera_v2.pos.sync.service.CustomerSyncResult;
import mera.mera_v2.pos.sync.service.CustomerSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/customers")
@RequiredArgsConstructor
public class CustomerSyncController {

    private final CustomerSyncService customerSyncService;

    @PostMapping("/sync")
    public ResponseEntity<?> syncCustomers(@RequestBody CustomerSyncRequest request) {
        log.info("Customer sync request: startDate={}, endDate={}, pageSize={}",
                request.getStartDate(), request.getEndDate(), request.getPageSize());

        if (request.getStartDate() == null || request.getStartDate().isBlank()
                || request.getEndDate() == null || request.getEndDate().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "startDate va endDate la bat buoc"));
        }

        try {
            CustomerSyncResult result = customerSyncService.syncCustomers(
                    request.getStartDate(),
                    request.getEndDate(),
                    request.getPageSize());
            return ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            log.error("Customer sync failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Loi dong bo: " + e.getMessage()));
        }
    }
}