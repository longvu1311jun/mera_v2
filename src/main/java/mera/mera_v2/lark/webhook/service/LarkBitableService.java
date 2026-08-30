package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.BitableRecordRequest;
import mera.mera_v2.lark.webhook.dto.BitableRecordResponse;
import mera.mera_v2.lark.webhook.dto.BitableSearchRequest;
import mera.mera_v2.lark.webhook.dto.BitableSearchResponse;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkBitableService {
    
    private static final String BASE_URL = "https://open.larksuite.com/open-apis/bitable/v1/apps";
    private final RestTemplate restTemplate;
    
    public BitableRecordResponse createRecord(
            String appToken,
            String tableId,
            String userAccessToken,
            Map<String, Object> fields
    ) throws Exception {
        String url = String.format("%s/%s/tables/%s/records", BASE_URL, appToken, tableId);
        
        BitableRecordRequest request = BitableRecordRequest.builder()
                .fields(fields)
                .build();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);
        
        // Log request body để debug
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            String requestBodyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(request);
            log.info("📤 BITABLE API REQUEST:");
            log.info("   URL: {}", url);
            log.info("   Base ID: {}", appToken);
            log.info("   Table ID: {}", tableId);
            log.info("   User Access Token: {}", maskToken(userAccessToken));
            log.info("   Request Body:\n{}", requestBodyJson);
        } catch (Exception e) {
            log.warn("⚠️ Failed to serialize request body for logging: {}", e.getMessage());
        }
        
        HttpEntity<BitableRecordRequest> entity = new HttpEntity<>(request, headers);
        
        log.info("Creating record in Bitable: appToken={}, tableId={}", 
                maskToken(appToken), tableId);

        try {
            ResponseEntity<BitableRecordResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    BitableRecordResponse.class
            );

            BitableRecordResponse result = response.getBody();

            if (result == null) {
                throw new RuntimeException("Empty response from Lark Bitable API");
            }

            if (!result.isSuccess()) {
                log.error("Failed to create record: code={}, msg={}",
                        result.getCode(), result.getMsg());
                throw new RuntimeException(
                        String.format("Lark Bitable error: code=%d, msg=%s",
                                result.getCode(), result.getMsg())
                );
            }

            log.info("Successfully created record: recordId={}",
                    result.getData() != null && result.getData().getRecord() != null
                            ? result.getData().getRecord().getRecordId()
                            : "unknown");

            return result;

        } catch (RestClientException e) {
            log.error("Error calling Lark Bitable API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to create record in Bitable: " + e.getMessage(), e);
        }
    }
    
    /**
     * Search records trong Bitable
     */
    public BitableSearchResponse searchRecords(
            String appToken,
            String tableId,
            String userAccessToken,
            BitableSearchRequest searchRequest
    ) throws Exception {
        String url = String.format("%s/%s/tables/%s/records/search?page_size=%d", 
                BASE_URL, appToken, tableId, 
                searchRequest.getPageSize() != null ? searchRequest.getPageSize() : 500);
        
        if (searchRequest.getPageToken() != null && !searchRequest.getPageToken().isBlank()) {
            url += "&page_token=" + searchRequest.getPageToken();
        }
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);
        
        HttpEntity<BitableSearchRequest> entity = new HttpEntity<>(searchRequest, headers);
        
        log.info("🔍 Searching records in Bitable: appToken={}, tableId={}, pageSize={}", 
                maskToken(appToken), tableId, searchRequest.getPageSize());
        
        try {
            ResponseEntity<BitableSearchResponse> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    BitableSearchResponse.class
            );
            
            BitableSearchResponse result = response.getBody();
            
            if (result == null) {
                throw new RuntimeException("Empty response from Lark Bitable search API");
            }
            
            if (!result.isSuccess()) {
                log.error("Failed to search records: code={}, msg={}",
                        result.getCode(), result.getMsg());
                throw new RuntimeException(
                        String.format("Lark Bitable search error: code=%d, msg=%s",
                                result.getCode(), result.getMsg())
                );
            }
            
            int itemCount = result.getData() != null && result.getData().getItems() != null 
                    ? result.getData().getItems().size() 
                    : 0;
            log.info("✅ Found {} records (hasMore: {})", 
                    itemCount, 
                    result.getData() != null ? result.getData().getHasMore() : false);
            
            return result;
            
        } catch (RestClientException e) {
            log.error("Error calling Lark Bitable search API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to search records in Bitable: " + e.getMessage(), e);
        }
    }
    
    /**
     * Kiểm tra số điện thoại đã tồn tại trong bảng chưa (sử dụng filter API)
     * Sử dụng API search với filter để kiểm tra chính xác hơn
     * @return true nếu số điện thoại đã tồn tại, false nếu chưa hoặc có lỗi
     */
    public boolean checkPhoneExistsWithFilter(
            String appToken,
            String tableId,
            String userAccessToken,
            String phoneNumber,
            String viewId
    ) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("⚠️ Phone number is null or blank, cannot check");
            return false;
        }
        
        log.info("🔍 Checking phone with filter API: {}", phoneNumber);
        
        try {
            // Tạo filter condition đúng cấu trúc như curl API
            BitableSearchRequest.Condition condition = BitableSearchRequest.Condition.builder()
                    .fieldName("Điện thoại")
                    .operator("is")
                    .value(List.of(phoneNumber))
                    .build();
            
            BitableSearchRequest.ChildFilter childFilter = BitableSearchRequest.ChildFilter.builder()
                    .conditions(List.of(condition))
                    .conjunction("or")
                    .build();
            
            BitableSearchRequest.Filter filter = BitableSearchRequest.Filter.builder()
                    .children(List.of(childFilter))
                    .conjunction("and")
                    .build();
            
            // Tạo search request
            BitableSearchRequest searchRequest = BitableSearchRequest.builder()
                    .automaticFields(false)
                    .fieldNames(List.of("Điện thoại"))
                    .viewId(viewId)
                    .pageSize(1) // Chỉ cần kiểm tra có tồn tại không, không cần lấy tất cả
                    .filter(filter)
                    .build();
            
            BitableSearchResponse response = searchRecords(appToken, tableId, userAccessToken, searchRequest);
            
            // Kiểm tra response
            if (response.getData() != null) {
                int total = response.getData().getTotal() != null ? response.getData().getTotal() : 0;
                boolean exists = total > 0;
                
                if (exists) {
                    log.info("✅ Phone '{}' EXISTS in Bitable table (total: {})", phoneNumber, total);
                } else {
                    log.info("✅ Phone '{}' NOT found in Bitable table", phoneNumber);
                }
                
                return exists;
            }
            
            log.warn("⚠️ Empty response when checking phone: {}", phoneNumber);
            return false;
            
        } catch (Exception e) {
            log.error("❌ Error checking phone with filter: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 10) + "...";
    }
}
