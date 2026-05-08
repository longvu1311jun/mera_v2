package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.dto.BitableBatchUpdateRequest;
import mera.mera_v2.lark.webhook.dto.BitableRecordRequest;
import mera.mera_v2.lark.webhook.dto.BitableRecordResponse;
import mera.mera_v2.lark.webhook.dto.BitableSearchRequest;
import mera.mera_v2.lark.webhook.dto.BitableSearchResponse;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import mera.mera_v2.lark.token.LarkTokenService;
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
    
    /**
     * Kiểm tra số điện thoại đã tồn tại trong bảng chưa
     * @param appToken Base ID
     * @param tableId Table ID
     * @param userAccessToken User access token
     * @param phoneNumber Số điện thoại cần kiểm tra
     * @param viewId View ID (optional, có thể null)
     * @return true nếu số điện thoại đã tồn tại, false nếu chưa
     */
    public boolean checkPhoneExists(
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
        
        log.info("🔍 Checking if phone number exists: {}", phoneNumber);
        
        try {
            // Normalize số điện thoại để so sánh
            String normalizedPhone = normalizePhoneNumber(phoneNumber);
            
            // Tạo search request
            BitableSearchRequest searchRequest = BitableSearchRequest.builder()
                    .automaticFields(false)
                    .fieldNames(Arrays.asList("Điện thoại"))
                    .viewId(viewId)
                    .pageSize(500)
                    .build();
            
            Set<String> existingPhones = new HashSet<>();
            String pageToken = null;
            int totalChecked = 0;
            
            // Lặp lại cho đến khi không còn page nào
            do {
                if (pageToken != null) {
                    searchRequest.setPageToken(pageToken);
                }
                
                BitableSearchResponse response = searchRecords(appToken, tableId, userAccessToken, searchRequest);
                
                if (response.getData() != null && response.getData().getItems() != null) {
                    // Lấy tất cả số điện thoại từ response
                    for (BitableSearchResponse.SearchItem item : response.getData().getItems()) {
                        if (item.getFields() != null) {
                            Object phoneField = item.getFields().get("Điện thoại");
                            if (phoneField != null) {
                                String existingPhone = phoneField.toString();
                                String normalizedExisting = normalizePhoneNumber(existingPhone);
                                existingPhones.add(normalizedExisting);
                            }
                        }
                    }
                    
                    totalChecked += response.getData().getItems().size();
                    log.debug("   Checked {} records, found {} unique phones", 
                            totalChecked, existingPhones.size());
                }
                
                // Kiểm tra xem có page tiếp theo không
                boolean hasMore = response.getData() != null && 
                        Boolean.TRUE.equals(response.getData().getHasMore());
                pageToken = hasMore && response.getData() != null 
                        ? response.getData().getPageToken() 
                        : null;
                
            } while (pageToken != null && !pageToken.isBlank());
            
            boolean exists = existingPhones.contains(normalizedPhone);
            log.info("✅ Phone check completed: checked {} records, phone '{}' exists: {}", 
                    totalChecked, phoneNumber, exists);
            
            return exists;
            
        } catch (Exception e) {
            log.error("❌ Error checking phone existence: {}", e.getMessage(), e);
            // Nếu có lỗi khi check, cho phép tạo bản ghi để tránh block flow
            return false;
        }
    }
    
    /**
     * Normalize số điện thoại: loại bỏ khoảng trắng, dấu gạch ngang, dấu ngoặc đơn
     * Ví dụ: "0961 253 819" -> "0961253819", "0961-253-819" -> "0961253819"
     */
    private String normalizePhoneNumber(String phone) {
        if (phone == null || phone.isBlank()) {
            return "";
        }
        // Loại bỏ tất cả ký tự không phải số
        return phone.replaceAll("[^0-9]", "");
    }
    
    /**
     * Xóa record trong Bitable
     */
    public void deleteRecord(
            String appToken,
            String tableId,
            String recordId,
            String userAccessToken
    ) throws Exception {
        String url = String.format("%s/%s/tables/%s/records/%s", 
                BASE_URL, appToken, tableId, recordId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(userAccessToken);
        
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        
        log.info("🗑️ Deleting record in Bitable: appToken={}, tableId={}, recordId={}", 
                maskToken(appToken), tableId, recordId);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.DELETE,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            // Kiểm tra response
            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");
                if (code != null && code != 0) {
                    String msg = (String) body.get("msg");
                    log.error("Failed to delete record: code={}, msg={}", code, msg);
                    throw new RuntimeException(
                            String.format("Lark Bitable delete error: code=%d, msg=%s", code, msg)
                    );
                }
            }
            
            log.info("✅ Successfully deleted record: recordId={}", recordId);
            
        } catch (RestClientException e) {
            log.error("Error calling Lark Bitable delete API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete record in Bitable: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tìm record ID bằng số điện thoại
     * @param appToken Base ID
     * @param tableId Table ID
     * @param userAccessToken User access token
     * @param phoneNumber Số điện thoại cần tìm
     * @param viewId View ID (optional, có thể null)
     * @return Record ID nếu tìm thấy, null nếu không tìm thấy
     */
    public String findRecordIdByPhone(
            String appToken,
            String tableId,
            String userAccessToken,
            String phoneNumber,
            String viewId
    ) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            log.warn("⚠️ Phone number is null or blank, cannot search");
            return null;
        }
        
        log.info("🔍 Searching for record by phone number: {}", phoneNumber);
        
        try {
            // Tạo filter condition
            BitableSearchRequest.Condition condition = BitableSearchRequest.Condition.builder()
                    .fieldName("Điện thoại")
                    .operator("is")
                    .value(List.of(phoneNumber))
                    .build();
            
            BitableSearchRequest.Filter filter = BitableSearchRequest.Filter.builder()
                    .conjunction("and")
                    .conditions(List.of(condition))
                    .build();
            
            // Tạo search request
            BitableSearchRequest searchRequest = BitableSearchRequest.builder()
                    .automaticFields(false)
                    .fieldNames(List.of("Điện thoại"))
                    .viewId(viewId)
                    .pageSize(500)
                    .filter(filter)
                    .build();
            
            BitableSearchResponse response = searchRecords(appToken, tableId, userAccessToken, searchRequest);
            
            if (response.getData() != null && response.getData().getItems() != null 
                    && !response.getData().getItems().isEmpty()) {
                // Lấy record ID đầu tiên
                String recordId = response.getData().getItems().get(0).getRecordId();
                log.info("✅ Found record ID: {} for phone number: {}", recordId, phoneNumber);
                return recordId;
            }
            
            log.warn("⚠️ No record found for phone number: {}", phoneNumber);
            return null;
            
        } catch (Exception e) {
            log.error("❌ Error searching for record by phone: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Batch update records trong Bitable
     */
    public void batchUpdateRecords(
            String appToken,
            String tableId,
            String userAccessToken,
            BitableBatchUpdateRequest batchUpdateRequest
    ) throws Exception {
        String url = String.format("%s/%s/tables/%s/records/batch_update", 
                BASE_URL, appToken, tableId);
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(userAccessToken);
        
        HttpEntity<BitableBatchUpdateRequest> entity = new HttpEntity<>(batchUpdateRequest, headers);
        
        log.info("🔄 Batch updating records in Bitable: appToken={}, tableId={}, recordCount={}", 
                maskToken(appToken), tableId, 
                batchUpdateRequest.getRecords() != null ? batchUpdateRequest.getRecords().size() : 0);
        
        try {
            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    new ParameterizedTypeReference<Map<String, Object>>() {}
            );
            
            // Kiểm tra response
            if (response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");
                if (code != null && code != 0) {
                    String msg = (String) body.get("msg");
                    log.error("Failed to batch update records: code={}, msg={}", code, msg);
                    throw new RuntimeException(
                            String.format("Lark Bitable batch update error: code=%d, msg=%s", code, msg)
                    );
                }
            }
            
            log.info("✅ Successfully batch updated records");
            
        } catch (RestClientException e) {
            log.error("Error calling Lark Bitable batch update API: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to batch update records in Bitable: " + e.getMessage(), e);
        }
    }
    
    /**
     * Tìm record ID bằng số điện thoại trong một table cụ thể
     * Sử dụng field "Điện Thoại" (chữ hoa) và view ID cố định
     */
    public String findRecordIdByPhoneInTable(
            String appToken,
            String tableId,
            String userAccessToken,
            String phoneNumber,
            String viewId
    ) {
        if (phoneNumber == null || phoneNumber.isBlank()) {
            return null;
        }
        
        try {
            // Tạo filter condition với field "Điện Thoại" (chữ hoa)
            BitableSearchRequest.Condition condition = BitableSearchRequest.Condition.builder()
                    .fieldName("Điện Thoại")
                    .operator("is")
                    .value(List.of(phoneNumber))
                    .build();
            
            BitableSearchRequest.Filter filter = BitableSearchRequest.Filter.builder()
                    .conjunction("and")
                    .conditions(List.of(condition))
                    .build();
            
            // Tạo search request
            BitableSearchRequest searchRequest = BitableSearchRequest.builder()
                    .automaticFields(false)
                    .fieldNames(List.of("Điện Thoại"))
                    .viewId(viewId)
                    .pageSize(500)
                    .filter(filter)
                    .build();
            
            BitableSearchResponse response = searchRecords(appToken, tableId, userAccessToken, searchRequest);
            
            if (response.getData() != null && response.getData().getItems() != null 
                    && !response.getData().getItems().isEmpty()) {
                // Lấy record ID đầu tiên
                String recordId = response.getData().getItems().get(0).getRecordId();
                log.debug("✅ Found record ID: {} in table {} for phone: {}", recordId, tableId, phoneNumber);
                return recordId;
            }
            
            return null;
            
        } catch (Exception e) {
            log.debug("❌ Error searching for record in table {}: {}", tableId, e.getMessage());
            return null;
        }
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 10) + "...";
    }
}
