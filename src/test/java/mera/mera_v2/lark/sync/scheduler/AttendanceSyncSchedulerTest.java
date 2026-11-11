package mera.mera_v2.lark.sync.scheduler;

import java.time.*;
import java.util.List;
import mera.mera_v2.config.SyncFeatureToggleService;
import mera.mera_v2.lark.sync.dto.AttendanceSyncRequest;
import mera.mera_v2.lark.sync.service.AttendanceSyncResult;
import mera.mera_v2.lark.sync.service.LarkAttendanceSyncService;
import mera.mera_v2.repository.LarkAttendancePunchRepository;
import mera.mera_v2.repository.LarkEmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AttendanceSyncSchedulerTest {
    private final LarkAttendanceSyncService sync = mock(LarkAttendanceSyncService.class);
    private final LarkEmployeeRepository employees = mock(LarkEmployeeRepository.class);
    private final LarkAttendancePunchRepository punches = mock(LarkAttendancePunchRepository.class);
    private final SyncFeatureToggleService toggle = mock(SyncFeatureToggleService.class);
    private final AttendanceSyncScheduler scheduler = new AttendanceSyncScheduler(sync, employees, punches, toggle);

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(scheduler, "jobsEnabled", true);
        when(toggle.isAttendanceSyncEnabled()).thenReturn(true);
        when(employees.findAll()).thenReturn(List.of());
        when(punches.findEmployeeIdsMissingCheckin(any())).thenReturn(List.of());
        when(sync.syncAttendance(any(), anyString(), anyString()))
                .thenReturn(AttendanceSyncResult.builder().success(true).status("success").build());
    }

    private void time(String instant) {
        ReflectionTestUtils.setField(scheduler, "clock",
                Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Ho_Chi_Minh")));
    }

    @Test
    void skippedEarlyCronIsRecoveredByFirstMinuteTickWithEmptyDatabase() {
        time("2026-11-10T00:58:58Z");
        scheduler.dailyFullSync();
        verifyNoInteractions(sync);
        time("2026-11-10T00:59:04Z");
        scheduler.incrementalSyncMissingEmployees();
        var request = ArgumentCaptor.forClass(AttendanceSyncRequest.class);
        verify(sync).syncAttendance(request.capture(), eq("scheduler_daily"), eq("system"));
        assertEquals(LocalDate.of(2026, 11, 9), request.getValue().getStartDate()); // keo ca check-out hom truoc
        assertEquals(LocalDate.of(2026, 11, 10), request.getValue().getEndDate());
        verifyNoInteractions(punches);
        scheduler.incrementalSyncMissingEmployees();
        verify(sync, times(1)).syncAttendance(any(), anyString(), anyString());
        verify(punches).findEmployeeIdsMissingCheckin(LocalDate.of(2026, 11, 10));
    }

    @Test
    void partialSyncRetriesUntilSuccessful() {
        time("2026-11-10T01:21:00Z");
        when(sync.syncAttendance(any(), anyString(), anyString()))
                .thenReturn(AttendanceSyncResult.builder().success(true).status("partial_success").build())
                .thenReturn(AttendanceSyncResult.builder().success(true).status("success").build());
        scheduler.incrementalSyncMissingEmployees();
        scheduler.incrementalSyncMissingEmployees();
        scheduler.incrementalSyncMissingEmployees();
        verify(sync, times(2)).syncAttendance(any(), eq("scheduler_daily"), eq("system"));
    }

    @Test
    void disabledToggleAndOffHoursDoNotSync() {
        time("2026-11-10T01:00:00Z");
        when(toggle.isAttendanceSyncEnabled()).thenReturn(false);
        scheduler.incrementalSyncMissingEmployees();
        when(toggle.isAttendanceSyncEnabled()).thenReturn(true);
        time("2026-11-10T11:00:00Z");
        scheduler.incrementalSyncMissingEmployees();
        verifyNoInteractions(sync);
    }

    @Test
    void newDayStartsNewFullSyncInVietnamTimezone() {
        time("2026-11-10T01:00:00Z");
        scheduler.incrementalSyncMissingEmployees();
        time("2026-11-11T01:00:00Z");
        scheduler.incrementalSyncMissingEmployees();
        var request = ArgumentCaptor.forClass(AttendanceSyncRequest.class);
        verify(sync, times(2)).syncAttendance(request.capture(), eq("scheduler_daily"), eq("system"));
        assertEquals(LocalDate.of(2026, 11, 10), request.getValue().getStartDate());
        assertEquals(LocalDate.of(2026, 11, 11), request.getValue().getEndDate());
    }

    @Test
    void exceptionDoesNotMarkDayComplete() {
        time("2026-11-10T01:00:00Z");
        when(sync.syncAttendance(any(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("API unavailable"));
        scheduler.incrementalSyncMissingEmployees();
        scheduler.incrementalSyncMissingEmployees();
        verify(sync, times(2)).syncAttendance(any(), eq("scheduler_daily"), eq("system"));
    }
}
