package mera.mera_v2.lark.sync.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkAttendanceDay;
import mera.mera_v2.entity.LarkAttendancePunch;
import mera.mera_v2.entity.LarkEmployee;
import mera.mera_v2.entity.LarkSyncJob;
import mera.mera_v2.lark.sync.client.LarkAttendanceClient;
import mera.mera_v2.lark.sync.client.LarkAttendanceClient.AttendanceDataItem;
import mera.mera_v2.lark.sync.client.LarkAttendanceClient.AttendanceStatsResponse;
import mera.mera_v2.lark.sync.client.LarkAttendanceClient.UserAttendanceData;
import mera.mera_v2.lark.sync.dto.AttendanceSyncRequest;
import mera.mera_v2.lark.webhook.service.TenantTokenService;
import mera.mera_v2.repository.LarkAttendanceDayRepository;
import mera.mera_v2.repository.LarkAttendancePunchRepository;
import mera.mera_v2.repository.LarkEmployeeRepository;
import mera.mera_v2.repository.LarkSyncJobRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class LarkAttendanceSyncService {

    private final LarkAttendanceClient attendanceClient;
    private final LarkEmployeeRepository employeeRepository;
    private final LarkAttendanceDayRepository attendanceDayRepository;
    private final LarkAttendancePunchRepository attendancePunchRepository;
    private final LarkSyncJobRepository syncJobRepository;
    private final TenantTokenService tenantTokenService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

    public AttendanceSyncResult syncAttendance(AttendanceSyncRequest request, String triggerSource, String createdBy) {
        log.info("[lark-attendance] Starting sync: startDate={}, endDate={}, triggerSource={}",
                request.getStartDate(), request.getEndDate(), triggerSource);

        validateRequest(request);

        String lockKey = buildLockKey(request);
        if (!request.isForce() && isJobRunning(lockKey)) {
            return AttendanceSyncResult.builder()
                    .success(false)
                    .message("Job is already running for this scope")
                    .build();
        }

        List<LarkEmployee> employees = resolveEmployees(request.getEmployeeIds(), request.getDepartmentIds());
        List<String> employeeIds = employees.stream()
                .map(LarkEmployee::getId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toList());

        if (employeeIds.isEmpty()) {
            return AttendanceSyncResult.builder()
                    .success(false)
                    .message("No employees found")
                    .build();
        }

        String jobId = UUID.randomUUID().toString();
        createJob(jobId, request, triggerSource, createdBy, lockKey, employeeIds.size());

        AtomicInteger totalSuccess = new AtomicInteger(0);
        AtomicInteger totalFailed = new AtomicInteger(0);
        AtomicInteger totalRequests = new AtomicInteger(0);
        Set<String> invalidUsers = Collections.synchronizedSet(new HashSet<>());
        int invalidEmployeeCount = 0;

        List<int[]> dateRanges = splitDateRange(request.getStartDate(), request.getEndDate());
        List<List<String>> userChunks = splitUserChunks(employeeIds);

        for (int[] dateRange : dateRanges) {
            LocalDate rangeStart = LocalDate.ofEpochDay(dateRange[0]);
            LocalDate rangeEnd = LocalDate.ofEpochDay(dateRange[1]);
            int startDateInt = Integer.parseInt(rangeStart.format(DATE_FORMAT));
            int endDateInt = Integer.parseInt(rangeEnd.format(DATE_FORMAT));

            for (List<String> userChunk : userChunks) {
                totalRequests.incrementAndGet();
                try {
                    AttendanceStatsResponse data = attendanceClient.queryUserStatsData(
                            userChunk, startDateInt, endDateInt, getTenantToken());

                    invalidEmployeeCount += processResponse(data, rangeStart, invalidUsers);

                    int successCount = userChunk.size() - invalidUsers.size();
                    totalSuccess.addAndGet(Math.max(0, successCount));

                } catch (Exception e) {
                    totalFailed.incrementAndGet();
                    log.error("[lark-attendance] Chunk failed: {}", e.getMessage());
                }
            }
        }

        String finalStatus = determineJobStatus(totalFailed.get(), totalRequests.get());
        finishJob(jobId, finalStatus, totalSuccess.get(), invalidEmployeeCount, totalFailed.get(), totalRequests.get());

        log.info("[lark-attendance] Job {} finished: status={}, success={}, failed={}",
                jobId, finalStatus, totalSuccess.get(), totalFailed.get());

        return AttendanceSyncResult.builder()
                .success(true)
                .jobId(jobId)
                .status(finalStatus)
                .totalEmployees(employeeIds.size())
                .totalRequests(totalRequests.get())
                .totalSuccessEmployees(totalSuccess.get())
                .totalInvalidEmployees(invalidEmployeeCount)
                .totalFailedRequests(totalFailed.get())
                .message("Attendance sync completed with status: " + finalStatus)
                .build();
    }

    private void validateRequest(AttendanceSyncRequest request) {
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new IllegalArgumentException("startDate and endDate are required");
        }
        if (request.getStartDate().isAfter(request.getEndDate())) {
            throw new IllegalArgumentException("startDate must be before or equal to endDate");
        }
        if (request.getStartDate().plusDays(31).isBefore(request.getEndDate())) {
            throw new IllegalArgumentException("Date range must not exceed 31 days");
        }
    }

    private String buildLockKey(AttendanceSyncRequest request) {
        String scope;
        if (request.getEmployeeIds() != null && !request.getEmployeeIds().isEmpty()) {
            scope = "emp:" + request.getEmployeeIds().stream()
                    .sorted()
                    .limit(5)
                    .collect(Collectors.joining(","));
        } else if (request.getDepartmentIds() != null && !request.getDepartmentIds().isEmpty()) {
            scope = "dept:" + request.getDepartmentIds().stream()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        } else {
            scope = "all";
        }
        return String.format("attendance:%s:%s:%s",
                request.getStartDate(), request.getEndDate(), scope);
    }

    private boolean isJobRunning(String lockKey) {
        return syncJobRepository.findFirstByLockedKeyAndStatusOrderByCreatedAtDesc(lockKey, "running").isPresent();
    }

    private List<LarkEmployee> resolveEmployees(List<String> employeeIds, List<Long> departmentIds) {
        if (employeeIds != null && !employeeIds.isEmpty()) {
            return employeeRepository.findAllById(employeeIds);
        }
        return employeeRepository.findAll();
    }

    private String getTenantToken() {
        return tenantTokenService.getTenantAccessToken();
    }

    private void createJob(String jobId, AttendanceSyncRequest request, String triggerSource,
                             String createdBy, String lockKey, int totalEmployees) {
        LarkSyncJob job = new LarkSyncJob();
        job.setId(jobId);
        job.setJobType("attendance_daily_sync");
        job.setTriggerSource(triggerSource);
        job.setStatus("running");
        job.setStartDate(request.getStartDate());
        job.setEndDate(request.getEndDate());
        job.setLockedKey(lockKey);
        job.setCreatedBy(createdBy);
        job.setStartedAt(LocalDateTime.now());
        job.setCreatedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        job.setTotalEmployees(totalEmployees);
        job.setTotalRequests(0);
        job.setTotalSuccessEmployees(0);
        job.setTotalInvalidEmployees(0);
        job.setTotalFailedRequests(0);
        syncJobRepository.save(job);
    }

    private List<int[]> splitDateRange(LocalDate startDate, LocalDate endDate) {
        List<int[]> ranges = new ArrayList<>();
        LocalDate current = startDate;

        while (!current.isAfter(endDate)) {
            LocalDate rangeEnd = current.plusDays(30);
            if (rangeEnd.isAfter(endDate)) {
                rangeEnd = endDate;
            }

            ranges.add(new int[]{(int) current.toEpochDay(), (int) rangeEnd.toEpochDay()});
            current = rangeEnd.plusDays(1);
        }
        return ranges;
    }

    private List<List<String>> splitUserChunks(List<String> employeeIds) {
        List<List<String>> chunks = new ArrayList<>();
        for (int i = 0; i < employeeIds.size(); i += 20) {
            int end = Math.min(i + 20, employeeIds.size());
            chunks.add(employeeIds.subList(i, end));
        }
        return chunks;
    }

    @Transactional
    public int processResponse(AttendanceStatsResponse data, LocalDate fallbackDate, Set<String> invalidUsers) {
        int invalidCount = 0;

        List<String> invalidUserList = data.getInvalidUserList();
        if (invalidUserList != null) {
            for (String userId : invalidUserList) {
                if (userId != null) {
                    invalidUsers.add(userId);
                    invalidCount++;
                }
            }
        }

        List<UserAttendanceData> userDatas = data.getUserDatas();
        if (userDatas != null) {
            for (UserAttendanceData userData : userDatas) {
                if (userData == null) continue;
                try {
                    processUserData(userData, fallbackDate);
                } catch (Exception e) {
                    log.error("[lark-attendance] Error processing user {}: {}", 
                            userData != null ? userData.getUserId() : "null", e.getMessage(), e);
                }
            }
        }

        return invalidCount;
    }

    @Transactional
    public void processUserData(UserAttendanceData userData, LocalDate fallbackDate) {
        String userId = userData.getUserId();
        List<AttendanceDataItem> datas = userData.getDatas();
        if (datas == null || datas.isEmpty()) {
            log.warn("[lark-attendance] No data items for user {}", userId);
            return;
        }

        Map<String, AttendanceDataItem> dataMap = new HashMap<>();
        for (AttendanceDataItem item : datas) {
            String code = item.getCode();
            if (code != null) {
                dataMap.put(code, item);
            }
        }

        String attendanceDateStr = getValueFromMap(dataMap, "51201");
        LocalDate attendanceDate = parseDate(attendanceDateStr);
        if (attendanceDate == null) {
            attendanceDate = fallbackDate;
        }

        upsertAttendanceDay(userData, dataMap, userId, attendanceDate);

        List<String> currentCodes = new ArrayList<>();
        for (AttendanceDataItem item : datas) {
            String code = item.getCode();
            if (code != null && code.startsWith("51503-")) {
                upsertAttendancePunch(userData, item, userId, attendanceDate);
                currentCodes.add(code);
            }
        }

        if (!currentCodes.isEmpty()) {
            attendancePunchRepository.deleteStalePunches(userId, attendanceDate, currentCodes);
        }

        attendanceDayRepository.computeWorkHours(userId, attendanceDate);
    }

    private void upsertAttendanceDay(UserAttendanceData userData, Map<String, AttendanceDataItem> dataMap,
                                     String userId, LocalDate attendanceDate) {
        String employeeName = getValueFromMap(dataMap, "50101");
        if (employeeName == null) {
            employeeName = userData.getName();
        }

        Optional<LarkAttendanceDay> existing = attendanceDayRepository.findByEmployeeIdAndAttendanceDate(userId, attendanceDate);

        LarkAttendanceDay day = existing.orElse(new LarkAttendanceDay());
        if (existing.isEmpty()) {
            day.setId(UUID.randomUUID().toString());
            day.setCreatedAt(LocalDateTime.now());
        }

        day.setEmployeeId(userId);
        day.setAttendanceDate(attendanceDate);
        day.setEmployeeName(nullIfEmpty(employeeName));
        day.setEmployeeNo(nullIfEmpty(getValueFromMap(dataMap, "50103")));
        day.setDepartmentName(nullIfEmpty(getValueFromMap(dataMap, "50102")));
        day.setAttendanceGroupName(nullIfEmpty(getValueFromMap(dataMap, "51203")));
        day.setShiftName(nullIfEmpty(getValueFromMap(dataMap, "51202")));
        day.setTimezone(nullIfEmpty(getValueFromMap(dataMap, "51204")));
        day.setWeekday(nullIfEmpty(getValueFromMap(dataMap, "weekday")));
        day.setRequiredHours(parseDouble(getValueFromMap(dataMap, "51302")));
        day.setActualHours(parseDouble(getValueFromMap(dataMap, "51303")));
        day.setLeaveHours(parseDouble(getValueFromMap(dataMap, "51401")));
        day.setOvertimeHours(parseDouble(getValueFromMap(dataMap, "51307")));
        day.setResignedDate(parseDate(getValueFromMap(dataMap, "9")));
        day.setSyncedAt(LocalDateTime.now());
        day.setUpdatedAt(LocalDateTime.now());

        try {
            day.setRawData(objectMapper.writeValueAsString(userData));
        } catch (Exception e) {
            day.setRawData(null);
        }

        attendanceDayRepository.save(day);
        attendanceDayRepository.flush();
    }

    private void upsertAttendancePunch(UserAttendanceData userData, AttendanceDataItem item,
                                       String userId, LocalDate attendanceDate) {
        Map<String, String> features = item.getFeatures();
        if (features == null) features = new HashMap<>();

        String code = item.getCode();
        String id = userId + "_" + attendanceDate + "_" + code;
        LocalDateTime now = LocalDateTime.now();

        String rawFeatures = null;
        String rawItem = null;
        try {
            rawFeatures = objectMapper.writeValueAsString(features);
            rawItem = objectMapper.writeValueAsString(item);
        } catch (Exception e) {
            // ignore
        }

        attendancePunchRepository.upsertPunch(
                id,
                userId,
                nullIfEmpty(userData.getName()),
                attendanceDate,
                code,
                nullIfEmpty(item.getTitle()),
                nullIfEmpty(features.get("GroupName")),
                nullIfEmpty(features.get("Weekday")),
                parseInteger(features.get("PunchType")),
                parseInteger(features.get("PunchNo")),
                normalizeTime(features.get("ShiftTime")),
                normalizeTime(features.get("PunchTime")),
                nullIfEmpty(features.get("PunchStatus")),
                nullIfEmpty(features.get("PunchSubStatus")),
                nullIfEmpty(features.get("StatusMsg")),
                nullIfEmpty(features.get("PunchLocName")),
                nullIfEmpty(features.get("TaskId")),
                nullIfEmpty(features.get("FlowId")),
                nullIfEmpty(features.get("Note")),
                rawFeatures,
                rawItem,
                now,
                now,
                now
        );
    }

    private String getValueFromMap(Map<String, AttendanceDataItem> map, String code) {
        AttendanceDataItem item = map.get(code);
        if (item == null) return null;
        String value = item.getValue();
        if (value == null || value.isEmpty() || value.equals("-")) return null;
        return value;
    }

    private LocalDate parseDate(String dateStr) {
        if (dateStr == null || !dateStr.matches("\\d{8}")) return null;
        return LocalDate.parse(
                dateStr.substring(0, 4) + "-" +
                        dateStr.substring(4, 6) + "-" +
                        dateStr.substring(6, 8)
        );
    }

    private String normalizeTime(String time) {
        if (time == null || time.isEmpty() || time.equals("-")) return null;
        if (time.matches("\\d{2}:\\d{2}")) return time + ":00";
        if (time.matches("\\d{2}:\\d{2}:\\d{2}")) return time;
        return null;
    }

    private BigDecimal parseDouble(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Integer parseInteger(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String nullIfEmpty(String value) {
        if (value == null || value.trim().isEmpty()) return null;
        return value;
    }

    private String determineJobStatus(int totalFailed, int totalRequests) {
        if (totalFailed == 0) return "success";
        if (totalFailed < totalRequests) return "partial_success";
        return "failed";
    }

    private void finishJob(String jobId, String status, int totalSuccess, int invalidCount,
                           int totalFailed, int totalRequests) {
        syncJobRepository.findById(jobId).ifPresent(job -> {
            job.setStatus(status);
            job.setTotalSuccessEmployees(totalSuccess);
            job.setTotalInvalidEmployees(invalidCount);
            job.setTotalFailedRequests(totalFailed);
            job.setTotalRequests(totalRequests);
            job.setFinishedAt(LocalDateTime.now());
            job.setUpdatedAt(LocalDateTime.now());
            syncJobRepository.save(job);
        });
    }
}
