package mera.mera_v2.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.assignment.OrderAssignmentConfigService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class SyncFeatureToggleService {

    public static final String KEY_ATTENDANCE = "attendance_sync_enabled";
    public static final String KEY_ASSIGNMENT = "order_assignment_enabled";

    private final OrderAssignmentConfigService configService;

    private final AtomicBoolean attendanceSyncEnabled = new AtomicBoolean(true);
    private final AtomicBoolean assignmentSyncEnabled = new AtomicBoolean(true);

    @PostConstruct
    public void init() {
        refresh();
    }

    public void refresh() {
        boolean attendance = configService.getConfigAsBoolean(KEY_ATTENDANCE);
        boolean assignment = configService.getConfigAsBoolean(KEY_ASSIGNMENT);
        attendanceSyncEnabled.set(attendance);
        assignmentSyncEnabled.set(assignment);
        log.info("Sync toggles loaded from DB: attendance={}, assignment={}", attendance, assignment);
    }

    public boolean isAttendanceSyncEnabled() {
        return attendanceSyncEnabled.get();
    }

    public boolean isAssignmentSyncEnabled() {
        return assignmentSyncEnabled.get();
    }

    @Transactional
    public void setAttendanceSyncEnabled(boolean enabled) {
        configService.updateConfig(KEY_ATTENDANCE, String.valueOf(enabled));
        attendanceSyncEnabled.set(enabled);
        log.info("attendance_sync_enabled persisted: {}", enabled);
    }

    @Transactional
    public void setAssignmentSyncEnabled(boolean enabled) {
        configService.updateConfig(KEY_ASSIGNMENT, String.valueOf(enabled));
        assignmentSyncEnabled.set(enabled);
        log.info("order_assignment_enabled persisted: {}", enabled);
    }
}