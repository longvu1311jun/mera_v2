package mera.mera_v2.pos.sync.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.util.Collections;
import java.util.List;

@Slf4j
@Component
public class CustomerApiClient {

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
     * Fetch a page of customers from POS API filtered by updated_at date range.
     *
     * @param startDate yyyy-MM-dd (inclusive)
     * @param endDate   yyyy-MM-dd (inclusive)
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
            builder.queryParam("start_date", startDate);
        }
        if (endDate != null && !endDate.isBlank()) {
            builder.queryParam("end_date", endDate);
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

            // Fallback: derive totalPages from total + page_size if API didn't return total_pages.
            Integer total = dto.getTotal() != null ? dto.getTotal() : dto.getTotalEntries();
            int computedTotalPages = 1;
            if (total != null && pageSize > 0) {
                computedTotalPages = (int) Math.ceil(total / (double) pageSize);
            } else if (dto.getData() != null && dto.getData().size() >= pageSize) {
                // API không trả total → mở rộng sang trang kế tiếp để bắt phần còn lại.
                computedTotalPages = Math.max((dto.getTotalPages() != null ? dto.getTotalPages() : 1), pageNumber + 1);
            }

            if (dto.getTotalPages() == null) {
                dto.setTotalPages(computedTotalPages);
            } else {
                // POS API có thể trả total_pages=1 dù còn data → nâng lên nếu page hiện tại đầy.
                if (dto.getData() != null && dto.getData().size() >= pageSize) {
                    dto.setTotalPages(Math.max(dto.getTotalPages(), pageNumber + 1));
                }
            }

            log.info("Fetched {} customers from POS (page {}/{}, total={}, computedTotalPages={})",
                    dto.getData().size(),
                    pageNumber,
                    dto.getTotalPages(),
                    total != null ? total : dto.getData().size(),
                    computedTotalPages);
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