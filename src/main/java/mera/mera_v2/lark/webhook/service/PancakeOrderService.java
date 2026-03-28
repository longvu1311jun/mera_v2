package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.PancakeOrderResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PancakeOrderService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // Cache orders in memory
    private List<PancakeOrderResponse.PancakeOrder> cachedOrders;

    public void setCachedOrders(List<PancakeOrderResponse.PancakeOrder> orders) {
        this.cachedOrders = orders;
        log.info("📦 Cached {} orders", orders != null ? orders.size() : 0);
    }

    public List<PancakeOrderResponse.PancakeOrder> getCachedOrders() {
        return cachedOrders;
    }

    @Value("${pancake.api.base-url:https://pos.pages.fm/api/v1}")
    private String baseUrl;

    @Value("${pancake.api.key:2a6ed8b51a8d4ae49a851d5876b00018}")
    private String apiKey;

    @Value("${pancake.shop.id:1546758}")
    private Long shopId;

    @Value("${pancake.export.json-dir:./order-responses}")
    private String jsonExportDir;

    /**
     * Chuyển đổi ngày sang timestamp UTC
     * Ngày bắt đầu: 00:00 +7 = 17:00 UTC ngày hôm trước
     * Ngày kết thúc: 23:59:59 +7 = 16:59:59 UTC cùng ngày
     * Hỗ trợ cả dd/MM/yyyy (date picker) và yyyy-MM-dd (date input)
     */
    public long[] convertDatesToTimestamps(String startDate, String endDate) {
        DateTimeFormatter formatter;
        LocalDate startLocalDate;
        LocalDate endLocalDate;

        // Kiểm tra định dạng ngày
        if (startDate.contains("/")) {
            // Format: dd/MM/yyyy
            formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
            startLocalDate = LocalDate.parse(startDate, formatter);
            endLocalDate = LocalDate.parse(endDate, formatter);
        } else {
            // Format: yyyy-MM-dd (date picker)
            formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            startLocalDate = LocalDate.parse(startDate, formatter);
            endLocalDate = LocalDate.parse(endDate, formatter);
        }

        ZonedDateTime startDateTime = startLocalDate.atTime(0, 0, 0)
                .atZone(ZoneId.of("+07:00"))
                .withZoneSameInstant(ZoneId.of("UTC"));
        long startTimestamp = startDateTime.toEpochSecond();

        ZonedDateTime endDateTime = endLocalDate.atTime(23, 59, 59)
                .atZone(ZoneId.of("+07:00"))
                .withZoneSameInstant(ZoneId.of("UTC"));
        long endTimestamp = endDateTime.toEpochSecond();

        log.info("📅 Date range: {} to {}", startDate, endDate);
        log.info("📅 Timestamp: {} to {} (UTC)", startTimestamp, endTimestamp);

        return new long[]{startTimestamp, endTimestamp};
    }

    /**
     * Gọi API lấy đơn hàng
     */
    private List<PancakeOrderResponse.PancakeOrder> fetchOrdersPage(long startTimestamp, long endTimestamp, String status, String updateStatus, int pageSize, int pageNumber, String search) throws Exception {
        String url = String.format("%s/shops/%d/orders?api_key=%s&page_size=%d&page_number=%d&updateStatus=%s&startDateTime=%d&endDateTime=%d",
                baseUrl, shopId, apiKey, pageSize, pageNumber, updateStatus, startTimestamp, endTimestamp);

        // Thêm status parameter nếu có
        if (status != null && !status.isEmpty()) {
            url = url + "&status=" + status;
        }

        log.info("🔄 API call page {}: {}", pageNumber, url);

        // Thêm search vào query param nếu có
        if (search != null && !search.isEmpty()) {
            url = url + "&search=" + search;
        }

        HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());
        ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            String rawJson = response.getBody();

            // Lưu raw JSON response vào file riêng cho mỗi page
            savePageResponseToFile(rawJson, pageNumber, startDateFromTimestamp(startTimestamp), endDateFromTimestamp(endTimestamp));

            JsonNode json = objectMapper.readTree(rawJson);

            // Check success
            Boolean success = json.path("success").asBoolean(true);
            if (!success) {
                String message = json.path("message").asText("Unknown error");
                log.error("❌ API returned success=false: {}", message);
                throw new RuntimeException("API error: " + message);
            }

            JsonNode dataNode = json.path("data");
            List<PancakeOrderResponse.PancakeOrder> orders = new ArrayList<>();

            if (dataNode.isArray()) {
                for (JsonNode orderNode : dataNode) {
                    try {
                        PancakeOrderResponse.PancakeOrder order = objectMapper.treeToValue(orderNode, PancakeOrderResponse.PancakeOrder.class);
                        orders.add(order);
                    } catch (Exception e) {
                        log.warn("⚠️ Failed to parse order: {}", e.getMessage());
                    }
                }
            }

            int totalPages = json.path("total_pages").asInt(1);
            log.info("✅ Page {}: got {} orders, total pages: {}", pageNumber, orders.size(), totalPages);

            return orders;
        } else {
            throw new RuntimeException("HTTP error: " + response.getStatusCode());
        }
    }

    /**
     * Lấy một trang đơn hàng
     */
    public List<PancakeOrderResponse.PancakeOrder> fetchOrdersPage(String startDate, String endDate, String status, String updateStatus, int pageSize, int pageNumber, String search) {
        long[] timestamps = convertDatesToTimestamps(startDate, endDate);
        try {
            return fetchOrdersPage(timestamps[0], timestamps[1], status, updateStatus, pageSize, pageNumber, search);
        } catch (Exception e) {
            log.error("❌ Error fetching page {}: {}", pageNumber, e.getMessage());
            return new ArrayList<>();
        }
    }

    /**
     * Lấy tất cả đơn hàng trong khoảng ngày (song song)
     */
    public List<PancakeOrderResponse.PancakeOrder> fetchAllOrders(String startDate, String endDate, String status, String updateStatus, int pageSize, String search) {
        long[] timestamps = convertDatesToTimestamps(startDate, endDate);
        long startTimestamp = timestamps[0];
        long endTimestamp = timestamps[1];

        log.info("🔄 Fetching orders (parallel): {} to {}, status={}, updateStatus={}, search={}", startDate, endDate, status, updateStatus, search);

        // Gọi API page 1 để lấy total_pages
        String url = String.format("%s/shops/%d/orders?api_key=%s&page_size=%d&page_number=1&updateStatus=%s&startDateTime=%d&endDateTime=%d",
                baseUrl, shopId, apiKey, pageSize, updateStatus, startTimestamp, endTimestamp);
        if (status != null && !status.isEmpty()) {
            url = url + "&status=" + status;
        }
        if (search != null && !search.isEmpty()) {
            url = url + "&search=" + search;
        }

        int totalPages = 1;
        try {
            HttpEntity<Void> entity = new HttpEntity<>(new HttpHeaders());
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                JsonNode json = objectMapper.readTree(response.getBody());
                totalPages = json.path("total_pages").asInt(1);
                log.info("📊 Total pages: {}", totalPages);
            }
        } catch (Exception e) {
            log.error("❌ Error fetching page 1: {}", e.getMessage());
            return new ArrayList<>();
        }

        // Sử dụng ExecutorService để gọi song song
        int threadPoolSize = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadPoolSize);

        List<CompletableFuture<List<PancakeOrderResponse.PancakeOrder>>> futures = new ArrayList<>();

        // Gọi API song song cho tất cả các page
        for (int pageNumber = 1; pageNumber <= totalPages; pageNumber++) {
            final int page = pageNumber;
            CompletableFuture<List<PancakeOrderResponse.PancakeOrder>> future = CompletableFuture.supplyAsync(() -> {
                try {
                    return fetchOrdersPage(startTimestamp, endTimestamp, status, updateStatus, pageSize, page, search);
                } catch (Exception e) {
                    log.error("❌ Error fetching page {}: {}", page, e.getMessage());
                    return new ArrayList<>();
                }
            }, executor);
            futures.add(future);
        }

        // Chờ tất cả các futures hoàn thành
        List<PancakeOrderResponse.PancakeOrder> allOrders = new ArrayList<>();
        for (CompletableFuture<List<PancakeOrderResponse.PancakeOrder>> future : futures) {
            try {
                allOrders.addAll(future.join());
            } catch (Exception e) {
                log.error("❌ Error joining future: {}", e.getMessage());
            }
        }

        executor.shutdown();

        log.info("✅ Total orders fetched: {}", allOrders.size());
        return allOrders;
    }

    /**
     * Lưu raw JSON response của một page vào file
     */
    private void savePageResponseToFile(String rawJson, int pageNumber, String startDate, String endDate) {
        try {
            Path dir = Paths.get(jsonExportDir);
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }

            String fileName = String.format("orders_%s_to_%s_page_%03d.json", startDate, endDate, pageNumber);
            Path filePath = dir.resolve(fileName);

            Files.writeString(filePath, rawJson);
            log.info("💾 Saved page {} response to {}", pageNumber, filePath.toAbsolutePath());
        } catch (IOException e) {
            log.warn("⚠️ Failed to save page {} response to file: {}", pageNumber, e.getMessage());
        }
    }

    /**
     * Convert timestamp (epoch second) sang date string yyyy-MM-dd
     */
    private String startDateFromTimestamp(long timestamp) {
        return java.time.Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.of("+07:00"))
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }

    private String endDateFromTimestamp(long timestamp) {
        return java.time.Instant.ofEpochSecond(timestamp)
                .atZone(ZoneId.of("+07:00"))
                .toLocalDate()
                .format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
    }
}
