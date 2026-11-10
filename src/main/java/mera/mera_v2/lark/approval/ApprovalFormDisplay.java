package mera.mera_v2.lark.approval;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class ApprovalFormDisplay {
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ObjectMapper json;

    public record Field(String label, String value) {}

    JsonNode parse(String form) {
        if (form != null) {
            try {
                JsonNode parsed = json.readTree(form);
                if (parsed != null && parsed.isTextual()) parsed = json.readTree(parsed.asText());
                if (parsed != null && parsed.isArray()) return parsed;
            } catch (JsonProcessingException ignored) {
                // Existing records can contain a malformed or unsupported form.
            }
        }
        return MissingNode.getInstance();
    }

    void collectContacts(JsonNode node, Set<String> ids) {
        if ("contact".equals(node.path("type").asText())) {
            for (JsonNode id : node.path("value")) if (id.isTextual()) ids.add(id.asText());
            for (JsonNode id : node.path("open_ids")) if (id.isTextual()) ids.add(id.asText());
        }
        for (JsonNode child : node) collectContacts(child, ids);
    }

    String subject(JsonNode form, Map<String, String> names) {
        for (JsonNode field : form) {
            if ("contact".equals(field.path("type").asText())) {
                String value = contacts(field, names);
                if (!value.isBlank()) return value;
            }
        }
        return "";
    }

    String leavePeriod(JsonNode form) {
        for (JsonNode field : form) {
            if ("dateInterval".equals(field.path("type").asText())) return interval(field.path("value"));
        }
        return "";
    }

    List<Field> fields(JsonNode form, Map<String, String> names) {
        var result = new ArrayList<Field>();
        for (JsonNode field : form) {
            String label = field.path("name").asText(field.path("id").asText());
            JsonNode value = field.path("value");
            String type = field.path("type").asText();
            String rendered = switch (type) {
                case "contact" -> contacts(field, names);
                case "date" -> date(value.asText());
                case "dateInterval" -> interval(value);
                case "telephone" -> (value.path("country_code").asText() + " "
                        + value.path("national_number").asText()).strip();
                case "radioV2", "checkboxV2" -> options(field, value);
                case "fieldList" -> rows(value, names);
                default -> plain(value);
            };
            if (!rendered.isBlank()) result.add(new Field(label.isBlank() ? type : label, rendered));
        }
        return result;
    }

    private String rows(JsonNode values, Map<String, String> names) {
        var result = new ArrayList<String>();
        int number = 0;
        for (JsonNode row : values) {
            var columns = fields(row, names).stream()
                    .map(field -> field.label() + ": " + field.value()).toList();
            if (!columns.isEmpty()) result.add("#" + (number + 1) + " " + String.join(" | ", columns));
            number++;
        }
        return String.join("\n", result);
    }

    private static String contacts(JsonNode field, Map<String, String> names) {
        JsonNode ids = field.path("value");
        JsonNode openIds = field.path("open_ids");
        var resolved = new ArrayList<String>();
        for (int i = 0; i < Math.max(ids.size(), openIds.size()); i++) {
            String id = ids.path(i).asText();
            String openId = openIds.path(i).asText();
            String name = names.getOrDefault(id, names.getOrDefault(openId, id.isBlank() ? openId : id));
            if (!name.isBlank()) resolved.add(name);
        }
        return String.join(", ", resolved);
    }

    private static String options(JsonNode field, JsonNode value) {
        JsonNode option = field.path("option");
        if (option.isObject()) return option.path("text").asText(plain(value));
        if (option.isArray() && !option.isEmpty()) {
            var labels = new ArrayList<String>();
            for (JsonNode selected : option) {
                String text = selected.path("text").asText();
                if (!text.isBlank()) labels.add(text);
            }
            if (!labels.isEmpty()) return String.join(", ", labels);
        }
        return plain(value);
    }

    private static String interval(JsonNode value) {
        String start = date(value.path("start").asText());
        String end = date(value.path("end").asText());
        return start.isBlank() ? end : end.isBlank() || end.equals(start) ? start : start + " – " + end;
    }

    private static String date(String value) {
        try {
            return OffsetDateTime.parse(value).atZoneSameInstant(ApprovalSyncService.LOCAL_ZONE).format(DATE_TIME);
        } catch (DateTimeParseException ex) {
            return value;
        }
    }

    private static String plain(JsonNode value) {
        if (value.isNull() || value.isMissingNode()) return "";
        if (value.isArray()) {
            var items = new ArrayList<String>();
            for (JsonNode item : value) items.add(plain(item));
            return String.join(", ", items);
        }
        return value.isValueNode() ? value.asText() : value.toString();
    }
}
