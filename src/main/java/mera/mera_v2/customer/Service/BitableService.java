package mera.mera_v2.customer.Service;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import mera.mera_v2.model.BitableRecord;
import mera.mera_v2.model.BitableTable;
import mera.mera_v2.model.BitableView;
import mera.mera_v2.model.SaleSummaryRow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import mera.mera_v2.lark.token.LarkTokenService;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class BitableService {

  private static final Logger log = LoggerFactory.getLogger(BitableService.class);

  private static final String SALE_BITABLE_APP_TOKEN = "VsLjbnWlfapGXhszsvqlRm6QgIf";
  private static final String SALE_VIEW_ID = "vewE3Ope6x";

  // Bitable app/token đích cho bảng "Từ chối chăm"
  private static final String TU_CHOI_CHAM_APP_TOKEN = "Fah8bsKwQan10vsg9Q1l5LOhgsg";
  private static final String TU_CHOI_CHAM_TABLE_ID = "tblsiLRl6QUcbG8V";
  // Bitable app/token + table cho trạng thái "Đang chăm"
  private static final String DANG_CHAM_APP_TOKEN = "A9EeblIYZafN5Ys0aiNl9Phxggh";
  private static final String DANG_CHAM_TABLE_ID = "tblfjNnW3AoRzKUg";

  private static final int DEFAULT_TABLE_PAGE_SIZE = 50;
  private static final int DEFAULT_RECORD_PAGE_SIZE = 500;

  private final RestTemplate restTemplate;
  private final LarkTokenService tokenService;
  private final ObjectMapper objectMapper = new ObjectMapper();

  public BitableService(LarkTokenService tokenService) {
    this.restTemplate = new RestTemplate();
    this.tokenService = tokenService;
  }

  /** Lấy tables SALE, chỉ giữ table name có "_" */
  public List<BitableTable> getSaleTables(HttpSession session) throws Exception {
    List<BitableTable> all = listTables(session, SALE_BITABLE_APP_TOKEN, DEFAULT_TABLE_PAGE_SIZE);
    List<BitableTable> filtered = new ArrayList<>();
    for (BitableTable t : all) {
      String name = (t != null) ? t.getName() : null;
      if (name != null && name.contains("_")) filtered.add(t);
    }
    return filtered;
  }

  /** Lấy tất cả tables từ một Base ID */
  public List<BitableTable> getTablesByBaseId(HttpSession session, String baseId) throws Exception {
    if (baseId == null || baseId.isBlank()) {
      return new ArrayList<>();
    }
    return listTables(session, baseId, DEFAULT_TABLE_PAGE_SIZE);
  }

  /** Tìm kiếm records từ một table với filter tùy chỉnh */
  public List<BitableRecord> searchRecords(HttpSession session, String baseId, String tableId,
      List<String> fieldNames, String viewId, String timeRangeValue) throws Exception {
    if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank()) {
      return new ArrayList<>();
    }

    String accessToken = tokenService.getAccessToken(session, false);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    List<BitableRecord> allRecords = new ArrayList<>();
    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildSearchRecordsUrl(baseId, tableId, DEFAULT_RECORD_PAGE_SIZE, pageToken);
      
      RecordSearchRequest bodyReq = new RecordSearchRequest();
      bodyReq.automaticFields = false;
      bodyReq.fieldNames = fieldNames;
      bodyReq.viewId = viewId;

      Condition c = new Condition();
      c.fieldName = "Ngày tạo";
      c.operator = "is";
      c.value = List.of(timeRangeValue);

      Filter f = new Filter();
      f.conjunction = "and";
      f.conditions = List.of(c);

      bodyReq.filter = f;

      HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

      ResponseEntity<RecordSearchResponse> response;
      try {
        response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
      } catch (RestClientException e) {
        log.error("Error calling Bitable search records API: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to call Bitable search records API: " + e.getMessage(), e);
      }

      if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
        throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
      }

      RecordSearchResponse body = response.getBody();
      if (body.code != 0) {
        throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);
      }

      if (body.data != null && body.data.items != null) {
        allRecords.addAll(body.data.items);
      }

      hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
      pageToken = (body.data != null) ? body.data.pageToken : null;

      if (hasMore && (pageToken == null || pageToken.isBlank())) {
        log.warn("Search records has_more=true but page_token is empty. Break to avoid infinite loop.");
        break;
      }
    }

    return allRecords;
  }

  /** Lấy danh sách fields của một table */
  public List<Map<String, Object>> getFieldsByTableId(String accessToken, String baseId, String tableId) {
    String url = String.format("https://open.larksuite.com/open-apis/bitable/v1/apps/%s/tables/%s/fields?page_size=100", baseId, tableId);
    
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    
    try {
      ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.GET, new HttpEntity<>(headers), Map.class);
      if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
        Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");
        if (data != null && data.containsKey("items")) {
          return (List<Map<String, Object>>) data.get("items");
        }
      }
    } catch (Exception e) {
      log.warn("Failed to get fields for table {}: {}", tableId, e.getMessage());
    }
    return Collections.emptyList();
  }

  /** Tìm tên field thực tế dựa trên một vài gợi ý tên (case-insensitive) */
  private String detectFieldName(List<Map<String, Object>> fields, List<String> hints) {
    if (fields == null || fields.isEmpty()) return hints.get(0);
    
    for (String hint : hints) {
      for (Map<String, Object> field : fields) {
        String actualName = (String) field.get("field_name");
        if (actualName != null && actualName.equalsIgnoreCase(hint)) {
          return actualName;
        }
      }
    }
    // Fallback to first hint if not found
    return hints.get(0);
  }

  /** Tìm kiếm khách hàng theo số điện thoại (Search đa dạng) */
  public List<BitableRecord> searchCustomerByPhone(HttpSession session, String baseId, String tableId,
      String phoneNumber, String viewId) throws Exception {
    if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank() || phoneNumber == null) {
      return new ArrayList<>();
    }

    String accessToken = tokenService.getAccessToken(session, false);
    List<Map<String, Object>> actualFields = getFieldsByTableId(accessToken, baseId, tableId);
    String phoneFieldName = detectFieldName(actualFields, List.of("Điện thoại", "SĐT", "Số điện thoại", "Số Điện Thoại", "Phone"));

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    List<BitableRecord> allRecords = new ArrayList<>();
    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildSearchRecordsUrl(baseId, tableId, DEFAULT_RECORD_PAGE_SIZE, pageToken);
      
      RecordSearchRequest bodyReq = new RecordSearchRequest();
      bodyReq.automaticFields = true; // Lấy tất cả các field tự động
      if (viewId != null && !viewId.isBlank()) {
        bodyReq.viewId = viewId;
      }

      String searchPhone = phoneNumber.trim();
      if (searchPhone.length() > 9) {
          searchPhone = searchPhone.substring(searchPhone.length() - 9);
      }

      Condition c = new Condition();
      c.fieldName = phoneFieldName;
      c.operator = "contains"; // Dùng contains để linh hoạt hơn
      c.value = List.of(searchPhone);

      Filter f = new Filter();
      f.conjunction = "and";
      f.conditions = List.of(c);

      bodyReq.filter = f;

      HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

      ResponseEntity<RecordSearchResponse> response = null;
      int retries = 3;
      for (int attempt = 1; attempt <= retries; attempt++) {
        try {
          response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
          break;
        } catch (HttpClientErrorException.TooManyRequests e) {
          if (attempt == retries) {
            log.error("Rate limit (429) exceeded after {} retries for phone search: {}", retries, e.getMessage());
            return new ArrayList<>();
          }
          long waitMs = 1000L * attempt;
          log.warn("Rate limit (429) on phone search attempt {}/{}, waiting {}ms...", attempt, retries, waitMs);
          Thread.sleep(waitMs);
        } catch (HttpClientErrorException.BadRequest e) {
          String body = e.getResponseBodyAsString();
          if (body != null && body.contains("request trigger frequency limit")) {
            if (attempt == retries) {
              log.error("Rate limit (freq) exceeded after {} retries for phone search", retries);
              return new ArrayList<>();
            }
            long waitMs = 1500L * attempt;
            log.warn("Rate limit (freq) on phone search attempt {}/{}, waiting {}ms...", attempt, retries, waitMs);
            Thread.sleep(waitMs);
          } else {
            log.error("Bitable BAD_REQUEST for phone search: {}", body);
            return new ArrayList<>();
          }
        } catch (RestClientException e) {
          log.error("Error calling Bitable search customer by phone API: {}", e.getMessage(), e);
          return new ArrayList<>();
        }
      }

      if (response == null) {
        log.error("All retries failed for phone search URL: {}", url);
        return new ArrayList<>();
      }

      if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
        throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
      }

      RecordSearchResponse body = response.getBody();
      if (body.code != 0) {
        throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);
      }

      if (body.data != null && body.data.items != null) {
        allRecords.addAll(body.data.items);
      }

      hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
      pageToken = (body.data != null) ? body.data.pageToken : null;

      if (hasMore && (pageToken == null || pageToken.isBlank())) {
        log.warn("Search customer has_more=true but page_token is empty. Break to avoid infinite loop.");
        break;
      }
    }

    return allRecords;
  }

  /**
   * Tìm tất cả khách hàng có "Tên Liệu Trình" chứa "Từ chối chăm" trong 1 table CSKH.
   * Dùng view khách hàng chung, lấy đầy đủ các field cần để insert sang bảng "Từ chối chăm".
   */
  public List<BitableRecord> searchRejectedCareCustomers(HttpSession session, String baseId, String tableId,
      String viewId) throws Exception {
    return searchCareCustomersByKeyword(session, baseId, tableId, viewId, "Từ chối chăm");
  }

  /**
   * Tìm khách hàng có "Tên Liệu Trình" chứa "Đang chăm".
   */
  public List<BitableRecord> searchDangChamCustomers(HttpSession session, String baseId, String tableId,
      String viewId) throws Exception {
    return searchCareCustomersByKeyword(session, baseId, tableId, viewId, "Đang chăm");
  }

  /**
   * Tìm khách hàng theo từ khóa trong "Tên Liệu Trình".
   */
  private List<BitableRecord> searchCareCustomersByKeyword(HttpSession session, String baseId, String tableId,
      String viewId, String keyword) throws Exception {
    if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank()) {
      return new ArrayList<>();
    }

    String accessToken = tokenService.getAccessToken(session, false);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    List<BitableRecord> allRecords = new ArrayList<>();
    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildSearchRecordsUrl(baseId, tableId, DEFAULT_RECORD_PAGE_SIZE, pageToken);

      RecordSearchRequest bodyReq = new RecordSearchRequest();
      bodyReq.automaticFields = false;
      bodyReq.fieldNames = List.of(
          "Mã KH",
          "Tên khách hàng",
          "Địa chỉ",
          "Tỉnh/Thành phố",
          "Điện thoại",
          "Tên Liệu Trình",
          "Link",
          "Tuổi",
          "Bệnh nền",
          "Người CSKH",
          "Ngày tạo"
      );
      if (viewId != null && !viewId.isBlank()) {
        bodyReq.viewId = viewId;
      }

      Condition c = new Condition();
      c.fieldName = "Tên Liệu Trình";
      c.operator = "contains";
      c.value = List.of(keyword);

      Filter f = new Filter();
      f.conjunction = "and";
      f.conditions = List.of(c);

      bodyReq.filter = f;

      HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

      ResponseEntity<RecordSearchResponse> response;
      try {
        response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
      } catch (RestClientException e) {
        log.error("Error calling Bitable search rejected care customers API: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to call Bitable search rejected care customers API: " + e.getMessage(), e);
      }

      if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
        throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
      }

      RecordSearchResponse body = response.getBody();
      if (body.code != 0) {
        throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);
      }

      if (body.data != null && body.data.items != null) {
        allRecords.addAll(body.data.items);
      }

      hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
      pageToken = (body.data != null) ? body.data.pageToken : null;

      if (hasMore && (pageToken == null || pageToken.isBlank())) {
        log.warn("Search rejected care customers has_more=true but page_token is empty. Break to avoid infinite loop.");
        break;
      }
    }

    return allRecords;
  }

  /**
   * Kiểm tra trong bảng "Từ chối chăm" đích đã tồn tại bản ghi với số điện thoại này chưa.
   * Nếu đã tồn tại thì trả về true, để bỏ qua không insert trùng.
   */
  public boolean existsRejectedCareByPhone(HttpSession session, String phoneNumber) throws Exception {
    return existsCareByPhone(session, phoneNumber, TU_CHOI_CHAM_TABLE_ID, false);
  }

  /**
   * Kiểm tra trong bảng "Đang chăm" đích đã tồn tại bản ghi với số điện thoại này chưa.
   */
  public boolean existsDangChamByPhone(HttpSession session, String phoneNumber) throws Exception {
    return existsCareByPhone(session, phoneNumber, DANG_CHAM_TABLE_ID, false);
  }

  private boolean existsCareByPhone(HttpSession session, String phoneNumber, String targetTableId, boolean isRetry) throws Exception {
    if (phoneNumber == null || phoneNumber.isBlank()) {
      return false;
    }

    String accessToken = tokenService.getAccessToken(session, isRetry); // Nếu là retry thì force refresh

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    String url = buildSearchRecordsUrl(resolveAppTokenForTarget(targetTableId), targetTableId, 1, null);

    RecordSearchRequest bodyReq = new RecordSearchRequest();
    bodyReq.automaticFields = false;
    bodyReq.fieldNames = List.of("Điện thoại");

    Condition c = new Condition();
    c.fieldName = "Điện thoại";
    c.operator = "is";
    c.value = List.of(phoneNumber.trim());

    Filter f = new Filter();
    f.conjunction = "and";
    f.conditions = List.of(c);

    bodyReq.filter = f;

    HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

    ResponseEntity<RecordSearchResponse> response;
    try {
      response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
    } catch (HttpClientErrorException e) {
      if (!isRetry && (e.getStatusCode() == HttpStatus.BAD_REQUEST || e.getStatusCode() == HttpStatus.UNAUTHORIZED)) {
        String responseBody = e.getResponseBodyAsString();
        if (responseBody != null && (responseBody.contains("Invalid access token") || responseBody.contains("99991668"))) {
          log.warn("Token expired in existsCareByPhone, refreshing and retrying...");
          return existsCareByPhone(session, phoneNumber, targetTableId, true);
        }
      }
      log.error("Error calling Bitable existsRejectedCareByPhone API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to call Bitable existsRejectedCareByPhone API: " + e.getMessage(), e);
    } catch (RestClientException e) {
      log.error("Error calling Bitable existsRejectedCareByPhone API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to call Bitable existsRejectedCareByPhone API: " + e.getMessage(), e);
    }

    if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
      if (!isRetry && (response.getStatusCode() == HttpStatus.BAD_REQUEST || response.getStatusCode() == HttpStatus.UNAUTHORIZED)) {
        log.warn("Token expired in existsCareByPhone (status check), refreshing and retrying...");
        return existsCareByPhone(session, phoneNumber, targetTableId, true);
      }
      throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
    }

    RecordSearchResponse body = response.getBody();
    if (body.code != 0) {
      if (!isRetry && (body.code == 99991668 || (body.msg != null && body.msg.contains("Invalid access token")))) {
        log.warn("Token expired in existsCareByPhone (code check), refreshing and retrying...");
        return existsCareByPhone(session, phoneNumber, targetTableId, true);
      }
      throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);
    }

    return body.data != null && body.data.items != null && !body.data.items.isEmpty();
  }

  /** Tìm kiếm trao đổi hoặc lịch hẹn theo record_id của khách hàng */
  public List<BitableRecord> searchRecordsByCustomerId(HttpSession session, String baseId, String tableId,
      String customerRecordId, List<String> fieldNames, String viewId) throws Exception {
    if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank() 
        || customerRecordId == null || customerRecordId.isBlank()) {
      return new ArrayList<>();
    }

    String accessToken = tokenService.getAccessToken(session, false);
    List<Map<String, Object>> actualFields = getFieldsByTableId(accessToken, baseId, tableId);
    String customerLinkFieldName = detectFieldName(actualFields, List.of("Khách Hàng", "Khách hàng", "Customer", "Mã khách hàng", "Mã KH"));
    log.info("BaseId={} - TD table fields: detectedLinkField='{}', allFields={}", baseId, customerLinkFieldName,
            actualFields.stream().map(f -> f.get("field_name")).collect(Collectors.toList()));
    log.info("BaseId={} - Searching TD with customerRecordId='{}', viewId='{}'", baseId, customerRecordId, viewId);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    List<BitableRecord> allRecords = new ArrayList<>();
    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildSearchRecordsUrl(baseId, tableId, DEFAULT_RECORD_PAGE_SIZE, pageToken);
      
      RecordSearchRequest bodyReq = new RecordSearchRequest();
      bodyReq.automaticFields = true; // Tự động lấy các field
      if (viewId != null && !viewId.isBlank()) {
        bodyReq.viewId = viewId;
      }

      Condition c = new Condition();
      c.fieldName = customerLinkFieldName;
      c.operator = "is";
      c.value = List.of(customerRecordId);

      Filter f = new Filter();
      f.conjunction = "and";
      f.conditions = List.of(c);

      bodyReq.filter = f;

      log.info("BaseId={} - TD API request: url={}, filterField='{}', filterValue='{}', viewId='{}', pageToken='{}'",
              baseId, url, customerLinkFieldName, customerRecordId, viewId, pageToken);

      HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

      ResponseEntity<RecordSearchResponse> response = null;
      int retries = 3;
      for (int attempt = 1; attempt <= retries; attempt++) {
        try {
          response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
          break;
        } catch (HttpClientErrorException.TooManyRequests e) {
          if (attempt == retries) {
            log.error("Rate limit (429) exceeded after {} retries for TD search base {}: {}", retries, baseId, e.getMessage());
            throw new RuntimeException("Rate limit exceeded for TD search", e);
          }
          long waitMs = 1500L * attempt;
          log.warn("Rate limit (429) on TD search base {} attempt {}/{}, waiting {}ms...", baseId, attempt, retries, waitMs);
          Thread.sleep(waitMs);
        } catch (HttpClientErrorException.BadRequest e) {
          String body = e.getResponseBodyAsString();
          if (body != null && body.contains("request trigger frequency limit")) {
            if (attempt == retries) {
              log.error("Rate limit (freq) exceeded after {} retries for TD search base {}", retries, baseId);
              throw new RuntimeException("Rate limit exceeded for TD search", e);
            }
            long waitMs = 2000L * attempt;
            log.warn("Rate limit (freq) on TD search base {} attempt {}/{}, waiting {}ms...", baseId, attempt, retries, waitMs);
            Thread.sleep(waitMs);
          } else {
            log.error("Bitable BAD_REQUEST for TD search base {}: {}", baseId, body);
            throw new RuntimeException("Bitable BAD_REQUEST for TD search: " + body, e);
          }
        } catch (RestClientException e) {
          log.error("Error calling Bitable search records by customer ID for base {}: {}", baseId, e.getMessage(), e);
          throw new RuntimeException("Failed to call Bitable search records API for base " + baseId + ": " + e.getMessage(), e);
        }
      }

      if (response == null) {
        log.error("All retries failed for TD search base {} URL: {}", baseId, url);
        throw new RuntimeException("Rate limit exceeded for TD search");
      }

      if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
        throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
      }

      RecordSearchResponse body = response.getBody();
      if (body.code != 0) {
        throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);
      }

      log.info("BaseId={} - TD API response: code={}, msg='{}', items={}, hasMore={}",
              baseId, body.code, body.msg,
              body.data != null && body.data.items != null ? body.data.items.size() : 0,
              body.data != null ? body.data.hasMore : false);

      if (body.data != null && body.data.items != null) {
        allRecords.addAll(body.data.items);
      }

      hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
      pageToken = (body.data != null) ? body.data.pageToken : null;

      if (hasMore && (pageToken == null || pageToken.isBlank())) {
        log.warn("Search records by customer ID has_more=true but page_token is empty. Break to avoid infinite loop.");
        break;
      }
    }

    return allRecords;
  }

  /** ✅ Build summary theo 1 table (đếm status trong lúc search + paginate) */
  public SaleSummaryRow buildSaleSummaryForTable(HttpSession session, BitableTable table, String timeRangeValue)
      throws Exception {

    String accessToken = tokenService.getAccessToken(session, false);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    long nhuCau = 0, trung = 0, rac = 0, khongTuongTac = 0;
    long chotNong = 0, chotCu = 0, donHuy = 0;
    long tongMess = 0;
    long tongDon = 0;

    String tableId = table.getTableId();
    String tableName = table.getName();

    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildSearchRecordsUrl(SALE_BITABLE_APP_TOKEN, tableId, DEFAULT_RECORD_PAGE_SIZE, pageToken);
      RecordSearchRequest bodyReq = RecordSearchRequest.forSale(SALE_VIEW_ID, timeRangeValue);

      HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

      ResponseEntity<RecordSearchResponse> response;
      try {
        response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
      } catch (RestClientException e) {
        log.error("Error calling Bitable search records API: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to call Bitable search records API: " + e.getMessage(), e);
      }

      if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
        throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
      }

      RecordSearchResponse body = response.getBody();
      if (body.code != 0) {
        throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);
      }

      List<BitableRecord> items = (body.data != null) ? body.data.items : null;
      if (items != null) {
        for (BitableRecord r : items) {
          Map<String, Object> fields = r.getFields();
          Object rawStatus = (fields != null) ? fields.get("Trạng thái mess") : null;
          String status = normalizeStatus(extractText(rawStatus));

          if (status.isEmpty()) continue;

          // buckets
          if (status.contains("nhu cầu")) nhuCau++;
          else if (status.contains("trùng")) trung++;
          else if (status.contains("rác")) rac++;
          else if (status.contains("không tương tác")) khongTuongTac++;
          else if (status.contains("chốt nóng")) chotNong++;
          else if (status.contains("chốt cũ")) chotCu++;
          else if (status.contains("đơn hủy") || status.contains("đơn huỷ")) donHuy++;
        }
      }

      hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
      pageToken = (body.data != null) ? body.data.pageToken : null;

      if (hasMore && (pageToken == null || pageToken.isBlank())) {
        log.warn("Search records has_more=true but page_token is empty. Break to avoid infinite loop.");
        break;
      }
    }

    SaleSummaryRow row = new SaleSummaryRow();
    row.setTableName(tableName);
    row.setTableId(tableId);

    row.setNhuCau(nhuCau);
    row.setTrung(trung);
    row.setRac(rac);
    row.setKhongTuongTac(khongTuongTac);

    row.setChotNong(chotNong);
    row.setChotCu(chotCu);

    row.setDonHuy(donHuy);

    // Theo yêu cầu: Tổng mess = Nhu cầu + Trùng + Rác + Không tương tác + Chốt nóng + Chốt cũ + Đơn hủy
    tongMess = nhuCau + trung + rac + khongTuongTac + chotNong + chotCu + donHuy;
    // Theo yêu cầu: Tổng đơn = Chốt nóng + Chốt cũ
    tongDon = chotNong + chotCu;

    row.setTongMess(tongMess);
    row.setTongDon(tongDon);

    // Đơn/mess nhu cầu = Tổng đơn / (Nhu cầu + Chốt nóng + Chốt cũ + Hủy)
    // -> backend chỉ giữ Tỷ Lệ (0.x), phần trăm và ký hiệu % xử lý ở frontend
    long nhuCauDenominator = nhuCau + chotNong + chotCu + donHuy;
    double donPerMessNhuCau = (nhuCauDenominator == 0)
        ? 0.0
        : ((long)(((double) tongDon / (double) nhuCauDenominator)*100 * 10 + 0.5)) / 10.0;
    row.setDonPerMessNhuCau(donPerMessNhuCau);

    // Đơn/mess tổng = Tổng đơn / Tổng mess (cũng giữ dạng tỷ lệ 0.x)
    double donPerMessTong = (tongMess == 0)
        ? 0.0
        : ((long)(((double) tongDon / (double) tongMess)*100 * 10 + 0.5)) / 10.0;
//    System.out.println("check :" +donPerMessTong);
    row.setDonPerMessTong(donPerMessTong);

    double tiLeHuy = (tongDon == 0) ? 0 : ((long)(((double) donHuy / (double) tongDon)*100 * 10 + 0.5)) / 10.0;
    row.setTiLeHuyPercent(tiLeHuy);

    return row;
  }

  // ================== list tables ==================

  private List<BitableTable> listTables(HttpSession session, String appToken, int pageSize) throws Exception {
    String accessToken = tokenService.getAccessToken(session, false);

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    HttpEntity<Void> entity = new HttpEntity<>(headers);

    List<BitableTable> all = new ArrayList<>();
    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildListTablesUrl(appToken, pageSize, pageToken);

      try {
        ResponseEntity<BitableTablesResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, BitableTablesResponse.class
        );

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
          throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
        }

        BitableTablesResponse body = response.getBody();
        if (body.code != 0) throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);

        if (body.data != null && body.data.items != null) all.addAll(body.data.items);

        hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
        pageToken = (body.data != null) ? body.data.pageToken : null;

        if (hasMore && (pageToken == null || pageToken.isBlank())) break;

      } catch (HttpClientErrorException e) {
        // Nếu lỗi 400 (Bad Request) hoặc 403 (Forbidden) thì thường là do Base bị lỗi hoặc không có quyền.
        // Chỉ log warn để tránh làm rối log hệ thống khi quét qua nhiều bases.
        log.warn("ℹ️ Cannot list tables for base {}: {} (Code: {})", appToken, e.getStatusText(), e.getStatusCode());
        return Collections.emptyList();
      } catch (RestClientException e) {
        log.error("❌ Unexpected error calling Bitable list tables API for base {}: {}", appToken, e.getMessage());
        return Collections.emptyList();
      }
    }
    return all;
  }

  private String buildListTablesUrl(String appToken, int pageSize, String pageToken) {
    String base = "https://open.larksuite.com/open-apis/bitable/v1/apps/" + appToken + "/tables";
    String url = base + "?page_size=" + pageSize;
    if (pageToken != null && !pageToken.isBlank()) url += "&page_token=" + pageToken;
    return url;
  }

  // ================== create record ==================

  /**
   * Tạo bản ghi mới trong bảng "Từ chối chăm" đích, dùng access token của user hiện tại.
   * fields phải theo đúng structure của Bitable (Map lồng nhau, list, v.v.).
   */
  public void createRejectedCareRecord(HttpSession session, Map<String, Object> fields) throws Exception {
    if (fields == null || fields.isEmpty()) {
      return;
    }

    createCareRecordWithRetry(session, fields, TU_CHOI_CHAM_TABLE_ID, false);
  }

  /**
   * Tạo bản ghi mới trong bảng "Đang chăm".
   */
  public void createDangChamRecord(HttpSession session, Map<String, Object> fields) throws Exception {
    if (fields == null || fields.isEmpty()) {
      return;
    }

    createCareRecordWithRetry(session, fields, DANG_CHAM_TABLE_ID, false);
  }

  private void createCareRecordWithRetry(HttpSession session, Map<String, Object> fields, String targetTableId, boolean isRetry) throws Exception {
    String accessToken = tokenService.getAccessToken(session, isRetry); // Nếu là retry thì force refresh

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    Map<String, Object> body = new HashMap<>();
    body.put("fields", fields);

    String url = buildCreateRecordUrl(resolveAppTokenForTarget(targetTableId), targetTableId);

    // In ra curl tương đương để debug
    try {
      String jsonBody = objectMapper.writeValueAsString(body);
      String escapedJson = jsonBody.replace("'", "\\'");
      String curl = "curl -i -X POST '" + url + "' \\\n"
          + " -H 'Content-Type: application/json' \\\n"
          + " -H 'Authorization: Bearer " + accessToken + "' \\\n"
          + " -d '" + escapedJson + "'";
      log.info("CURL create rejected care record:\n{}", curl);
    } catch (JsonProcessingException e) {
      log.warn("Failed to serialize body for curl log: {}", e.getMessage());
    }

    HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<CreateRecordResponse> response;
    try {
      response = restTemplate.exchange(url, HttpMethod.POST, entity, CreateRecordResponse.class);
    } catch (HttpClientErrorException e) {
      if (!isRetry && (e.getStatusCode() == HttpStatus.BAD_REQUEST || e.getStatusCode() == HttpStatus.UNAUTHORIZED)) {
        String responseBody = e.getResponseBodyAsString();
        if (responseBody != null && (responseBody.contains("Invalid access token") || responseBody.contains("99991668"))) {
          log.warn("Token expired in createCareRecord, refreshing and retrying...");
          createCareRecordWithRetry(session, fields, targetTableId, true);
          return;
        }
      }
      log.error("Error calling Bitable create rejected care record API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to call Bitable create rejected care record API: " + e.getMessage(), e);
    } catch (RestClientException e) {
      log.error("Error calling Bitable create rejected care record API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to call Bitable create rejected care record API: " + e.getMessage(), e);
    }

    if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
      if (!isRetry && (response.getStatusCode() == HttpStatus.BAD_REQUEST || response.getStatusCode() == HttpStatus.UNAUTHORIZED)) {
        log.warn("Token expired in createCareRecord (status check), refreshing and retrying...");
        createCareRecordWithRetry(session, fields, targetTableId, true);
        return;
      }
      throw new RuntimeException("Bitable HTTP error when creating record: " + response.getStatusCode());
    }

    CreateRecordResponse bodyRes = response.getBody();
    if (bodyRes.code != 0) {
      if (!isRetry && (bodyRes.code == 99991668 || (bodyRes.msg != null && bodyRes.msg.contains("Invalid access token")))) {
        log.warn("Token expired in createCareRecord (code check), refreshing and retrying...");
        createCareRecordWithRetry(session, fields, targetTableId, true);
        return;
      }
      throw new RuntimeException("Bitable error when creating record: " + bodyRes.code + " - " + bodyRes.msg);
    }
  }

  private static class BitableTablesResponse {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") TablesData data;
  }

  private static class TablesData {
    @JsonProperty("items") List<BitableTable> items;
    @JsonProperty("page_token") String pageToken;
    @JsonProperty("has_more") Boolean hasMore;
  }

  // ================== list views ==================

  /** Lấy danh sách views của một table. Trả về view đầu tiên (grid view). */
  public String getDefaultViewId(HttpSession session, String baseId, String tableId) throws Exception {
    List<BitableView> views = listViews(session, baseId, tableId);
    if (views.isEmpty()) {
      log.warn("BaseId={} TableId={} - Khong lay duoc view nao", baseId, tableId);
      return null;
    }
    String vid = views.get(0).getViewId();
    log.info("BaseId={} TableId={} - view_id resolved: {}", baseId, tableId, vid);
    return vid;
  }

  /** Raw JSON response tu API list views - dung de debug */
  public String getViewsJson(String baseId, String tableId) throws Exception {
    String accessToken = tokenService.getAccessToken(null, false);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    String url = buildListViewsUrl(baseId, tableId, null);
    ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
    return response.getBody();
  }

  private List<BitableView> listViews(HttpSession session, String baseId, String tableId) throws Exception {
    String accessToken = tokenService.getAccessToken(session, false);
    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
    HttpEntity<Void> entity = new HttpEntity<>(headers);

    List<BitableView> all = new ArrayList<>();
    String pageToken = null;
    boolean hasMore = true;

    while (hasMore) {
      String url = buildListViewsUrl(baseId, tableId, pageToken);

      try {
        ResponseEntity<BitableViewsResponse> response = restTemplate.exchange(
            url, HttpMethod.GET, entity, BitableViewsResponse.class);

        if (response.getStatusCode() != HttpStatus.OK || response.getBody() == null) {
          throw new RuntimeException("Bitable HTTP error: " + response.getStatusCode());
        }

        BitableViewsResponse body = response.getBody();
        if (body.code != 0) throw new RuntimeException("Bitable error: " + body.code + " - " + body.msg);

        if (body.data != null && body.data.items != null) all.addAll(body.data.items);

        hasMore = body.data != null && Boolean.TRUE.equals(body.data.hasMore);
        pageToken = (body.data != null) ? body.data.pageToken : null;

        if (hasMore && (pageToken == null || pageToken.isBlank())) break;

      } catch (HttpClientErrorException e) {
        log.warn("Cannot list views for table {} in base {}: {} ({})", tableId, baseId, e.getStatusText(), e.getStatusCode());
        return Collections.emptyList();
      } catch (RestClientException e) {
        log.warn("Unexpected error listing views for table {} in base {}: {}", tableId, baseId, e.getMessage());
        return Collections.emptyList();
      }
    }
    return all;
  }

  private String buildListViewsUrl(String baseId, String tableId, String pageToken) {
    String url = String.format("https://open.larksuite.com/open-apis/bitable/v1/apps/%s/tables/%s/views?page_size=100", baseId, tableId);
    if (pageToken != null && !pageToken.isBlank()) url += "&page_token=" + pageToken;
    return url;
  }

  private static class BitableViewsResponse {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") ViewsData data;
  }

  private static class ViewsData {
    @JsonProperty("items") List<BitableView> items;
    @JsonProperty("page_token") String pageToken;
    @JsonProperty("has_more") Boolean hasMore;
  }

  // ================== search records ==================

  private String buildSearchRecordsUrl(String appToken, String tableId, int pageSize, String pageToken) {
    String base = "https://open.larksuite.com/open-apis/bitable/v1/apps/" + appToken
        + "/tables/" + tableId + "/records/search";
    String url = base + "?page_size=" + pageSize;
    if (pageToken != null && !pageToken.isBlank()) url += "&page_token=" + pageToken;
    return url;
  }

  private String buildCreateRecordUrl(String appToken, String tableId) {
    return "https://open.larksuite.com/open-apis/bitable/v1/apps/" + appToken
        + "/tables/" + tableId + "/records";
  }

  private String resolveAppTokenForTarget(String tableId) {
    if (tableId != null && tableId.equals(DANG_CHAM_TABLE_ID)) {
      return DANG_CHAM_APP_TOKEN;
    }
    return TU_CHOI_CHAM_APP_TOKEN;
  }

  private static class RecordSearchResponse {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") RecordSearchData data;
  }

  private static class RecordSearchData {
    @JsonProperty("items") List<BitableRecord> items;
    @JsonProperty("page_token") String pageToken;
    @JsonProperty("has_more") Boolean hasMore;
  }

  private static class RecordSearchRequest {
    @JsonProperty("automatic_fields") boolean automaticFields;
    @JsonProperty("field_names") List<String> fieldNames;
    @JsonProperty("filter") Filter filter;
    @JsonProperty("view_id") String viewId;

    static RecordSearchRequest forSale(String viewId, String timeRangeValue) {
      RecordSearchRequest req = new RecordSearchRequest();
      req.automaticFields = false;
      req.fieldNames = List.of("Ngày tạo", "Điện Thoại", "Trạng thái mess");
      req.viewId = viewId;

      Condition c = new Condition();
      c.fieldName = "Ngày tạo";
      c.operator = "is";
      c.value = List.of(timeRangeValue);

      Filter f = new Filter();
      f.conjunction = "and";
      f.conditions = List.of(c);

      req.filter = f;
      return req;
    }
  }

  private static class Filter {
    @JsonProperty("conditions") List<Condition> conditions;
    @JsonProperty("conjunction") String conjunction;
  }

  private static class Condition {
    @JsonProperty("field_name") String fieldName;
    @JsonProperty("operator") String operator;
    @JsonProperty("value") List<String> value;
  }

  private static class CreateRecordResponse {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
  }

  // ================== helpers ==================

  private String normalizeStatus(String s) {
    if (s == null) return "";
    return s.trim().toLowerCase();
  }

  private String extractText(Object v) {
    if (v == null) return "";
    if (v instanceof String s) return s;
    if (v instanceof List<?> list) {
        java.util.stream.Stream<String> stream = list.stream().map(this::extractText).filter(s -> !s.isBlank());
        return stream.collect(java.util.stream.Collectors.joining(", "));
    }
    if (v instanceof java.util.Map<?, ?> map) {
        Object text = map.get("text");
        if (text != null) return String.valueOf(text);
        Object name = map.get("name");
        if (name != null) return String.valueOf(name);
        return map.toString();
    }
    return String.valueOf(v);
  }

  private double round2(double x) {
    return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }
}