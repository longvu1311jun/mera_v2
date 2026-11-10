package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.*;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.*;
import java.util.*;

/** Dedicated structured audit trail; never writes tokens or raw customer payloads. */
@Service
public class WebhookCreationLogService {
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path directory;

    public WebhookCreationLogService(@Value("${lark.webhook.creation-log-dir:logs/lark-webhook}") String directory) {
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    public synchronized void append(Entry entry) throws IOException {
        Files.createDirectories(directory);
        entry.setTimestamp(ZonedDateTime.now(ZONE).toOffsetDateTime().toString());
        Files.writeString(directory.resolve(LocalDate.now(ZONE) + ".jsonl"),
                mapper.writeValueAsString(entry) + System.lineSeparator(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }

    public synchronized List<Entry> read(LocalDate from, LocalDate to, String order, String status) throws IOException {
        // Latest state per attempt, including attempts interrupted while queued/processing.
        LinkedHashMap<String, Entry> entries = new LinkedHashMap<>();
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Path file = directory.resolve(date + ".jsonl");
            if (!Files.exists(file)) continue;
            try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Entry entry;
                    try { entry = mapper.readValue(line, Entry.class); }
                    catch (com.fasterxml.jackson.core.JsonProcessingException incompleteLine) { continue; }
                    if (order != null && !order.isBlank() && !entry.getOrderId().contains(order.trim())) continue;
                    entries.remove(entry.getAttemptId());
                    entries.put(entry.getAttemptId(), entry);
                    // Bounded recent window; the page explicitly describes this limit.
                    if (entries.size() > 10000) entries.remove(entries.keySet().iterator().next());
                }
            }
        }
        List<Entry> result = new ArrayList<>(entries.values());
        Collections.reverse(result);
        return result.stream().filter(e -> status == null || status.isBlank() || status.equals(e.getStatus()))
                .limit(200).toList();
    }

    @Data
    public static class Entry {
        private String timestamp;
        private String attemptId = UUID.randomUUID().toString();
        private String orderId = "";
        private String shopId = "";
        private String cskh = "";
        private String baseId = "";
        private String tableId = "";
        private String recordId = "";
        private String statusOneAt = "";
        private String stage = "Tiếp nhận";
        private String status = "QUEUED";
        private String reason = "Đã nhận webhook, chờ xử lý";

        public static Entry of(JsonNode root) {
            Entry entry = new Entry();
            JsonNode order = root.path("data").isObject() ? root.path("data") : root;
            entry.orderId = order.path("id").asText(root.path("id").asText(""));
            entry.shopId = order.path("shop_id").asText(root.path("shop_id").asText(""));
            return entry;
        }

        public void finish(String status, String reason) {
            this.status = status;
            this.reason = reason;
        }
    }
}
