package mera.mera_v2.lark.sync.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttendanceSyncScheduler {

    private final LarkAttendanceSyncService attendanceSyncService;
    private final LarkEmployeeRepository employeeRepository;
    private final LarkAttendancePunchRepository attendancePunchRepository;

    private static final LocalTime FULL_SYNC_TIME = LocalTime.of(7, 59);
    private static final LocalTime SCHEDULE_START = LocalTime.of(7, 59);
    private static final LocalTime SCHEDULE_END = LocalTime.of(17, 0);

    @Scheduled(cron = "0 59 7 * * *", zone = "Asia/Ho_Chi_Minh")
    public void dailyFullSync() {
        if (!shouldRun()) {
            log.info("[scheduler] Skipping daily full sync - outside work hours");
            return;
        }

        LocalDate today = LocalDate.now();
        log.info("[scheduler] Starting daily full attendance sync for date: {}", today);

        try {
            AttendanceSyncRequest request = new AttendanceSyncRequest();
            request.setStartDate(today);
            request.setEndDate(today);
            request.setForce(true);

            AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "scheduler_daily", "system");

            log.info("[scheduler] Daily sync completed: success={}, employees={}, message={}",
                    result.isSuccess(), result.getTotalEmployees(), result.getMessage());

        } catch (Exception e) {
            log.error("[scheduler] Daily attendance sync failed: {}", e.getMessage(), e);
        }
    }

    @Scheduled(fixedRate = 60000)
    public void incrementalSyncMissingEmployees() {
        if (!shouldRun()) {
            return;
        }

        LocalTime now = LocalTime.now();
        if (now.isBefore(FULL_SYNC_TIME)) {
            return;
        }

        LocalDate today = LocalDate.now();

        try {
            Set<String> checkedInEmployeeIds = getCheckedInEmployeeIds(today);

            List<LarkEmployee> allEmployees = employeeRepository.findAll();
            List<String> allEmployeeIds = allEmployees.stream()
                    .map(LarkEmployee::getId)
                    .filter(id -> id != null && !id.isBlank())
                    .collect(Collectors.toList());

            List<String> missingEmployeeIds = allEmployeeIds.stream()
                    .filter(id -> !checkedInEmployeeIds.contains(id))
                    .collect(Collectors.toList());

            if (missingEmployeeIds.isEmpty()) {
                log.debug("[scheduler] All employees have checked in today");
                return;
            }

            log.info("[scheduler] Found {} employees not checked in yet: {}", 
                    missingEmployeeIds.size(), missingEmployeeIds);

            AttendanceSyncRequest request = new AttendanceSyncRequest();
            request.setStartDate(today);
            request.setEndDate(today);
            request.setEmployeeIds(missingEmployeeIds);
            request.setForce(true);

            AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "scheduler_incremental", "system");

            log.info("[scheduler] Incremental sync completed: success={}, synced={}, message={}",
                    result.isSuccess(), result.getTotalSuccessEmployees(), result.getMessage());

        } catch (Exception e) {
            log.error("[scheduler] Incremental attendance sync failed: {}", e.getMessage(), e);
        }
    }

    private Set<String> getCheckedInEmployeeIds(LocalDate date) {
        List<String> ids = attendancePunchRepository.findDistinctEmployeeIdsCheckedIn(date);
        return ids.stream().filter(id -> id != null).collect(Collectors.toSet());
    }

    private boolean shouldRun() {
        LocalTime now = LocalTime.now();
        return !now.isBefore(SCHEDULE_START) && !now.isAfter(SCHEDULE_END);
    }

    public void triggerManualFullSync() {
        LocalDate today = LocalDate.now();
        log.info("[manual] Triggering full attendance sync for date: {}", today);

        AttendanceSyncRequest request = new AttendanceSyncRequest();
        request.setStartDate(today);
        request.setEndDate(today);
        request.setForce(true);

        AttendanceSyncResult result = attendanceSyncService.syncAttendance(request, "manual", "system");
        log.info("[manual] Full sync result: success={}, message={}", result.isSuccess(), result.getMessage());
    }

    public void triggerIncrementalSync() {
        LocalDate today = LocalDate.now();
        log.info("[manual] Triggering incremental attendance sync");

        Set<String> checkedInEmployeeIds = getCheckedInEmployeeIds(today);

        List<LarkEmployee> allEmployees = employeeRepository.findAll();
        List<String> missingEmployeeIds = allEmployees.stream()
                .map(LarkEmployee::getId)
                .filter(id -> id != null && !id.isBlank())
                .filter(id -> !checkedInEmployeeIds.contains(id))
                .collect(Collectors.toList());

        if (missingEmployeeIds.isEmpty()) {
            log.info("[manual] All employees have checked in today");
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
    }
}
