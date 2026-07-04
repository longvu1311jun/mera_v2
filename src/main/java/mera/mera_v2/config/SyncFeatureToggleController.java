package mera.mera_v2.config;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/sync-toggle")
@RequiredArgsConstructor
public class SyncFeatureToggleController {

    private final SyncFeatureToggleService toggleService;

    @GetMapping
    public ResponseEntity<Map<String, Object>> getStatus() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("attendanceSyncEnabled", toggleService.isAttendanceSyncEnabled());
        body.put("assignmentSyncEnabled", toggleService.isAssignmentSyncEnabled());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/attendance")
    public ResponseEntity<Map<String, Object>> setAttendance(@RequestParam boolean enabled) {
        toggleService.setAttendanceSyncEnabled(enabled);
        return ResponseEntity.ok(status());
    }

    @PostMapping("/assignment")
    public ResponseEntity<Map<String, Object>> setAssignment(@RequestParam boolean enabled) {
        toggleService.setAssignmentSyncEnabled(enabled);
        return ResponseEntity.ok(status());
    }

    private Map<String, Object> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("attendanceSyncEnabled", toggleService.isAttendanceSyncEnabled());
        body.put("assignmentSyncEnabled", toggleService.isAssignmentSyncEnabled());
        return body;
    }
}