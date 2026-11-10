package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.*;
import java.util.List;

/** Resolve actual transitions rather than relying on history array ordering. */
public final class WebhookStatusDate {
    private WebhookStatusDate() {}

    public static Instant latestStatusOne(JsonNode root, ZoneId localZone) {
        Instant latest = null;
        for (JsonNode histories : List.of(root.path("histories"), root.path("data").path("histories"))) {
            if (!histories.isArray()) continue;
            for (JsonNode history : histories) {
                JsonNode change = history.path("status");
                if (!"1".equals(change.path("new").asText()) || "1".equals(change.path("old").asText())) continue;
                Instant time = parse(history.path("updated_at").asText(""), localZone);
                if (latest == null || time.isAfter(latest)) latest = time;
            }
        }
        return latest;
    }

    static Instant parse(String value, ZoneId localZone) {
        String text = value.trim();
        if (text.matches("[0-9]{10}")) return Instant.ofEpochSecond(Long.parseLong(text));
        if (text.matches("[0-9]{13}")) return Instant.ofEpochMilli(Long.parseLong(text));
        text = text.replace(' ', 'T');
        try { return OffsetDateTime.parse(text).toInstant(); }
        catch (java.time.format.DateTimeParseException ignored) {
            return LocalDateTime.parse(text).atZone(localZone).toInstant();
        }
    }
}
