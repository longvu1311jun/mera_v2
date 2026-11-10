package mera.mera_v2.lark.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import mera.mera_v2.lark.webhook.service.TenantTokenService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

@Service
@Slf4j
@RequiredArgsConstructor
public class ApprovalSyncService {
    static final String TENANT = "mera_group";
    static final ZoneId LOCAL_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final RestTemplate http;
    private final ObjectMapper json;
    private final TenantTokenService tokens;
    private final LarkBaseProperties lark;

    public record Template(long id, String code, String name) {}
    public record Result(String name, int listed, int saved, int failed, String status) {}

    public List<Template> templates() {
        return jdbc.query("""
                SELECT id, approval_code, approval_name FROM lark_approval_definitions
                WHERE tenant_key=? ORDER BY approval_name, approval_code
                """, (row, n) -> new Template(row.getLong(1), row.getString(2), row.getString(3)), TENANT);
    }

    public void seedTemplates() {
        JsonNode definitions;
        try (var input = new ClassPathResource("lark-approval-templates.json").getInputStream()) {
            definitions = json.readTree(input);
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot load the bundled approval catalog", ex);
        }
        transaction.executeWithoutResult(tx -> definitions.forEach(definition ->
                insertTemplate(required(definition, "approval_code"), definition)));
    }

    public void addTemplate(String code) {
        JsonNode definition = get("/approvals/" + code, Map.of());
        insertTemplate(code, definition);
    }

    private void insertTemplate(String code, JsonNode definition) {
        jdbc.update("""
                INSERT INTO lark_approval_definitions
                    (tenant_key, approval_code, approval_name, source_payload, fetched_at)
                VALUES (?, ?, ?, ?, UTC_TIMESTAMP(3))
                ON DUPLICATE KEY UPDATE id=id
                """, TENANT, code, required(definition, "approval_name"), definition.toString());
    }

    public Result sync(Template template, LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(LOCAL_ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(LOCAL_ZONE).toInstant();
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO lark_approval_sync_runs
                    (definition_id, window_start, window_end, status, started_at)
                    VALUES (?, ?, ?, 'RUNNING', UTC_TIMESTAMP(3))
                    """, java.sql.Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, template.id());
            statement.setObject(2, utc(start));
            statement.setObject(3, utc(end));
            return statement;
        }, key);
        long run = Objects.requireNonNull(key.getKey()).longValue();

        Set<String> seen = new HashSet<>();
        Set<String> cursors = new HashSet<>();
        List<String> failed = new ArrayList<>();
        int saved = 0;
        String cursor = null;
        String failure = null;
        try {
            JsonNode definition = get("/approvals/" + template.code(), Map.of());
            jdbc.update("""
                    UPDATE lark_approval_definitions SET approval_name=?, source_payload=?, fetched_at=UTC_TIMESTAMP(3)
                    WHERE id=?
                    """, required(definition, "approval_name"), definition.toString(), template.id());
            do {
                Map<String, String> query = new LinkedHashMap<>(Map.of(
                        "approval_code", template.code(), "page_size", "100",
                        "start_time", Long.toString(start.toEpochMilli()),
                        "end_time", Long.toString(end.toEpochMilli() - 1)));
                if (cursor != null) query.put("page_token", cursor);
                JsonNode page = get("/instances", query);
                JsonNode codes = page.path("instance_code_list");
                if (!codes.isArray() && !codes.isMissingNode() && !codes.isNull()) {
                    throw new IllegalStateException("Invalid instance list");
                }
                for (JsonNode node : codes) {
                    String code = node.asText();
                    if (!seen.add(code)) continue;
                    try {
                        JsonNode detail = get("/instances/" + code, Map.of());
                        if (!code.equals(required(detail, "instance_code"))
                                || !template.code().equalsIgnoreCase(required(detail, "approval_code"))) {
                            throw new IllegalStateException("Unexpected approval identity");
                        }
                        save(template, code, detail);
                        saved++;
                    } catch (RuntimeException ex) {
                        failed.add(code);
                        log.warn("Approval detail failed: {} ({})", code, ex.getClass().getSimpleName());
                    }
                }
                cursor = page.path("has_more").asBoolean() ? value(page, "page_token") : null;
                if (page.path("has_more").asBoolean() && (cursor == null || !cursors.add(cursor))) {
                    throw new IllegalStateException("Invalid Lark pagination cursor");
                }
                jdbc.update("""
                        UPDATE lark_approval_sync_runs SET next_page_token=?, listed_count=?,
                        saved_count=?, failed_count=?, failed_instances=? WHERE id=?
                        """, cursor, seen.size(), saved, failed.size(), json.valueToTree(failed).toString(), run);
            } while (cursor != null);
        } catch (RuntimeException ex) {
            failure = ex instanceof IllegalStateException ? ex.getMessage() : ex.getClass().getSimpleName();
            log.warn("Approval sync run {} stopped: {}", run, failure);
        }
        String status = failure != null ? "FAILED" : failed.isEmpty() ? "SUCCESS" : "PARTIAL";
        jdbc.update("""
                UPDATE lark_approval_sync_runs SET status=?, listed_count=?, saved_count=?,
                failed_count=?, failed_instances=?, last_error=?, finished_at=UTC_TIMESTAMP(3) WHERE id=?
                """, status, seen.size(), saved, failed.size(), json.valueToTree(failed).toString(), failure, run);
        return new Result(template.name(), seen.size(), saved, failed.size(), status);
    }

    private void save(Template template, String code, JsonNode detail) {
        JsonNode form = detail.get("form");
        String formJson = null;
        if (form != null && !form.isNull()) {
            try {
                formJson = form.isTextual() && form.asText().isBlank() ? null
                        : form.isTextual() ? json.readTree(form.asText()).toString() : form.toString();
            } catch (JsonProcessingException ex) {
                throw new IllegalStateException("Invalid approval form", ex);
            }
        }
        String parsedForm = formJson;
        transaction.executeWithoutResult(tx -> {
            jdbc.update("""
                    INSERT INTO lark_approval_instances
                    (definition_id, tenant_key, instance_code, serial_number, status, requester_user_id,
                     requester_open_id, department_id, submitted_at, ended_at, form_json, source_payload, fetched_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, UTC_TIMESTAMP(3))
                    ON DUPLICATE KEY UPDATE definition_id=VALUES(definition_id), serial_number=VALUES(serial_number),
                    status=VALUES(status), requester_user_id=VALUES(requester_user_id),
                    requester_open_id=VALUES(requester_open_id), department_id=VALUES(department_id),
                    submitted_at=VALUES(submitted_at), ended_at=VALUES(ended_at), form_json=VALUES(form_json),
                    source_payload=VALUES(source_payload), fetched_at=VALUES(fetched_at)
                    """, template.id(), TENANT, code, value(detail, "serial_number"), required(detail, "status"),
                    value(detail, "user_id"), value(detail, "open_id"), value(detail, "department_id"),
                    timestamp(detail, "start_time"), timestamp(detail, "end_time"), parsedForm, detail.toString());
            Long id = jdbc.queryForObject("""
                    SELECT id FROM lark_approval_instances WHERE tenant_key=? AND instance_code=?
                    """, Long.class, TENANT, code);
            JsonNode tasks = detail.path("task_list");
            if (tasks.isArray()) {
                jdbc.update("DELETE FROM lark_approval_tasks WHERE instance_id=?", id);
                for (JsonNode task : tasks) {
                    jdbc.update("""
                            INSERT INTO lark_approval_tasks
                            (instance_id, task_id, node_id, node_name, task_type, status, assignee_user_id,
                             assignee_open_id, started_at, ended_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                            """, id, required(task, "id"), value(task, "node_id"), value(task, "node_name"),
                            value(task, "type"), value(task, "status"), value(task, "user_id"),
                            value(task, "open_id"), timestamp(task, "start_time"), timestamp(task, "end_time"));
                }
            }
        });
    }

    private JsonNode get(String path, Map<String, String> query) {
        var uri = UriComponentsBuilder.fromUriString(lark.getBaseUrl())
                .path("/open-apis/approval/v4").path(path);
        Map<String, String> variables = new HashMap<>();
        query.forEach((name, value) -> {
            uri.queryParam(name, "{" + name + "}");
            variables.put(name, value);
        });
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(tokens.getTenantAccessToken());
        String body = http.exchange(uri.encode().buildAndExpand(variables).toUri(),
                HttpMethod.GET, new HttpEntity<>(headers), String.class).getBody();
        JsonNode response;
        try {
            response = body == null ? null : json.readTree(body);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Invalid Lark Approval response", ex);
        }
        if (response == null || response.path("code").asInt(-1) != 0 || !response.path("data").isObject()) {
            throw new IllegalStateException("Lark Approval API code " +
                    (response == null ? -1 : response.path("code").asInt(-1)));
        }
        return response.path("data");
    }

    private static String required(JsonNode node, String field) {
        return Objects.requireNonNull(value(node, field), "Missing approval field " + field);
    }

    private static String value(JsonNode node, String field) {
        String value = node.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private static LocalDateTime timestamp(JsonNode node, String field) {
        long ms = node.path(field).asLong();
        return ms > 0 ? utc(Instant.ofEpochMilli(ms)) : null;
    }

    private static LocalDateTime utc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }
}
