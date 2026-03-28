package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.token.TokenStorageService;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkImService {
    
    private static final String IM_API_URL = "https://open.larksuite.com/open-apis/im/v1/messages";
    private final RestTemplate restTemplate;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private mera.mera_v2.lark.webhook.config.LarkBaseProperties larkBaseProperties;
    
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private TokenStorageService tokenStorageService;
    
    /**
     * Gửi tin nhắn cho CSKH khi có khách mới được giao
     */
    public void sendToCskh(String cskhOpenId,
                           String tenKhach,
                           String dienThoai,
                           String diaChi,
                           Integer tuoi) {
        if (cskhOpenId == null || cskhOpenId.isBlank()) {
            log.warn("[LARK IM] Không có cskhOpenId, bỏ qua gửi tin nhắn");
            return;
        }
        
        String token = getUserAccessToken();
        if (token == null || token.isBlank()) {
            log.error("[LARK IM] Không lấy được user_access_token, bỏ qua gửi tin nhắn");
            return;
        }
        
        try {
            StringBuilder text = new StringBuilder();
            text.append("📢 Có khách mới được giao cho bạn\n");
            
            if (tenKhach != null && !tenKhach.isBlank()) {
                text.append("• Tên khách: ").append(tenKhach).append("\n");
            }
            
            if (dienThoai != null && !dienThoai.isBlank()) {
                text.append("• Điện thoại: ").append(dienThoai).append("\n");
            }
            
            if (diaChi != null && !diaChi.isBlank()) {
                text.append("• Địa chỉ: ").append(diaChi).append("\n");
            }
            
            if (tuoi != null) {
                text.append("• Tuổi: ").append(tuoi).append("\n");
            }
            
            String contentJson = "{\"text\":\"" + escapeForJson(text.toString()) + "\"}";
            
            // receive_id_type là query parameter trong URL, không phải trong body
            String url = IM_API_URL + "?receive_id_type=open_id";
            
            Map<String, Object> body = new HashMap<>();
            body.put("receive_id", cskhOpenId);
            body.put("msg_type", "text");
            body.put("content", contentJson);
            
            // Log request body để debug
            try {
                ObjectMapper objectMapper = new ObjectMapper();
                String requestBodyJson = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(body);
                log.info("📤 LARK IM API REQUEST:");
                log.info("   URL: {}", url);
                log.info("   User Access Token: {}", maskToken(token));
                log.info("   Receive ID (CSKH Open ID): {}", cskhOpenId);
                log.info("   Request Body:\n{}", requestBodyJson);
            } catch (Exception e) {
                log.warn("⚠️ Failed to serialize request body for logging: {}", e.getMessage());
            }
            
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(token);
            
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            
            ResponseEntity<String> resp = restTemplate.exchange(
                    url,
                    HttpMethod.POST,
                    entity,
                    String.class
            );
            
            log.info("[LARK IM] Send message resp: {}", resp.getBody());
            
        } catch (Exception e) {
            log.error("[LARK IM] Error when sending message: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Escape các ký tự đặc biệt cho JSON
     */
    private String escapeForJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
                   .replace("\"", "\\\"")
                   .replace("\n", "\\n")
                   .replace("\r", "\\r")
                   .replace("\t", "\\t");
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
        
        log.warn("[LARK IM] User access token not found in both TokenStorageService and LarkBaseProperties");
        return null;
    }
    
    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 10) + "...";
    }
}
