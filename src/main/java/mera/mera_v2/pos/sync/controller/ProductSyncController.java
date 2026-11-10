package mera.mera_v2.pos.sync.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.service.ProductSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductSyncController {

    private final ProductSyncService productSyncService;

    // ponytail: bo qua startDate/endDate tu UI - shop chi ~100 SP nen moi lan keo het; them loc khi SP len hang chuc nghin
    @PostMapping("/sync")
    public ResponseEntity<?> sync() {
        try {
            return ResponseEntity.ok(productSyncService.syncAll());
        } catch (Exception e) {
            log.error("Product sync failed: {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Loi dong bo san pham: " + e.getMessage()));
        }
    }
}
