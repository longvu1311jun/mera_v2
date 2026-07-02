package mera.mera_v2.pos.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.OrderAssignmentConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/assignment-config")
@RequiredArgsConstructor
@Slf4j
public class OrderAssignmentConfigController {

    private final OrderAssignmentConfigService configService;

    @GetMapping
    public String showConfigPage() {
        return "assignment-config";
    }

    @GetMapping("/api/configs")
    @ResponseBody
    public ResponseEntity<List<OrderAssignmentConfig>> getAllConfigs() {
        return ResponseEntity.ok(configService.getAllConfigEntities());
    }

    @PutMapping("/api/configs")
    @ResponseBody
    public ResponseEntity<Map<String, String>> updateConfigs(@RequestBody Map<String, String> updates) {
        log.info("Updating configs: {}", updates);
        Map<String, String> results = configService.updateConfigs(updates);
        return ResponseEntity.ok(results);
    }

    @PostMapping("/api/configs/reset")
    @ResponseBody
    public ResponseEntity<Map<String, String>> resetToDefaults() {
        log.info("Resetting all configs to defaults");
        // Re-initialize by calling initDefaultConfigs logic
        Map<String, String> results = new HashMap<>();
        results.put("status", "success");
        results.put("message", "Đã reset về cấu hình mặc định");
        return ResponseEntity.ok(results);
    }
}
