package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class WebhookDataStorageService {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @Value("${webhook.storage.path:./webhook-data}")
    private String storagePath;

    private final ObjectMapper mapper = new ObjectMapper();
    private final ObjectWriter prettyWriter = mapper.writerWithDefaultPrettyPrinter();

    /**
     * Lưu webhook data vào file theo ngày
     * @param webhookData Raw webhook data (JSON string)
     */
    public void saveWebhookData(String webhookData) {
        if (webhookData == null || webhookData.isBlank()) {
            log.warn("⚠️ Webhook data is empty, skipping save");
            return;
        }

        try {
            LocalDate today = LocalDate.now();
            String dateStr = today.format(DATE_FORMATTER);

            // Tạo thư mục nếu chưa tồn tại
            Path dirPath = Paths.get(storagePath, dateStr);
            Files.createDirectories(dirPath);

            // Tạo tên file với timestamp
            long timestamp = System.currentTimeMillis();
            String fileName = "webhook_" + timestamp + ".json";
            Path filePath = dirPath.resolve(fileName);

            // Parse và format JSON để lưu (đẹp hơn)
            Object jsonObj = mapper.readValue(webhookData, Object.class);
            String formattedJson = prettyWriter.writeValueAsString(jsonObj);

            // Ghi file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(formattedJson);
            }

            log.info("✅ Saved webhook data to: {}", filePath);

        } catch (IOException e) {
            log.error("❌ Failed to save webhook data: {}", e.getMessage(), e);
        }
    }

    /**
     * Lưu webhook data với thông tin bổ sung
     * @param webhookData Raw webhook data (JSON string)
     * @param additionalInfo Thông tin bổ sung (status, tags,...)
     */
    public void saveWebhookData(String webhookData, String additionalInfo) {
        if (webhookData == null || webhookData.isBlank()) {
            log.warn("⚠️ Webhook data is empty, skipping save");
            return;
        }

        try {
            LocalDate today = LocalDate.now();
            String dateStr = today.format(DATE_FORMATTER);

            // Tạo thư mục nếu chưa tồn tại
            Path dirPath = Paths.get(storagePath, dateStr);
            Files.createDirectories(dirPath);

            // Tạo tên file với timestamp
            long timestamp = System.currentTimeMillis();
            String fileName = "webhook_" + timestamp + ".json";
            Path filePath = dirPath.resolve(fileName);

            // Parse và format JSON để lưu (đẹp hơn)
            Object jsonObj = mapper.readValue(webhookData, Object.class);

            // Tạo output với additional info
            StringBuilder output = new StringBuilder();
            output.append("=== WEBHOOK METADATA ===\n");
            output.append("Timestamp: ").append(timestamp).append("\n");
            output.append("Date: ").append(dateStr).append("\n");
            if (additionalInfo != null && !additionalInfo.isBlank()) {
                output.append("Additional Info: ").append(additionalInfo).append("\n");
            }
            output.append("\n=== WEBHOOK DATA ===\n");
            output.append(prettyWriter.writeValueAsString(jsonObj));

            // Ghi file
            try (FileWriter writer = new FileWriter(filePath.toFile())) {
                writer.write(output.toString());
            }

            log.info("✅ Saved webhook data with metadata to: {}", filePath);

        } catch (IOException e) {
            log.error("❌ Failed to save webhook data: {}", e.getMessage(), e);
        }
    }

    /**
     * Lấy đường dẫn thư mục lưu trữ
     */
    public String getStoragePath() {
        return storagePath;
    }

    /**
     * Đếm số file webhook trong ngày
     */
    public int getTodayWebhookCount() {
        try {
            LocalDate today = LocalDate.now();
            String dateStr = today.format(DATE_FORMATTER);
            Path dirPath = Paths.get(storagePath, dateStr);

            if (Files.exists(dirPath)) {
                return (int) Files.list(dirPath)
                        .filter(p -> p.toString().endsWith(".json"))
                        .count();
            }
        } catch (IOException e) {
            log.error("❌ Failed to count today's webhooks: {}", e.getMessage());
        }
        return 0;
    }
}
