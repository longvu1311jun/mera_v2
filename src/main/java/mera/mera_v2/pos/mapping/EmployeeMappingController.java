package mera.mera_v2.pos.mapping;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/employee-mapping")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class EmployeeMappingController {

    private final EmployeeMappingService mappingService;

    @GetMapping("/lark-with-mapping")
    public ResponseEntity<List<EmployeeMappingService.LarkEmployeeWithMapping>> getLarkEmployeesWithMapping() {
        return ResponseEntity.ok(mappingService.getLarkEmployeesWithMapping());
    }

    @GetMapping("/pos-users")
    public ResponseEntity<List<EmployeeMappingService.PosUserSimple>> getPosUsers() {
        return ResponseEntity.ok(mappingService.getAllPosUsers());
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Long>> getStats() {
        return ResponseEntity.ok(mappingService.getStats());
    }

    @PostMapping("/save")
    public ResponseEntity<Map<String, Object>> saveMapping(@RequestBody Map<String, String> request) {
        String larkEmployeeId = request.get("larkEmployeeId");
        String posUserId = request.get("posUserId");
        
        if (larkEmployeeId == null || larkEmployeeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Thiếu larkEmployeeId"));
        }
        
        mappingService.saveMapping(larkEmployeeId, posUserId);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/save-all")
    public ResponseEntity<Map<String, Object>> saveAllMappings(@RequestBody List<Map<String, String>> mappings) {
        mappingService.saveMappings(mappings);
        return ResponseEntity.ok(Map.of("success", true, "saved", mappings.size()));
    }

    @PostMapping("/update-hire-date")
    public ResponseEntity<Map<String, Object>> updateHireDate(@RequestBody Map<String, String> request) {
        String larkEmployeeId = request.get("larkEmployeeId");
        String hireDateStr = request.get("hireDate");

        if (larkEmployeeId == null || larkEmployeeId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Thiếu larkEmployeeId"));
        }

        mappingService.updateSingleHireDate(larkEmployeeId, hireDateStr);
        return ResponseEntity.ok(Map.of("success", true));
    }

    @PostMapping("/update-hire-dates")
    public ResponseEntity<Map<String, Object>> updateHireDates(@RequestBody List<Map<String, String>> updates) {
        mappingService.updateHireDates(updates);
        return ResponseEntity.ok(Map.of("success", true, "updated", updates.size()));
    }
}
