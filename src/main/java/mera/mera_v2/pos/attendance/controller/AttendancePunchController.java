package mera.mera_v2.pos.attendance.controller;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkAttendancePunch;
import mera.mera_v2.lark.approval.AttendanceApprovalLookup;
import mera.mera_v2.lark.approval.AttendanceApprovalLookup.Day;
import mera.mera_v2.repository.LarkAttendanceDayRepository;
import mera.mera_v2.lark.sync.scheduler.AttendanceSyncScheduler;
import mera.mera_v2.lark.sync.service.AttendanceSyncResult;
import mera.mera_v2.repository.LarkAttendancePunchRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AttendancePunchController {
  private static final Logger log = LoggerFactory.getLogger(AttendancePunchController.class);

    private final LarkAttendancePunchRepository attendancePunchRepository;
    private final LarkAttendanceDayRepository attendanceDayRepository;
    private final AttendanceApprovalLookup approvalLookup;
    private final AttendanceSyncScheduler attendanceSyncScheduler;


    @GetMapping("/attendance-punch")
    public String attendancePunchPage(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String employeeName,
            @RequestParam(required = false) String department,
            Model model) {

        LocalDate effectiveStartDate = startDate != null ? startDate : LocalDate.now().minusDays(7);
        LocalDate effectiveEndDate = endDate != null ? endDate : LocalDate.now();

        List<LarkAttendancePunch> punches = attendancePunchRepository
                .findByAttendanceDateBetweenOrderByAttendanceDateDescEmployeeIdAscSyncedAtDesc(
                        effectiveStartDate, effectiveEndDate);

        if (employeeId != null && !employeeId.isBlank()) {
            punches = punches.stream()
                    .filter(p -> p.getEmployeeId() != null && p.getEmployeeId().contains(employeeId))
                    .collect(Collectors.toList());
        }

        if (employeeName != null && !employeeName.isBlank()) {
            punches = punches.stream()
                    .filter(p -> p.getEmployeeName() != null && p.getEmployeeName().toLowerCase().contains(employeeName.toLowerCase()))
                    .collect(Collectors.toList());
        }

        Map<String, List<LarkAttendancePunch>> groupedByEmployeeDate = punches.stream()
                .collect(Collectors.groupingBy(p -> {
                    String empId = p.getEmployeeId() != null ? p.getEmployeeId() : "";
                    LocalDate date = p.getAttendanceDate() != null ? p.getAttendanceDate() : LocalDate.now();
                    return empId + "###" + date.toString();
                }));

        List<Map<String, Object>> attendanceRows = new ArrayList<>();
        for (Map.Entry<String, List<LarkAttendancePunch>> entry : groupedByEmployeeDate.entrySet()) {
            List<LarkAttendancePunch> dayPunches = entry.getValue();
            if (dayPunches.isEmpty()) continue;

            LarkAttendancePunch first = dayPunches.get(0);

            dayPunches.sort(Comparator.comparing(
                    p -> p.getPunchTime() != null ? p.getPunchTime() : ""));

            String checkIn = dayPunches.stream()
                    .filter(p -> p.getPunchType() != null && p.getPunchType() == 1)
                    .filter(p -> p.getPunchTime() != null && !p.getPunchTime().isBlank())
                    .min(Comparator.comparing(LarkAttendancePunch::getPunchTime))
                    .map(LarkAttendancePunch::getPunchTime)
                    .orElse("-");

            String checkOut = dayPunches.stream()
                    .filter(p -> p.getPunchType() != null && p.getPunchType() == 2)
                    .filter(p -> p.getPunchTime() != null && !p.getPunchTime().isBlank())
                    .max(Comparator.comparing(LarkAttendancePunch::getPunchTime))
                    .map(LarkAttendancePunch::getPunchTime)
                    .orElse("-");

            Map<String, Object> row = new HashMap<>();
            row.put("employeeName", first.getEmployeeName() != null ? first.getEmployeeName() : "N/A");
            row.put("employeeId", first.getEmployeeId());
            row.put("department", first.getAttendanceGroupName() != null ? first.getAttendanceGroupName() : "-");
            row.put("checkIn", checkIn);
            row.put("checkOut", checkOut);
            row.put("date", first.getAttendanceDate());
            row.put("weekday", first.getWeekday() != null ? first.getWeekday() : "");
            row.put("punchCount", dayPunches.size());
            row.put("punches", dayPunches);

            attendanceRows.add(row);
        }

        for (var day : attendanceDayRepository.findByAttendanceDateBetweenAndRequiredHoursGreaterThan(
                effectiveStartDate, effectiveEndDate, java.math.BigDecimal.ZERO)) {
            String id = day.getEmployeeId();
            String name = Objects.toString(day.getEmployeeName(), "");
            if (groupedByEmployeeDate.containsKey(id + "###" + day.getAttendanceDate())
                    || (employeeId != null && !employeeId.isBlank() && !id.contains(employeeId))
                    || (employeeName != null && !employeeName.isBlank()
                    && !name.toLowerCase(Locale.ROOT).contains(employeeName.toLowerCase(Locale.ROOT)))) {
                continue;
            }
            Map<String, Object> row = new HashMap<>();
            row.put("employeeName", name);
            row.put("employeeId", id);
            row.put("department", Objects.toString(day.getAttendanceGroupName(), "-"));
            row.put("checkIn", "-");
            row.put("checkOut", "-");
            row.put("date", day.getAttendanceDate());
            row.put("punchCount", 0);
            attendanceRows.add(row);
        }

        var missingDays = new HashSet<Day>();
        for (var row : attendanceRows) {
            if ("-".equals(row.get("checkIn")) || "-".equals(row.get("checkOut"))) {
                missingDays.add(new Day((String) row.get("employeeId"), (LocalDate) row.get("date")));
            }
        }
        var approvals = approvalLookup.find(missingDays);
        for (var row : attendanceRows) {
            boolean missingIn = "-".equals(row.get("checkIn"));
            boolean missingOut = "-".equals(row.get("checkOut"));
            row.put("approvalNeeded", missingIn || missingOut);
            if (approvals != null) {
                var day = new Day((String) row.get("employeeId"), (LocalDate) row.get("date"));
                row.put("approvalMatches", approvals.getOrDefault(day, List.of()).stream()
                        .filter(match -> (missingIn && match.checkIn()) || (missingOut && match.checkOut()))
                        .toList());
            }
        }
        model.addAttribute("approvalLookupAvailable", approvals != null);

        attendanceRows.sort(Comparator.comparing(
                (Map<String, Object> r) -> (LocalDate) r.get("date"),
                Comparator.nullsLast(Comparator.reverseOrder())));

        model.addAttribute("attendanceRows", attendanceRows);
        model.addAttribute("startDate", effectiveStartDate);
        model.addAttribute("endDate", effectiveEndDate);
        model.addAttribute("employeeId", employeeId);
        model.addAttribute("employeeName", employeeName);
        model.addAttribute("department", department);
        model.addAttribute("totalRecords", attendanceRows.size());
        model.addAttribute("totalEmployees", attendanceRows.stream()
                .map(r -> (String) r.get("employeeId"))
                .filter(Objects::nonNull)
                .distinct()
                .count());

        return "attendance-punch";
    }

    @GetMapping("/api/attendance-punch/data")
    @ResponseBody
    public Map<String, Object> getAttendanceData(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String employeeId,
            @RequestParam(required = false) String employeeName) {

        List<LarkAttendancePunch> punches = attendancePunchRepository
                .findByAttendanceDateBetweenOrderByAttendanceDateDescEmployeeIdAscSyncedAtDesc(
                        startDate, endDate);

        if (employeeId != null && !employeeId.isBlank()) {
            punches = punches.stream()
                    .filter(p -> p.getEmployeeId() != null && p.getEmployeeId().contains(employeeId))
                    .collect(Collectors.toList());
        }

        if (employeeName != null && !employeeName.isBlank()) {
            punches = punches.stream()
                    .filter(p -> p.getEmployeeName() != null && p.getEmployeeName().toLowerCase().contains(employeeName.toLowerCase()))
                    .collect(Collectors.toList());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("punches", punches);
        result.put("totalRecords", punches.size());
        result.put("totalEmployees", punches.stream()
                .map(LarkAttendancePunch::getEmployeeId)
                .filter(Objects::nonNull)
                .distinct()
                .count());

        return result;
    }

    @PostMapping("/api/attendance-sync/full")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerFullSync() {
        log.info("[API] Manual full attendance sync triggered");

        try {
            attendanceSyncScheduler.triggerManualFullSync();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Full sync started");
            response.put("time", LocalDate.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] Full sync failed: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Sync failed: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }

    @PostMapping("/api/attendance-sync/incremental")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> triggerIncrementalSync() {
        log.info("[API] Manual incremental attendance sync triggered");

        try {
            attendanceSyncScheduler.triggerIncrementalSync();

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Incremental sync started");
            response.put("time", LocalDate.now().toString());

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[API] Incremental sync failed: {}", e.getMessage(), e);

            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Sync failed: " + e.getMessage());

            return ResponseEntity.internalServerError().body(response);
        }
    }
}
