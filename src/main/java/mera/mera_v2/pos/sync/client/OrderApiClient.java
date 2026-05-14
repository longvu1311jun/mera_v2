package mera.mera_v2.pos.sync.client;

import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import mera.mera_v2.pos.sync.dto.OrderApiDto;
import mera.mera_v2.pos.sync.dto.OrderListResponseDto;
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
public class OrderApiClient {

  private final RestTemplate restTemplate;

  @Value("${api.order.base-url}")
  private String baseUrl;

  @Value("${api.order.api-key}")
  private String apiKey;

  public OrderApiClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * Fetch orders using configured URL (legacy method).
   */
  public List<OrderApiDto> fetchOrders() {
    OrderListResponseDto resp = fetchOrdersPage(0, 0, 1, 200, "inserted_at", null);
    return resp.getData() != null ? resp.getData() : Collections.emptyList();
  }

  /**
   * Fetch orders with full dynamic parameters.
   * api_key is always included from config.
   *
   * @param startTimestamp Unix timestamp in seconds
   * @param endTimestamp  Unix timestamp in seconds
   * @param pageNumber     1-based page number
   * @param pageSize       page size
   * @param updateStatus   "inserted_at" or "updated_at"
   * @param status         order status filter, null/blank = no filter
   * @return OrderListResponseDto containing data + pagination metadata
   */
  public OrderListResponseDto fetchOrdersPage(
      long startTimestamp,
      long endTimestamp,
      int pageNumber,
      int pageSize,
      String updateStatus,
      String status
  ) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl)
        .queryParam("api_key", apiKey)
        .queryParam("startDateTime", startTimestamp)
        .queryParam("endDateTime", endTimestamp)
        .queryParam("page_number", pageNumber)
        .queryParam("page_size", pageSize)
        .queryParam("updateStatus", updateStatus);

    if (status != null && !status.isBlank()) {
      builder.queryParam("status", status);
    }

    String url = builder.build().toUriString();
    String maskedUrl = maskUrl(url);
    log.info("Calling API: {}", maskedUrl);
    log.info("Full URL (for reference): {}", url);

    try {
      // First, get raw response as String to see actual structure
      ResponseEntity<String> rawEntity = restTemplate.getForEntity(url, String.class);
      String rawResponse = rawEntity.getBody();
//      log.info("RAW API STATUS: {}, BODY ({} chars): {}", rawEntity.getStatusCode(),
//          rawResponse != null ? rawResponse.length() : 0, rawResponse);

      // Then manually parse with Jackson ObjectMapper (same instance RestTemplate uses)
      ObjectMapper mapper = new ObjectMapper();
      OrderListResponseDto dto = mapper.readValue(rawResponse, OrderListResponseDto.class);
      log.info("Parsed DTO successfully, data size: {}",
          dto.getData() != null ? dto.getData().size() : 0);
      log.info("Pagination: page={}, totalEntries={}, totalPages={}",
          dto.getPageNumber(), dto.getTotalEntries(), dto.getTotalPages());

      return dto;

    } catch (HttpClientErrorException e) {
      String body = e.getResponseBodyAsString();
      log.error("API client error {}: {}", e.getStatusCode(), body);
      throw new ApiClientException(
          "API returned client error: " + e.getStatusCode(),
          e,
          e.getStatusCode(),
          body
      );

    } catch (HttpServerErrorException e) {
      String body = e.getResponseBodyAsString();
      log.error("API server error {}: {}", e.getStatusCode(), body);
      throw new ApiClientException(
          "API returned server error: " + e.getStatusCode(),
          e,
          e.getStatusCode(),
          body
      );

    } catch (ResourceAccessException e) {
      log.error("Connection error: {}", e.getMessage());
      throw new ApiClientException("Failed to connect to API: " + e.getMessage(), e);

    } catch (ApiClientException e) {
      throw e;

    } catch (Exception e) {
      log.error("Unexpected error calling API: {}", e.getMessage(), e);
      throw new ApiClientException("Unexpected error: " + e.getMessage(), e);
    }
  }

  private String maskUrl(String url) {
    if (url == null) return "null";
    return url.replaceAll("api_key=[^&]*", "api_key=***");
  }
}