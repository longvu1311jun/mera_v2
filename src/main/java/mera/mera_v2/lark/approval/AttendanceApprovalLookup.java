package mera.mera_v2.lark.approval;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.format.DateTimeParseException;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AttendanceApprovalLookup {
    private final JdbcTemplate jdbc;
    private final ApprovalFormDisplay forms;

    public record Day(String employeeId, LocalDate date) {}
    public record Match(String code, String url, String status, boolean checkIn, boolean checkOut) {
        public String statusLabel() {
            return switch (status) {
                case "APPROVED" -> "Đã duyệt";
                case "PENDING" -> "Đang chờ";
                case "REJECTED" -> "Từ chối";
                case "CANCELED" -> "Đã hủy";
                case "DELETED" -> "Đã xóa";
                default -> status;
            };
        }
    }

    public Map<Day, List<Match>> find(Set<Day> days) {
        var matches = new HashMap<Day, List<Match>>();
        if (days.isEmpty()) return matches;
        var byEmployee = new HashMap<String, Set<LocalDate>>();
        for (Day day : days) byEmployee.computeIfAbsent(day.employeeId(), id -> new HashSet<>()).add(day.date());
        var byOpenId = new HashMap<String, String>();
        jdbc.query("SELECT id, open_id FROM lark_employees WHERE open_id IS NOT NULL", rs -> {
            if (byEmployee.containsKey(rs.getString("id"))) byOpenId.put(rs.getString("open_id"), rs.getString("id"));
        });
        try {
            jdbc.query("""
                    SELECT COALESCE(i.serial_number,i.instance_code) AS code, i.instance_code, i.status, i.form_json, d.approval_name
                    FROM lark_approval_instances i JOIN lark_approval_definitions d
                      ON d.id=i.definition_id AND d.tenant_key=i.tenant_key
                    WHERE i.tenant_key=? AND i.form_json IS NOT NULL
                      AND (d.approval_name LIKE 'Đơn xin nghỉ làm không lương / phép%'
                        OR d.approval_name LIKE 'Đơn xin quên chấm công%'
                        OR d.approval_name LIKE 'Đơn xin về sớm/ đi muộn%')
                    ORDER BY i.submitted_at DESC, i.id DESC
                    """, rs -> {
                JsonNode form = forms.parse(rs.getString("form_json"));
                Set<String> employees = new HashSet<>();
                LocalDate start = null;
                LocalDate end = null;
                boolean in = false;
                boolean out = false;
                for (JsonNode field : form) {
                    JsonNode value = field.path("value");
                    switch (field.path("type").asText()) {
                        case "contact" -> {
                            for (JsonNode id : value) {
                                if (byEmployee.containsKey(id.asText())) employees.add(id.asText());
                            }
                            for (JsonNode id : field.path("open_ids")) {
                                String employee = byOpenId.get(id.asText());
                                if (employee != null) employees.add(employee);
                            }
                        }
                        case "dateInterval" -> {
                            start = localDate(value.path("start").asText());
                            end = localDate(value.path("end").asText());
                            in = out = true;
                        }
                        case "date" -> {
                            LocalDate date = localDate(value.asText());
                            if (date != null) {
                                if (start == null || date.isBefore(start)) start = date;
                                if (end == null || date.isAfter(end)) end = date;
                            }
                        }
                        case "checkboxV2" -> {
                            for (JsonNode choice : value) {
                                switch (choice.asText()) {
                                    case "Quên chấm công sáng", "Xin đi muộn" -> in = true;
                                    case "Quên chấm công chiều", "Xin về sớm" -> out = true;
                                }
                            }
                        }
                    }
                }
                if (start == null || end == null || (!in && !out)) return;
                var match = new Match(rs.getString("code"), ApprovalAppLink.detail(rs.getString("instance_code")),
                        rs.getString("status"), in, out);
                for (String employee : employees) {
                    for (LocalDate day : byEmployee.get(employee)) {
                        if (!day.isBefore(start) && !day.isAfter(end)) {
                            matches.computeIfAbsent(new Day(employee, day), key -> new ArrayList<>()).add(match);
                        }
                    }
                }
            }, ApprovalSyncService.TENANT);
        } catch (BadSqlGrammarException ex) {
            // Older installations may not have the optional Approval migration yet.
            if (ex.getSQLException() == null || ex.getSQLException().getErrorCode() != 1146) throw ex;
            return null;
        }
        return matches;
    }

    private static LocalDate localDate(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ApprovalSyncService.LOCAL_ZONE).toLocalDate();
        } catch (DateTimeParseException ex) {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException ignored) {
                return null;
            }
        }
    }
}
