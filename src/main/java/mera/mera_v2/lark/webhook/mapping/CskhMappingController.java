package mera.mera_v2.lark.webhook.mapping;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Xem va nap lai mapping CSKH -> Base/Table tu file JSON.
 */
@RestController
@RequestMapping("/api/lark/cskh-mapping")
@RequiredArgsConstructor
public class CskhMappingController {

    private final CskhMappingService cskhMappingService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> list() {
        Map<String, Object> body = new HashMap<>();
        body.put("total", cskhMappingService.size());
        body.put("loadedFrom", cskhMappingService.getLoadedFrom());
        body.put("mappings", cskhMappingService.getAll());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/reload")
    public ResponseEntity<Map<String, Object>> reload() {
        int total = cskhMappingService.reload();
        Map<String, Object> body = new HashMap<>();
        body.put("status", "ok");
        body.put("total", total);
        body.put("loadedFrom", cskhMappingService.getLoadedFrom());
        return ResponseEntity.ok(body);
    }
}
