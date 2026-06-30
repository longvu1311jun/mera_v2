package mera.mera_v2.pos.attendance.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.LarkAttendancePunch;
import mera.mera_v2.repository.LarkAttendancePunchRepository;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequiredArgsConstructor
@Slf4j
public class AttendancePunchController {

    private final LarkAttendancePunchRepository attendancePunchRepository;

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

        // Group by employee + date using composite key
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
            
            // Sort by punch time
            dayPunches.sort(Comparator.comparing(
                    p -> p.getPunchTime() != null ? p.getPunchTime() : ""));
            
            String checkIn = dayPunches.stream()
                    .filter(p -> p.getPunchType() != null && p.getPunchType() == 1)
                    .filter(p -> p.getPunchTime() != null && !p.getPunchTime().isEmpty())
                    .min(Comparator.comparing(LarkAttendancePunch::getPunchTime))
                    .map(LarkAttendancePunch::getPunchTime)
                    .orElse("-");
            
            String checkOut = dayPunches.stream()
                    .filter(p -> p.getPunchType() != null && p.getPunchType() == 2)
                    .filter(p -> p.getPunchTime() != null && !p.getPunchTime().isEmpty())
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

        // Sort by date desc
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
}
