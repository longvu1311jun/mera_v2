package mera.mera_v2.pos.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.pos.sync.dto.CustomerApiDto;
import mera.mera_v2.pos.sync.dto.CustomerListResponseDto;
import mera.mera_v2.pos.sync.exception.ApiClientException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class CustomerApiClient {
  private static final Logger log = LoggerFactory.getLogger(CustomerApiClient.class);

    private static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DMY_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${pos.api.base-url:https://pos.pages.fm/api/v1}")
    private String baseUrl;

    @Value("${pos.api.api-key:}")
    private String apiKey;

    @Value("${pos.api.shop-id:}")
    private String shopId;

    public CustomerApiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Fetch a page of customers from POS API filtered by inserted_at range.
     * Ngày được quy về múi giờ VN (UTC+7) rồi đổi sang epoch giây:
     * startDate → 00:00:00 VN (= 17:00:00 UTC hôm trước), endDate → 23:59:59 VN (= 16:59:59 UTC).
     *
     * @param startDate yyyy-MM-dd hoặc dd/MM/yyyy (inclusive)
     * @param endDate   yyyy-MM-dd hoặc dd/MM/yyyy (inclusive)
     * @param pageNumber 1-based page number
     * @param pageSize   page size
     */
    public CustomerListResponseDto fetchCustomersPage(String startDate, String endDate, int pageNumber, int pageSize) {
        if (shopId == null || shopId.isBlank()) {
            throw new IllegalStateException("pos.api.shop-id is not configured");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("pos.api.api-key is not configured");
        }

        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/shops/" + shopId + "/customers")
                .queryParam("api_key", apiKey)
                .queryParam("page_size", pageSize)
                .queryParam("page_number", pageNumber);

        if (startDate != null && !startDate.isBlank()) {
            builder.queryParam("start_time_inserted_at", toStartOfDayEpochSecond(startDate));
        }
        if (endDate != null && !endDate.isBlank()) {
            builder.queryParam("end_time_inserted_at", toEndOfDayEpochSecond(endDate));
        }

        String url = builder.build().toUriString();
        log.info("Calling customer API: {}", url.replaceAll("api_key=[^&]*", "api_key=***"));

        try {
            ResponseEntity<String> rawEntity = restTemplate.getForEntity(url, String.class);
            String rawResponse = rawEntity.getBody();
            log.debug("Customer API raw response (first 500 chars): {}",
                    rawResponse != null && rawResponse.length() > 500 ? rawResponse.substring(0, 500) + "..." : rawResponse);
            log.info("Customer API page {} raw response (first 800 chars): {}",
                    pageNumber,
                    rawResponse != null && rawResponse.length() > 800 ? rawResponse.substring(0, 800) + "..." : rawResponse);
            // Debug: log full pagination metadata block
            try {
                JsonNode tmpRoot = objectMapper.readTree(rawResponse);
                StringBuilder meta = new StringBuilder();
                if (tmpRoot.has("total_entries")) meta.append("total_entries=").append(tmpRoot.get("total_entries")).append(",");
                if (tmpRoot.has("total")) meta.append("total=").append(tmpRoot.get("total")).append(",");
                if (tmpRoot.has("total_pages")) meta.append("total_pages=").append(tmpRoot.get("total_pages")).append(",");
                if (tmpRoot.has("current_page")) meta.append("current_page=").append(tmpRoot.get("current_page")).append(",");
                if (tmpRoot.has("page_number")) meta.append("page_number=").append(tmpRoot.get("page_number")).append(",");
                if (tmpRoot.has("page_size")) meta.append("page_size=").append(tmpRoot.get("page_size")).append(",");
                log.info("[POS-CUSTOMER-META page={}] {}", pageNumber, meta);
            } catch (Exception ignored) {
            }
            JsonNode root = objectMapper.readTree(rawResponse);
            CustomerListResponseDto dto = objectMapper.treeToValue(root, CustomerListResponseDto.class);
            if (dto.getData() == null) {
                dto.setData(Collections.emptyList());
            }

            // API trả sẵn total_pages/total_entries — chỉ fallback khi thiếu.
            Integer total = dto.getTotalEntries() != null ? dto.getTotalEntries() : dto.getTotal();
            if (dto.getTotalPages() == null) {
                if (total != null && pageSize > 0) {
                    dto.setTotalPages((int) Math.ceil(total / (double) pageSize));
                } else {
                    dto.setTotalPages(1);
                }
            }

            log.info("Fetched {} customers from POS (page {}/{}, total_entries={})",
                    dto.getData().size(), pageNumber, dto.getTotalPages(), total);
            return dto;
        } catch (HttpClientErrorException e) {
            log.error("Customer API client error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException("API returned client error: " + e.getStatusCode(), e,
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (HttpServerErrorException e) {
            log.error("Customer API server error {}: {}", e.getStatusCode(), e.getResponseBodyAsString());
            throw new ApiClientException("API returned server error: " + e.getStatusCode(), e,
                    e.getStatusCode(), e.getResponseBodyAsString());
        } catch (ResourceAccessException e) {
            log.error("Customer API connection error: {}", e.getMessage());
            throw new ApiClientException("Failed to connect to API: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error calling customer API: {}", e.getMessage(), e);
            throw new ApiClientException("Unexpected error: " + e.getMessage(), e);
        }
    }

    /** 00:00:00 theo giờ VN của ngày truyền vào, đổi ra epoch giây (VD 01/01/2026 → 1767200400). */
    private long toStartOfDayEpochSecond(String date) {
        return parseDate(date).atStartOfDay(VN_ZONE).toEpochSecond();
    }

    /** 23:59:59 theo giờ VN của ngày truyền vào, đổi ra epoch giây (VD 31/01/2026 → 1769878799). */
    private long toEndOfDayEpochSecond(String date) {
        return parseDate(date).atTime(23, 59, 59).atZone(VN_ZONE).toEpochSecond();
    }

    private LocalDate parseDate(String date) {
        String trimmed = date.trim();
        try {
            return LocalDate.parse(trimmed); // yyyy-MM-dd
        } catch (Exception ignored) {
        }
        try {
            return LocalDate.parse(trimmed, DMY_FORMAT); // dd/MM/yyyy
        } catch (Exception e) {
            throw new IllegalArgumentException("Ngày không hợp lệ: '" + date + "' (chấp nhận yyyy-MM-dd hoặc dd/MM/yyyy)");
        }
    }

    public List<CustomerApiDto> fetchAllCustomersByDateRange(String startDate, String endDate, int pageSize) {
        CustomerListResponseDto firstPage = fetchCustomersPage(startDate, endDate, 1, pageSize);
        if (firstPage.getData().isEmpty()) {
            return Collections.emptyList();
        }

        java.util.ArrayList<CustomerApiDto> all = new java.util.ArrayList<>(firstPage.getData());
        Integer totalPages = firstPage.getTotalPages() != null ? firstPage.getTotalPages()
                : (firstPage.getTotal() != null ? (int) Math.ceil(firstPage.getTotal() / (double) pageSize) : 1);

        for (int page = 2; page <= totalPages; page++) {
            CustomerListResponseDto next = fetchCustomersPage(startDate, endDate, page, pageSize);
            if (next.getData() != null && !next.getData().isEmpty()) {
                all.addAll(next.getData());
            }
        }
        return all;
    }
}