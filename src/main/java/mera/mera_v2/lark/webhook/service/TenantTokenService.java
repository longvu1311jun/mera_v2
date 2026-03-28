package mera.mera_v2.lark.webhook.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Service để lấy và cache Tenant Access Token
 * Tenant token được lấy từ app_id + app_secret, không cần user login
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TenantTokenService {

    private final LarkBaseProperties larkBaseProperties;
    private final RestTemplate restTemplate;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private volatile String cachedTenantToken;
    private volatile Instant tokenExpireAt = Instant.EPOCH;

    /**
     * Lấy tenant access token
     * Sử dụng cache nếu token còn hơn 1 phút
     */
    public String getTenantAccessToken() {
        // Kiểm tra cache: token còn hơn 1 phút không
        if (cachedTenantToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(60))) {
            log.debug("✅ Using cached tenant token (expires in {} seconds)",
                    tokenExpireAt.getEpochSecond() - Instant.now().getEpochSecond());
            return cachedTenantToken;
        }

        // Nếu cache hết hạn, refresh token
        synchronized (this) {
            // Double-check locking
            if (cachedTenantToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(60))) {
                return cachedTenantToken;
            }

            log.info("🔄 Refreshing tenant access token...");
            try {
                refreshTenantToken();
                return cachedTenantToken;
            } catch (Exception e) {
                log.error("❌ Failed to get tenant access token: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to get tenant access token: " + e.getMessage(), e);
            }
        }
    }

    /**
     * Refresh tenant token bằng cách gọi Lark API
     */
    private void refreshTenantToken() throws Exception {
        String appId = larkBaseProperties.getAppId();
        String appSecret = larkBaseProperties.getAppSecret();
        String baseUrl = larkBaseProperties.getBaseUrl();

        if (appId == null || appId.isBlank() || appSecret == null || appSecret.isBlank()) {
            throw new IllegalStateException("app_id or app_secret is not configured");
        }

        String tokenUrl = baseUrl + "/open-apis/auth/v3/tenant_access_token/internal";

        // Tạo request body
        Map<String, String> requestBody = new HashMap<>();
        requestBody.put("app_id", appId);
        requestBody.put("app_secret", appSecret);

        log.debug("📤 Calling Lark API: {}", tokenUrl);

        try {
            String response = restTemplate.postForObject(tokenUrl, requestBody, String.class);

            if (response == null) {
                throw new RuntimeException("Empty response from Lark API");
            }

            // Parse response
            JsonNode responseNode = objectMapper.readTree(response);
            int code = responseNode.get("code").asInt();

            if (code != 0) {
                String msg = responseNode.has("msg") ? responseNode.get("msg").asText() : "Unknown error";
                throw new RuntimeException("Lark API error: code=" + code + ", msg=" + msg);
            }

            cachedTenantToken = responseNode.get("tenant_access_token").asText();
            long expireSeconds = responseNode.get("expire").asLong();
            tokenExpireAt = Instant.now().plusSeconds(expireSeconds);

            log.info("✅ Tenant access token refreshed successfully");
            log.info("   Token expires in: {} seconds ({} minutes)",
                    expireSeconds, expireSeconds / 60);

        } catch (Exception e) {
            log.error("❌ Failed to call Lark API: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Lấy thời gian còn lại của token (tính bằng giây)
     */
    public long getTokenRemainingSeconds() {
        if (tokenExpireAt == null || tokenExpireAt.equals(Instant.EPOCH)) {
            return 0;
        }
        long remaining = tokenExpireAt.getEpochSecond() - Instant.now().getEpochSecond();
        return Math.max(0, remaining);
    }

    /**
     * Kiểm tra xem token còn hợp lệ không
     */
    public boolean isTokenValid() {
        return cachedTenantToken != null && Instant.now().isBefore(tokenExpireAt);
    }

    /**
     * Kiểm tra xem token còn hơn N phút không
     */
    public boolean isTokenValidForMinutes(int minutes) {
        if (tokenExpireAt == null || tokenExpireAt.equals(Instant.EPOCH)) {
            return false;
        }
        Instant futureTime = Instant.now().plusSeconds((long) minutes * 60);
        return tokenExpireAt.isAfter(futureTime);
    }
}
