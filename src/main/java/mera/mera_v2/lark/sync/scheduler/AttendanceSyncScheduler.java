package mera.mera_v2.lark.sync.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.config.SyncFeatureToggleService;
import mera.mera_v2.entity.LarkEmployee;
import mera.mera_v2.lark.sync.dto.AttendanceSyncRequest;
import mera.mera_v2.lark.sync.service.AttendanceSyncResult;
import mera.mera_v2.lark.sync.service.LarkAttendanceSyncService;
import mera.mera_v2.repository.LarkAttendancePunchRepository;
import mera.mera_v2.repository.LarkEmployeeRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSyncScheduler {

    private final LarkAttendanceSyncService attendanceSyncService;
    private final LarkEmployeeRepository employeeRepository;
    private final LarkAttendancePunchRepository attendancePunchRepository;
    private final SyncFeatureToggleService featureToggle;

    private static final LocalTime FULL_SYNC_TIME = LocalTime.of(7, 59);
    private static final LocalTime SCHEDULE_START = LocalTime.of(7, 59);
    private static final LocalTime SCHEDULE_END = LocalTime.of(17, 0);

    private static LocalDate lastFullSyncDate = null;
    private static final Set<String> syncedTodayEmployeeIds = Collections.synchronizedSet(new HashSet<>());
    private static final Object syncLock = new Object();

    @Scheduled(cron = "0 59 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void dailyFullSync() {
        if (!featureToggle.isAttendanceSyncEnabled()) {
            log.info("[scheduler] Skipping daily full sync - feature DISABLED via toggle");
            return;
        }
        if (!shouldRun()) {
            log.info("[scheduler] Skipping daily full sync - outside work hours");
            return;
        }

        LocalDate today = LocalDate.now();
        log.info("[scheduler] Starting daily full attendance sync for date: {}", today);

        performFullSync(today);
    }

    @Scheduled(fixedRate = 60000)
    public void incrementalSyncMissingEmployees() {
        if (!featureToggle.isAttendanceSyncEnabled()) {
            log.debug("[scheduler] Skipping incremental sync - feature DISABLED via toggle");
            return;
        }
        LocalTime now = LocalTime.now();
        log.info("[scheduler] Incremental sync triggered at {}", now);

        if (!shouldRun()) {
            log.debug("[scheduler] Outside work hours, skipping");
            return;
        }

        if (now.isBefore(FULL_SYNC_TIME)) {
            log.debug("[scheduler] Before full sync time (7:59), skipping");
            return;
        }

        LocalDate today = LocalDate.now();

        try {
            // Get employees who have punch records but missing check-in time
            List<String> employeesMissingCheckin = attendancePunchRepository.findEmployeeIdsMissingCheckin(today);

            log.info("[scheduler] Found {} employees with missing checkin time", employeesMissingCheckin.size());

            if (employeesMissingCheckin.isEmpty()) {
                log.debug("[scheduler] All employees have check-in time");
                return;
            }

            log.info("[scheduler] Syncing {} employees missing checkin: {}", employeesMissingCheckin.size(), employeesMissingCheckin);

            AttendanceSyncRequest request = new AttendanceSyncRequest();
            request.setStartDate(today);
            request.setEndDate(today);
            request.setEmployeeIds(employeesMissingCheckin);
            request.setForce(true);

            AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "scheduler_incremental", "system");

            log.info("[scheduler] Incremental sync completed: success={}, synced={}, message={}",
                    result.isSuccess(), result.getTotalSuccessEmployees(), result.getMessage());

        } catch (Exception e) {
            log.error("[scheduler] Incremental attendance sync failed: {}", e.getMessage(), e);
        }
    }

    /**
     * Ensure full sync has been done for today.
     * If not, perform full sync for all employees.
     */
    private void ensureFullSyncDone(LocalDate today) {
        if (lastFullSyncDate != null && lastFullSyncDate.equals(today)) {
            // Full sync already done today
            return;
        }

        log.info("[scheduler] Full sync not done yet today, performing now...");

        // Check if we have any punch records for today
        Set<String> employeesWithPunch = getEmployeesWithAnyPunchToday(today);

        if (employeesWithPunch.isEmpty()) {
            // No punch records, do full sync
            performFullSync(today);
        } else {
            // We have some punch records, mark them as synced
            log.info("[scheduler] Found {} employees with existing punch records, marking as synced",
                    employeesWithPunch.size());
            syncedTodayEmployeeIds.addAll(employeesWithPunch);
            lastFullSyncDate = today;
        }
    }

    private void performFullSync(LocalDate today) {
        synchronized (syncLock) {
            // Reset synced list for new day
            lastFullSyncDate = today;
            syncedTodayEmployeeIds.clear();

            try {
                AttendanceSyncRequest request = new AttendanceSyncRequest();
                request.setStartDate(today);
                request.setEndDate(today);
                request.setForce(true);

                AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "scheduler_daily", "system");

                log.info("[scheduler] Daily sync completed: success={}, employees={}, message={}",
                        result.isSuccess(), result.getTotalEmployees(), result.getMessage());

                // Mark all employees as synced after full sync
                markAllEmployeesAsSynced();

            } catch (Exception e) {
                log.error("[scheduler] Daily attendance sync failed: {}", e.getMessage(), e);
            }
        }
    }

    private Set<String> getEmployeesWithAnyPunchToday(LocalDate date) {
        List<String> ids = attendancePunchRepository.findDistinctEmployeeIdsWithAnyPunch(date);
        return ids.stream().filter(id -> id != null).collect(Collectors.toSet());
    }

    private void markAllEmployeesAsSynced() {
        List<LarkEmployee> allEmployees = employeeRepository.findAll();
        allEmployees.stream()
                .map(LarkEmployee::getId)
                .filter(id -> id != null && !id.isBlank())
                .forEach(syncedTodayEmployeeIds::add);
        log.info("[scheduler] Marked {} employees as synced", syncedTodayEmployeeIds.size());
    }

    private boolean shouldRun() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(SCHEDULE_START) && !now.isAfter(SCHEDULE_END);
    }

    public void triggerManualFullSync() {
        LocalDate today = LocalDate.now();
        log.info("[manual] Triggering full attendance sync for date: {}", today);

        performFullSync(today);
    }

    public void triggerIncrementalSync() {
        LocalDate today = LocalDate.now();
        log.info("[manual] Triggering incremental attendance sync");

        // Ensure full sync done first
        ensureFullSyncDone(today);

        Set<String> employeesWithPunch = getEmployeesWithAnyPunchToday(today);
        Set<String> knownSynced = new HashSet<>(syncedTodayEmployeeIds);
        knownSynced.addAll(employeesWithPunch);

        List<LarkEmployee> allEmployees = employeeRepository.findAll();
        List<String> missingEmployeeIds = allEmployees.stream()
                .map(LarkEmployee::getId)
                .filter(id -> id != null && !id.isBlank())
                .filter(id -> !knownSynced.contains(id))
                .collect(Collectors.toList());

        if (missingEmployeeIds.isEmpty()) {
            log.info("[manual] All employees have been synced today");
            return;
        }

        AttendanceSyncRequest request = new AttendanceSyncRequest();
        request.setStartDate(today);
        request.setEndDate(today);
        request.setEmployeeIds(missingEmployeeIds);
        request.setForce(true);

        AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "manual_incremental", "system");
        log.info("[manual] Incremental sync result: success={}, synced={}, message={}",
                result.isSuccess(), result.getTotalSuccessEmployees(), result.getMessage());

        if (result.isSuccess()) {
            missingEmployeeIds.forEach(syncedTodayEmployeeIds::add);
        }
    }
}
