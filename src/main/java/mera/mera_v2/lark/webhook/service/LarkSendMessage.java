package mera.mera_v2.lark.webhook.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import mera.mera_v2.lark.token.TokenStorageService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkSendMessage {
  private static final String FIND_BY_DEPT_URL =
      "https://open.larksuite.com/open-apis/contact/v3/users/find_by_department";
  
  private final RestTemplate restTemplate;
  
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private LarkBaseProperties larkBaseProperties;
  
  @org.springframework.beans.factory.annotation.Autowired(required = false)
  private TokenStorageService tokenStorageService;
  
  /**
   * Resolve CSKH Open ID bằng số điện thoại từ department
   * @param departmentId Department ID của phòng CSKH
   * @param cskhPhoneNumber Số điện thoại của CSKH (từ assigning_care.phone_number)
   * @param openIdRaw Open ID từ webhook nếu có sẵn (có thể là UUID, không phải open_id)
   * @return Open ID của CSKH từ API find_by_department (format: ou_...)
   */
  public String resolveCskhOpenIdByPhone(String departmentId,
      String cskhPhoneNumber,
      String openIdRaw) {

    // Luôn gọi API find_by_department để lấy open_id chính xác
    // Không dùng openIdRaw từ webhook vì có thể là UUID, không phải open_id
    log.info("[LARK CONTACT] Resolving CSKH Open ID by phone number (ignoring webhook ID: {})", openIdRaw);
    if (cskhPhoneNumber == null || cskhPhoneNumber.isBlank()) {
      log.warn("[LARK CONTACT] CSKH phone number is null or blank");
      return null;
    }

    String token = getUserAccessToken();
    if (token == null || token.isBlank()) {
      log.error("[LARK CONTACT] Không lấy được user_access_token");
      return null;
    }

    try {
      String pageToken = null;

      while (true) {
        // build URL theo curl command chuẩn
        StringBuilder url = new StringBuilder(FIND_BY_DEPT_URL);
        url.append("?department_id=").append(
            URLEncoder.encode(departmentId, StandardCharsets.UTF_8));
        url.append("&department_id_type=department_id");
        url.append("&page_size=50");
        url.append("&user_id_type=open_id");
        if (pageToken != null && !pageToken.isBlank()) {
          url.append("&page_token=").append(URLEncoder.encode(pageToken, StandardCharsets.UTF_8));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Authorization", "Bearer " + token);

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        @SuppressWarnings("unchecked")
        ResponseEntity<Map<String, Object>> resp = restTemplate.exchange(
            url.toString(),
            HttpMethod.GET,
            entity,
            (Class<Map<String, Object>>) (Class<?>) Map.class
        );

        Map<String, Object> body = resp.getBody();
        log.info("[LARK CONTACT] Response: {}", body);

        if (body == null || body.get("data") == null) {
          break;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) body.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        if (items != null) {
          // Normalize số điện thoại: loại bỏ khoảng trắng, dấu gạch ngang, dấu ngoặc đơn
          String normalizedCskhPhone = normalizePhoneNumber(cskhPhoneNumber);
          log.debug("[LARK CONTACT] Searching for CSKH with phone: '{}' (normalized: '{}')", 
                  cskhPhoneNumber, normalizedCskhPhone);
          
          for (Map<String, Object> item : items) {
            Object mobileObj = item.get("mobile");
            String mobile = null;
            if (mobileObj != null) {
              mobile = mobileObj.toString().trim();
            }
            
            if (mobile != null && !mobile.isBlank()) {
              String normalizedMobile = normalizePhoneNumber(mobile);
              // So sánh số điện thoại đã normalize
              if (normalizedMobile.equals(normalizedCskhPhone)) {
                // Lấy open_id từ response của API find_by_department
                String openId = (String) item.get("open_id");
                String name = (String) item.get("name");
                log.info("[LARK CONTACT] ✅ Found CSKH by phone '{}' (mobile: '{}', name: '{}') -> open_id={}", 
                        cskhPhoneNumber, mobile, name, openId);
                if (openId == null || openId.isBlank()) {
                  log.warn("[LARK CONTACT] ⚠️ open_id is null or blank in response for CSKH '{}'", name);
                }
                return openId;
              }
            }
          }
        }

        Boolean hasMore = data.get("has_more") instanceof Boolean
            ? (Boolean) data.get("has_more")
            : Boolean.FALSE;

        if (!hasMore) break;

        pageToken = (String) data.get("page_token");
        if (pageToken == null || pageToken.isBlank()) break;
      }

    } catch (Exception e) {
      log.error("[LARK CONTACT] Error resolveCskhOpenIdByPhone: {}", e.getMessage(), e);
    }

    log.warn("[LARK CONTACT] Không tìm thấy CSKH với số điện thoại '{}' trong department {}", 
            cskhPhoneNumber, departmentId);
    return null;
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
  
  private String getUserAccessToken() {
    // Ưu tiên lấy từ TokenStorageService (được cập nhật bởi scheduler)
    if (tokenStorageService != null) {
      String token = tokenStorageService.getUserAccessToken();
      if (token != null && !token.isBlank()) {
        return token;
      }
    }
    
    // Fallback: lấy từ LarkBaseProperties (nếu TokenStorageService không có)
    if (larkBaseProperties != null && larkBaseProperties.getUserAccessToken() != null) {
      String token = larkBaseProperties.getUserAccessToken().trim();
      if (!token.isBlank()) {
        return token;
      }
    }
    
    log.warn("[LARK CONTACT] User access token not found in both TokenStorageService and LarkBaseProperties");
    return null;
  }
}
