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
import mera.mera_v2.model.BitableRecord;
import mera.mera_v2.model.BitableTable;
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

  // Bitable app/token Ä‘Ã­ch cho báº£ng "Tá»« chá»‘i chÄƒm"
  private static final String TU_CHOI_CHAM_APP_TOKEN = "Fah8bsKwQan10vsg9Q1l5LOhgsg";
  private static final String TU_CHOI_CHAM_TABLE_ID = "tblsiLRl6QUcbG8V";
  // Bitable app/token + table cho tráº¡ng thÃ¡i "Äang chÄƒm"
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

  /** Láº¥y tables SALE, chá»‰ giá»¯ table name cÃ³ "_" */
  public List<BitableTable> getSaleTables(HttpSession session) throws Exception {
    List<BitableTable> all = listTables(session, SALE_BITABLE_APP_TOKEN, DEFAULT_TABLE_PAGE_SIZE);
    List<BitableTable> filtered = new ArrayList<>();
    for (BitableTable t : all) {
      String name = (t != null) ? t.getName() : null;
      if (name != null && name.contains("_")) filtered.add(t);
    }
    return filtered;
  }

  /** Láº¥y táº¥t cáº£ tables tá»« má»™t Base ID */
  public List<BitableTable> getTablesByBaseId(HttpSession session, String baseId) throws Exception {
    if (baseId == null || baseId.isBlank()) {
      return new ArrayList<>();
    }
    return listTables(session, baseId, DEFAULT_TABLE_PAGE_SIZE);
  }

  /** TÃ¬m kiáº¿m records tá»« má»™t table vá»›i filter tÃ¹y chá»‰nh */
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
      c.fieldName = "NgÃ y táº¡o";
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

  /** TÃ¬m kiáº¿m khÃ¡ch hÃ ng theo sá»‘ Ä‘iá»‡n thoáº¡i */
  public List<BitableRecord> searchCustomerByPhone(HttpSession session, String baseId, String tableId,
      String phoneNumber, String viewId) throws Exception {
    if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank() 
        || phoneNumber == null || phoneNumber.isBlank()) {
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
          "Äiá»‡n thoáº¡i",
          "TÃªn khÃ¡ch hÃ ng",
          "MÃ£ KH",
          "Tuá»•i",
          "Äá»‹a chá»‰",
          "Link",
          "TÃªn Liá»‡u TrÃ¬nh",
          "Bá»‡nh ná»n"
      );
      if (viewId != null && !viewId.isBlank()) {
        bodyReq.viewId = viewId;
      }

      Condition c = new Condition();
      c.fieldName = "Äiá»‡n thoáº¡i";
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
      } catch (RestClientException e) {
        log.error("Error calling Bitable search customer by phone API: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to call Bitable search customer API: " + e.getMessage(), e);
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
   * TÃ¬m táº¥t cáº£ khÃ¡ch hÃ ng cÃ³ "TÃªn Liá»‡u TrÃ¬nh" chá»©a "Tá»« chá»‘i chÄƒm" trong 1 table CSKH.
   * DÃ¹ng view khÃ¡ch hÃ ng chung, láº¥y Ä‘áº§y Ä‘á»§ cÃ¡c field cáº§n Ä‘á»ƒ insert sang báº£ng "Tá»« chá»‘i chÄƒm".
   */
  public List<BitableRecord> searchRejectedCareCustomers(HttpSession session, String baseId, String tableId,
      String viewId) throws Exception {
    return searchCareCustomersByKeyword(session, baseId, tableId, viewId, "Tá»« chá»‘i chÄƒm");
  }

  /**
   * TÃ¬m khÃ¡ch hÃ ng cÃ³ "TÃªn Liá»‡u TrÃ¬nh" chá»©a "Äang chÄƒm".
   */
  public List<BitableRecord> searchDangChamCustomers(HttpSession session, String baseId, String tableId,
      String viewId) throws Exception {
    return searchCareCustomersByKeyword(session, baseId, tableId, viewId, "Äang chÄƒm");
  }

  /**
   * TÃ¬m khÃ¡ch hÃ ng theo tá»« khÃ³a trong "TÃªn Liá»‡u TrÃ¬nh".
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
          "MÃ£ KH",
          "TÃªn khÃ¡ch hÃ ng",
          "Äá»‹a chá»‰",
          "Tá»‰nh/ThÃ nh phá»‘",
          "Äiá»‡n thoáº¡i",
          "TÃªn Liá»‡u TrÃ¬nh",
          "Link",
          "Tuá»•i",
          "Bá»‡nh ná»n",
          "NgÆ°á»i CSKH",
          "NgÃ y táº¡o"
      );
      if (viewId != null && !viewId.isBlank()) {
        bodyReq.viewId = viewId;
      }

      Condition c = new Condition();
      c.fieldName = "TÃªn Liá»‡u TrÃ¬nh";
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
   * Kiá»ƒm tra trong báº£ng "Tá»« chá»‘i chÄƒm" Ä‘Ã­ch Ä‘Ã£ tá»“n táº¡i báº£n ghi vá»›i sá»‘ Ä‘iá»‡n thoáº¡i nÃ y chÆ°a.
   * Náº¿u Ä‘Ã£ tá»“n táº¡i thÃ¬ tráº£ vá» true, Ä‘á»ƒ bá» qua khÃ´ng insert trÃ¹ng.
   */
  public boolean existsRejectedCareByPhone(HttpSession session, String phoneNumber) throws Exception {
    return existsCareByPhone(session, phoneNumber, TU_CHOI_CHAM_TABLE_ID, false);
  }

  /**
   * Kiá»ƒm tra trong báº£ng "Äang chÄƒm" Ä‘Ã­ch Ä‘Ã£ tá»“n táº¡i báº£n ghi vá»›i sá»‘ Ä‘iá»‡n thoáº¡i nÃ y chÆ°a.
   */
  public boolean existsDangChamByPhone(HttpSession session, String phoneNumber) throws Exception {
    return existsCareByPhone(session, phoneNumber, DANG_CHAM_TABLE_ID, false);
  }

  private boolean existsCareByPhone(HttpSession session, String phoneNumber, String targetTableId, boolean isRetry) throws Exception {
    if (phoneNumber == null || phoneNumber.isBlank()) {
      return false;
    }

    String accessToken = tokenService.getAccessToken(session, isRetry); // Náº¿u lÃ  retry thÃ¬ force refresh

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    String url = buildSearchRecordsUrl(resolveAppTokenForTarget(targetTableId), targetTableId, 1, null);

    RecordSearchRequest bodyReq = new RecordSearchRequest();
    bodyReq.automaticFields = false;
    bodyReq.fieldNames = List.of("Äiá»‡n thoáº¡i");

    Condition c = new Condition();
    c.fieldName = "Äiá»‡n thoáº¡i";
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

  /** TÃ¬m kiáº¿m trao Ä‘á»•i hoáº·c lá»‹ch háº¹n theo record_id cá»§a khÃ¡ch hÃ ng */
  public List<BitableRecord> searchRecordsByCustomerId(HttpSession session, String baseId, String tableId,
      String customerRecordId, List<String> fieldNames, String viewId) throws Exception {
    if (baseId == null || baseId.isBlank() || tableId == null || tableId.isBlank() 
        || customerRecordId == null || customerRecordId.isBlank()) {
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
      if (viewId != null && !viewId.isBlank()) {
        bodyReq.viewId = viewId;
      }

      Condition c = new Condition();
      c.fieldName = "KhÃ¡ch HÃ ng";
      c.operator = "is";
      c.value = List.of(customerRecordId);

      Filter f = new Filter();
      f.conjunction = "and";
      f.conditions = List.of(c);

      bodyReq.filter = f;

      HttpEntity<RecordSearchRequest> entity = new HttpEntity<>(bodyReq, headers);

      ResponseEntity<RecordSearchResponse> response;
      try {
        response = restTemplate.exchange(url, HttpMethod.POST, entity, RecordSearchResponse.class);
      } catch (RestClientException e) {
        log.error("Error calling Bitable search records by customer ID API: {}", e.getMessage(), e);
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
        log.warn("Search records by customer ID has_more=true but page_token is empty. Break to avoid infinite loop.");
        break;
      }
    }

    return allRecords;
  }

  /** âœ… Build summary theo 1 table (Ä‘áº¿m status trong lÃºc search + paginate) */
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
          Object rawStatus = (fields != null) ? fields.get("Tráº¡ng thÃ¡i mess") : null;
          String status = normalizeStatus(extractText(rawStatus));

          if (status.isEmpty()) continue;

          // buckets
          if (status.contains("nhu cáº§u")) nhuCau++;
          else if (status.contains("trÃ¹ng")) trung++;
          else if (status.contains("rÃ¡c")) rac++;
          else if (status.contains("khÃ´ng tÆ°Æ¡ng tÃ¡c")) khongTuongTac++;
          else if (status.contains("chá»‘t nÃ³ng")) chotNong++;
          else if (status.contains("chá»‘t cÅ©")) chotCu++;
          else if (status.contains("Ä‘Æ¡n há»§y") || status.contains("Ä‘Æ¡n huá»·")) donHuy++;
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

    // Theo yÃªu cáº§u: Tá»•ng mess = Nhu cáº§u + TrÃ¹ng + RÃ¡c + KhÃ´ng tÆ°Æ¡ng tÃ¡c + Chá»‘t nÃ³ng + Chá»‘t cÅ© + ÄÆ¡n há»§y
    tongMess = nhuCau + trung + rac + khongTuongTac + chotNong + chotCu + donHuy;
    // Theo yÃªu cáº§u: Tá»•ng Ä‘Æ¡n = Chá»‘t nÃ³ng + Chá»‘t cÅ©
    tongDon = chotNong + chotCu;

    row.setTongMess(tongMess);
    row.setTongDon(tongDon);

    // ÄÆ¡n/mess nhu cáº§u = Tá»•ng Ä‘Æ¡n / (Nhu cáº§u + Chá»‘t nÃ³ng + Chá»‘t cÅ© + Há»§y)
    // -> backend chá»‰ giá»¯ Tá»¶ Lá»† (0.x), pháº§n trÄƒm vÃ  kÃ½ hiá»‡u % xá»­ lÃ½ á»Ÿ frontend
    long nhuCauDenominator = nhuCau + chotNong + chotCu + donHuy;
    double donPerMessNhuCau = (nhuCauDenominator == 0)
        ? 0.0
        : ((long)(((double) tongDon / (double) nhuCauDenominator)*100 * 10 + 0.5)) / 10.0;
    row.setDonPerMessNhuCau(donPerMessNhuCau);

    // ÄÆ¡n/mess tá»•ng = Tá»•ng Ä‘Æ¡n / Tá»•ng mess (cÅ©ng giá»¯ dáº¡ng tá»· lá»‡ 0.x)
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

      } catch (RestClientException e) {
        log.error("Error calling Bitable list tables API: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to call Bitable list tables API: " + e.getMessage(), e);
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
   * Táº¡o báº£n ghi má»›i trong báº£ng "Tá»« chá»‘i chÄƒm" Ä‘Ã­ch, dÃ¹ng access token cá»§a user hiá»‡n táº¡i.
   * fields pháº£i theo Ä‘Ãºng structure cá»§a Bitable (Map lá»“ng nhau, list, v.v.).
   */
  public void createRejectedCareRecord(HttpSession session, Map<String, Object> fields) throws Exception {
    if (fields == null || fields.isEmpty()) {
      return;
    }

    createCareRecordWithRetry(session, fields, TU_CHOI_CHAM_TABLE_ID, false);
  }

  /**
   * Táº¡o báº£n ghi má»›i trong báº£ng "Äang chÄƒm".
   */
  public void createDangChamRecord(HttpSession session, Map<String, Object> fields) throws Exception {
    if (fields == null || fields.isEmpty()) {
      return;
    }

    createCareRecordWithRetry(session, fields, DANG_CHAM_TABLE_ID, false);
  }

  private void createCareRecordWithRetry(HttpSession session, Map<String, Object> fields, String targetTableId, boolean isRetry) throws Exception {
    String accessToken = tokenService.getAccessToken(session, isRetry); // Náº¿u lÃ  retry thÃ¬ force refresh

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(accessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    Map<String, Object> body = new HashMap<>();
    body.put("fields", fields);

    String url = buildCreateRecordUrl(resolveAppTokenForTarget(targetTableId), targetTableId);

    // In ra curl tÆ°Æ¡ng Ä‘Æ°Æ¡ng Ä‘á»ƒ debug
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
      req.fieldNames = List.of("NgÃ y táº¡o", "Äiá»‡n Thoáº¡i", "Tráº¡ng thÃ¡i mess");
      req.viewId = viewId;

      Condition c = new Condition();
      c.fieldName = "NgÃ y táº¡o";
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
    if (v instanceof Number n) return String.valueOf(n);

    if (v instanceof Map<?, ?> map) {
      Object name = map.get("name");
      if (name != null) return String.valueOf(name);
      Object text = map.get("text");
      if (text != null) return String.valueOf(text);
      Object value = map.get("value");
      if (value != null) return String.valueOf(value);
    }

    if (v instanceof List<?> list) {
      StringBuilder sb = new StringBuilder();
      for (Object it : list) {
        String part = extractText(it);
        if (!part.isBlank()) {
          if (!sb.isEmpty()) sb.append(", ");
          sb.append(part);
        }
      }
      return sb.toString();
    }

    return String.valueOf(v);
  }

  private double round2(double x) {
    return BigDecimal.valueOf(x).setScale(2, RoundingMode.HALF_UP).doubleValue();
  }
}