package mera.mera_v2.lark.approval;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class ApprovalListController {
    private static final int PAGE_SIZE = 50;
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ApprovalSyncService approvals;
    private final JdbcTemplate jdbc;

    private final ApprovalFormDisplay forms;

    private record StoredRow(String number, String template, String status, String createdAt,
                             String requester, String requesterOpenId, JsonNode form, String url) {}

    public record ApprovalRow(String number, String template, String status, String createdAt,
                              String employee, String requester, String leavePeriod,
                              List<ApprovalFormDisplay.Field> fields, String url) {}

    @GetMapping("/lark-approvals")
    public String list(@RequestParam(required = false) Long templateId,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                       @RequestParam(defaultValue = "1") int page, Model model) {
        if (page < 1 || (from != null && to != null && from.isAfter(to))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid approval filters");
        }
        var templates = approvals.templates();
        if (templateId != null && templates.stream().noneMatch(template -> template.id() == templateId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Approval template not found");
        }

        StringBuilder where = new StringBuilder(" WHERE i.tenant_key = ? AND d.tenant_key = i.tenant_key");
        List<Object> args = new ArrayList<>();
        args.add(ApprovalSyncService.TENANT);
        if (templateId != null) {
            where.append(" AND i.definition_id = ?");
            args.add(templateId);
        }
        if (from != null) {
            where.append(" AND i.submitted_at >= ?");
            args.add(utcStart(from));
        }
        if (to != null) {
            where.append(" AND i.submitted_at < ?");
            args.add(utcStart(to.plusDays(1)));
        }
        String source = " FROM lark_approval_instances i JOIN lark_approval_definitions d ON d.id = i.definition_id";
        long count = jdbc.queryForObject("SELECT COUNT(*)" + source + where, Long.class, args.toArray());
        long totalPages = Math.max(1, (count + PAGE_SIZE - 1) / PAGE_SIZE);
        long currentPage = Math.min(page, totalPages);
        args.add(PAGE_SIZE);
        args.add((currentPage - 1) * PAGE_SIZE);
        var stored = jdbc.query("""
                SELECT COALESCE(i.serial_number, i.instance_code), d.approval_name, i.status, i.submitted_at,
                       i.requester_user_id, i.requester_open_id, i.form_json, i.instance_code
                """ + source + where + " ORDER BY i.submitted_at DESC, i.id DESC LIMIT ? OFFSET ?",
                (rs, n) -> {
                    LocalDateTime utc = rs.getObject(4, LocalDateTime.class);
                    String createdAt = utc == null ? null : utc.atOffset(ZoneOffset.UTC)
                            .atZoneSameInstant(ApprovalSyncService.LOCAL_ZONE).format(DISPLAY_TIME);
                    return new StoredRow(rs.getString(1), rs.getString(2), rs.getString(3), createdAt,
                            rs.getString(5), rs.getString(6), forms.parse(rs.getString(7)), ApprovalAppLink.detail(rs.getString(8)));
                }, args.toArray());

        Set<String> employeeIds = new HashSet<>();
        for (StoredRow row : stored) {
            if (row.requester() != null) employeeIds.add(row.requester());
            if (row.requesterOpenId() != null) employeeIds.add(row.requesterOpenId());
            forms.collectContacts(row.form(), employeeIds);
        }
        Map<String, String> names = employeeNames(employeeIds);
        var rows = stored.stream().map(row -> {
            String requesterId = Objects.toString(row.requester(), "");
            String requesterOpenId = Objects.toString(row.requesterOpenId(), "");
            String requester = names.getOrDefault(requesterId,
                    names.getOrDefault(requesterOpenId, requesterId.isBlank() ? requesterOpenId : requesterId));
            return new ApprovalRow(row.number(), row.template(), row.status(), row.createdAt(),
                    forms.subject(row.form(), names), requester, forms.leavePeriod(row.form()),
                    forms.fields(row.form(), names), row.url());
        }).toList();

        model.addAttribute("templates", templates);
        model.addAttribute("templateId", templateId);
        model.addAttribute("from", from);
        model.addAttribute("to", to);
        model.addAttribute("rows", rows);
        model.addAttribute("count", count);
        model.addAttribute("page", currentPage);
        model.addAttribute("totalPages", totalPages);
        return "lark-approvals";
    }

    private Map<String, String> employeeNames(Set<String> ids) {
        Map<String, String> names = new HashMap<>();
        if (ids.isEmpty()) return names;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        List<String> args = new ArrayList<>(ids);
        args.addAll(ids);
        jdbc.query("SELECT id, open_id, name FROM lark_employees WHERE id IN (" + placeholders
                + ") OR open_id IN (" + placeholders + ")", rs -> {
            names.put(rs.getString("id"), rs.getString("name"));
            String openId = rs.getString("open_id");
            if (openId != null && !openId.isBlank()) names.put(openId, rs.getString("name"));
        }, args.toArray());
        return names;
    }

    private static LocalDateTime utcStart(LocalDate date) {
        return date.atStartOfDay(ApprovalSyncService.LOCAL_ZONE)
                .withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }
}
