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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.ZoneId;
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

    private volatile LocalDate lastFullSyncDate;
    private Clock clock = Clock.system(ZoneId.of("Asia/Ho_Chi_Minh"));
    private final Set<String> syncedTodayEmployeeIds = Collections.synchronizedSet(new HashSet<>());
    private final Object syncLock = new Object();

    /** Chỉ một instance chạy job nền — xem ProblemCustomerFactsService#jobsEnabled. */
    @Value("${mera.jobs.enabled:true}")
    private boolean jobsEnabled;

    @Scheduled(cron = "0 59 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void dailyFullSync() {
        if (!jobsEnabled) {
            return;
        }
        if (!featureToggle.isAttendanceSyncEnabled()) {
            log.info("[scheduler] Skipping daily full sync - feature DISABLED via toggle");
            return;
        }
        if (!shouldRun()) {
            log.info("[scheduler] Skipping daily full sync - outside work hours");
            return;
        }

        LocalDate today = LocalDate.now(clock);
        log.info("[scheduler] Starting daily full attendance sync for date: {}", today);

        ensureFullSyncDone(today);
    }

    @Scheduled(fixedRate = 60000)
    public void incrementalSyncMissingEmployees() {
        if (!jobsEnabled) {
            return;
        }
        if (!featureToggle.isAttendanceSyncEnabled()) {
            log.debug("[scheduler] Skipping incremental sync - feature DISABLED via toggle");
            return;
        }
        LocalTime now = LocalTime.now(clock);
        log.info("[scheduler] Incremental sync triggered at {}", now);

        if (!shouldRun()) {
            log.debug("[scheduler] Outside work hours, skipping");
            return;
        }

        if (now.isBefore(FULL_SYNC_TIME)) {
            log.debug("[scheduler] Before full sync time (7:59), skipping");
            return;
        }

        LocalDate today = LocalDate.now(clock);

        try {
            // Catch up after a missed cron, restart, or enabling auto-sync during the day.
            if (!today.equals(lastFullSyncDate)) {
                ensureFullSyncDone(today);
                return;
            }
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
        synchronized (syncLock) {
            if (today.equals(lastFullSyncDate)) return;
            log.info("[scheduler] Full sync not completed for {}; starting catch-up", today);
            performFullSync(today);
        }
    }

    private void performFullSync(LocalDate today) {
        synchronized (syncLock) {
            // Reset synced list for new day
            lastFullSyncDate = null;
            syncedTodayEmployeeIds.clear();

            try {
                AttendanceSyncRequest request = new AttendanceSyncRequest();
                // Lay ca hom qua: job chi chay 7:59-17:00 nen check-out sau 17h hom truoc chua bao gio duoc keo ve
                request.setStartDate(today.minusDays(1));
                request.setEndDate(today);
                request.setForce(true);

                AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "scheduler_daily", "system");

                log.info("[scheduler] Daily sync completed: success={}, employees={}, message={}",
                        result.isSuccess(), result.getTotalEmployees(), result.getMessage());

                if (result.isSuccess() && "success".equals(result.getStatus())) {
                    markAllEmployeesAsSynced();
                    lastFullSyncDate = today;
                } else {
                    log.warn("[scheduler] Full sync for {} remains incomplete; next tick will retry", today);
                }

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
        LocalTime now = LocalTime.now(clock);
        return !now.isBefore(SCHEDULE_START) && !now.isAfter(SCHEDULE_END);
    }

    public void triggerManualFullSync() {
        LocalDate today = LocalDate.now(clock);
        log.info("[manual] Triggering full attendance sync for date: {}", today);

        performFullSync(today);
    }

    public void triggerIncrementalSync() {
        LocalDate today = LocalDate.now(clock);
        log.info("[manual] Triggering incremental attendance sync");

        // Ensure full sync done first
        ensureFullSyncDone(today);
        if (!today.equals(lastFullSyncDate)) return;

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
