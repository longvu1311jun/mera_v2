package mera.mera_v2.lark.token;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpSession;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import mera.mera_v2.model.TokenInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class LarkTokenService {

  private static final Logger log = LoggerFactory.getLogger(LarkTokenService.class);
  private static final String SESSION_TOKEN_INFO = "LARK_TOKEN_INFO";

  private final Object refreshMutex = new Object();

  private final RestTemplate restTemplate = new RestTemplate();

  private final TokenStorageService tokenStorage;

  public LarkTokenService(TokenStorageService tokenStorage) {
    this.tokenStorage = tokenStorage;
  }

  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.app-secret}")
  private String appSecret;

  /**
   * Default: log masked tokens only.
   * Set lark.token.log-full=true (dev only) if you really want full token printed.
   */
  @Value("${lark.token.log-full:false}")
  private boolean logFullToken;

  public boolean hasToken(HttpSession session) {
    if (tokenStorage.hasToken()) return true;
    return session != null && session.getAttribute(SESSION_TOKEN_INFO) != null;
  }

  /** Convenience for jobs/background calls (no session). */
  public boolean hasToken() {
    return tokenStorage.hasToken();
  }

  public TokenInfo getCurrentToken(HttpSession session) {
    TokenInfo fromGlobal = tokenStorage.get();
    if (fromGlobal != null) return fromGlobal;

    if (session == null) return null;
    Object v = session.getAttribute(SESSION_TOKEN_INFO);
    return (v instanceof TokenInfo) ? (TokenInfo) v : null;
  }

  /** Convenience for jobs/background calls (no session). */
  public TokenInfo getCurrentToken() {
    return tokenStorage.get();
  }

  public String getAccessToken(HttpSession session, boolean forceRefresh) throws Exception {
    if (!hasToken(session)) {
      throw new IllegalStateException("No token. Please login first.");
    }

    if (forceRefresh) {
      refreshToken(session);
    } else {
      autoRefreshTokenIfNeeded(session);
    }

    TokenInfo token = getCurrentToken(session);
    if (token == null || token.getAccessToken() == null) {
      throw new IllegalStateException("Token not available.");
    }
    return token.getAccessToken();
  }

  /** Convenience for jobs/background calls (no session). */
  public String getAccessToken(boolean forceRefresh) throws Exception {
    return getAccessToken(null, forceRefresh);
  }

  /**
   * Tá»± Ä‘á»™ng refresh token náº¿u:
   * - Token Ä‘Ã£ háº¿t háº¡n (expired)
   * - Token sáº¯p háº¿t háº¡n trong 60 giÃ¢y
   * - Token Ä‘Ã£ Ä‘Æ°á»£c cáº­p nháº­t hÆ¡n 1 giá» trÆ°á»›c (Ä‘á»ƒ Ä‘áº£m báº£o refresh Ä‘á»‹nh ká»³)
   */
  public void autoRefreshTokenIfNeeded(HttpSession session) {
    TokenInfo token = getCurrentToken(session);
    if (token == null) {
      log.warn("âš ï¸ No token found in session");
      return;
    }

    if (token.getExpiresAt() == null) {
      log.warn("âš ï¸ Token has no expiresAt, cannot check expiration");
      return;
    }

    long now = Instant.now().toEpochMilli();
    long expiresAtMs = token.getExpiresAt()
        .atZone(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli();

    long remainMs = expiresAtMs - now;
    long remainSeconds = remainMs / 1000;

    // Kiá»ƒm tra náº¿u token Ä‘Ã£ Ä‘Æ°á»£c cáº­p nháº­t hÆ¡n 1 giá» trÆ°á»›c
    boolean needsRefreshByTime = false;
    if (token.getLastUpdated() != null) {
      long lastUpdatedMs = token.getLastUpdated()
          .atZone(ZoneId.systemDefault())
          .toInstant()
          .toEpochMilli();
      long timeSinceUpdateMs = now - lastUpdatedMs;
      long timeSinceUpdateHours = timeSinceUpdateMs / (1000 * 60 * 60);

      if (timeSinceUpdateHours >= 1) {
        needsRefreshByTime = true;
        log.info("ðŸ• Token was updated {} hours ago, will refresh", timeSinceUpdateHours);
      }
    }

    // Refresh náº¿u: Ä‘Ã£ háº¿t háº¡n, sáº¯p háº¿t háº¡n (< 60s), hoáº·c Ä‘Ã£ Ä‘Æ°á»£c cáº­p nháº­t > 1 giá»
    boolean isExpired = remainMs <= 0;
    boolean isExpiringSoon = remainMs <= 60_000;

    if (isExpired || isExpiringSoon || needsRefreshByTime) {
      log.info("ðŸ”„ Auto-refreshing token - Expired: {}, Expiring soon (<60s): {}, Needs refresh by time (>1h): {}, Remaining: {} seconds",
          isExpired, isExpiringSoon, needsRefreshByTime, remainSeconds);

      try {
        TokenInfo oldToken = token;
        TokenInfo newToken = refreshToken(session);

        // Log chi tiáº¿t Ä‘á»ƒ kiá»ƒm tra
        log.info("âœ… AUTO REFRESH SUCCESSFUL:");
        log.info("   oldAccessToken={}, oldRefreshToken={}, oldExpiresAt={}, oldLastUpdated={}",
            displayToken(oldToken.getAccessToken()),
            displayToken(oldToken.getRefreshToken()),
            oldToken.getExpiresAt(),
            oldToken.getLastUpdated());
        log.info("   newAccessToken={}, newRefreshToken={}, newExpiresAt={}, newLastUpdated={}, expiresIn={}s",
            displayToken(newToken.getAccessToken()),
            displayToken(newToken.getRefreshToken()),
            newToken.getExpiresAt(),
            newToken.getLastUpdated(),
            newToken.getExpiresIn());
        log.info("   â° Refresh completed at: {}", LocalDateTime.now());

      } catch (Exception e) {
        log.error("âŒ Auto refresh token failed: {}", e.getMessage(), e);
        throw new RuntimeException("Failed to auto-refresh token: " + e.getMessage(), e);
      }
    } else {
      log.debug("âœ“ Token still valid - Remaining: {} seconds, LastUpdated: {}",
          remainSeconds, token.getLastUpdated());
    }
  }

  /** Exchange code -> user access_token / refresh_token */
  public TokenInfo exchangeCodeForToken(String code, HttpSession session) throws Exception {
    String appAccessToken = getAppAccessToken();

    String url = "https://open.larksuite.com/open-apis/authen/v1/access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setBearerAuth(appAccessToken);
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    ExchangeReq req = new ExchangeReq();
    req.code = code;
    req.grantType = "authorization_code";

    HttpEntity<ExchangeReq> entity = new HttpEntity<>(req, headers);

    ResponseEntity<ExchangeResp> resp;
    try {
      resp = restTemplate.exchange(url, HttpMethod.POST, entity, ExchangeResp.class);
    } catch (RestClientException e) {
      throw new RuntimeException("Exchange token failed: " + e.getMessage(), e);
    }

    if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
      throw new RuntimeException("Exchange token HTTP error: " + resp.getStatusCode());
    }

    ExchangeResp body = resp.getBody();
    if (body.code != 0 || body.data == null) {
      throw new RuntimeException("Exchange token error: " + body.code + " - " + body.msg);
    }

    TokenInfo tokenInfo = buildTokenInfo(
        body.data.accessToken,
        body.data.refreshToken,
        body.data.tokenType,
        body.data.expiresIn
    );

    // âœ… Save global token (so scheduler + all functions use the same token)
    tokenStorage.save(tokenInfo);

    // Keep session copy (UI + backward compatible)
    if (session != null) {
      session.setAttribute(SESSION_TOKEN_INFO, tokenInfo);
    }

    log.info("âœ… Token saved:");
    log.info("   accessToken = {}", tokenInfo.getAccessToken());
    log.info("   refreshToken = {}", displayToken(tokenInfo.getRefreshToken()));
    log.info("   tokenType = {}", tokenInfo.getTokenType());
    log.info("   expiresIn = {} seconds", tokenInfo.getExpiresIn());
    log.info("   expiresAt = {}", tokenInfo.getExpiresAt());
    log.info("   lastUpdated = {}", tokenInfo.getLastUpdated());

    return tokenInfo;
  }

  /** Refresh token (global). For scheduler usage. */
  public TokenInfo refreshToken() throws Exception {
    return refreshToken(null);
  }

  /** Refresh token + log token má»›i sau khi refresh xong */
  public TokenInfo refreshToken(HttpSession session) throws Exception {
    synchronized (refreshMutex) {
      TokenInfo current = getCurrentToken(session);
      if (current == null || current.getRefreshToken() == null || current.getRefreshToken().isBlank()) {
        throw new IllegalStateException("No refresh_token available. Please login again.");
      }

      String url = "https://open.larksuite.com/open-apis/authen/v1/refresh_access_token";

      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_JSON);
      headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

      RefreshReq req = new RefreshReq();
      req.appId = appId;
      req.appSecret = appSecret;
      req.grantType = "refresh_token";
      req.refreshToken = current.getRefreshToken();

      HttpEntity<RefreshReq> entity = new HttpEntity<>(req, headers);

      ResponseEntity<RefreshResp> resp;
      try {
        resp = restTemplate.exchange(url, HttpMethod.POST, entity, RefreshResp.class);
      } catch (RestClientException e) {
        throw new RuntimeException("Refresh token failed: " + e.getMessage(), e);
      }

      if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
        throw new RuntimeException("Refresh token HTTP error: " + resp.getStatusCode());
      }

      RefreshResp body = resp.getBody();
      if (body.code != 0 || body.data == null) {
        throw new RuntimeException("Refresh token error: " + body.code + " - " + body.msg);
      }

      TokenInfo newToken = buildTokenInfo(
          body.data.accessToken,
          body.data.refreshToken,
          body.data.tokenType,
          body.data.expiresIn
      );

      // âœ… Update global token
      tokenStorage.save(newToken);

      // Keep session copy (UI + backward compatible)
      if (session != null) {
        session.setAttribute(SESSION_TOKEN_INFO, newToken);
      }

      log.info("ðŸ”„ REFRESH TOKEN COMPLETED:");
      log.info("   newAccessToken={}, newRefreshToken={}",
          displayToken(newToken.getAccessToken()),
          displayToken(newToken.getRefreshToken()));
      log.info("   expiresAt={}, lastUpdated={}, expiresIn={}s, tokenType={}",
          newToken.getExpiresAt(),
          newToken.getLastUpdated(),
          newToken.getExpiresIn(),
          newToken.getTokenType());

      return newToken;
    }
  }

  // ===================== helpers =====================

  private String getAppAccessToken() {
    String url = "https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));

    AppTokenReq req = new AppTokenReq();
    req.appId = appId;
    req.appSecret = appSecret;

    HttpEntity<AppTokenReq> entity = new HttpEntity<>(req, headers);

    ResponseEntity<AppTokenResp> resp;
    try {
      resp = restTemplate.exchange(url, HttpMethod.POST, entity, AppTokenResp.class);
    } catch (RestClientException e) {
      throw new RuntimeException("Get app_access_token failed: " + e.getMessage(), e);
    }

    if (resp.getStatusCode() != HttpStatus.OK || resp.getBody() == null) {
      throw new RuntimeException("Get app_access_token HTTP error: " + resp.getStatusCode());
    }

    AppTokenResp body = resp.getBody();
    if (body.code != 0 || body.appAccessToken == null) {
      throw new RuntimeException("Get app_access_token error: " + body.code + " - " + body.msg);
    }

    return body.appAccessToken;
  }

  /**
   * âœ… FIX chá»— nÃ y: TokenInfo.setExpiresIn(...) nháº­n int => convert long -> int an toÃ n
   */
  private TokenInfo buildTokenInfo(String accessToken, String refreshToken, String tokenType, long expiresInSeconds) {
    TokenInfo t = new TokenInfo();
    t.setAccessToken(accessToken);
    t.setRefreshToken(refreshToken);
    t.setTokenType(tokenType);

    int expiresInt = toIntExactSafe(expiresInSeconds);
    t.setExpiresIn(expiresInt);

    long nowMs = Instant.now().toEpochMilli();
    long expiresAtMs = nowMs + (expiresInSeconds * 1000L);

    LocalDateTime expiresAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(expiresAtMs), ZoneId.systemDefault());
    LocalDateTime lastUpdated = LocalDateTime.ofInstant(Instant.ofEpochMilli(nowMs), ZoneId.systemDefault());

    t.setExpiresAt(expiresAt);
    t.setLastUpdated(lastUpdated);

    return t;
  }

  private int toIntExactSafe(long v) {
    try {
      return Math.toIntExact(v);
    } catch (ArithmeticException ex) {
      // Lark thÆ°á»ng 7200s, nÃªn gáº§n nhÆ° khÃ´ng bao giá» vÃ o Ä‘Ã¢y.
      log.warn("expires_in too large for int: {}", v);
      return Integer.MAX_VALUE;
    }
  }

  private String mask(String token) {
    if (token == null) return "null";
    String s = token.trim();
    if (s.length() <= 12) return "****";
    return s.substring(0, 6) + "****" + s.substring(s.length() - 6);
  }

  private String displayToken(String token) {
    if (logFullToken) return token;
    return mask(token);
  }

  // ===================== DTOs =====================

  private static class AppTokenReq {
    @JsonProperty("app_id") String appId;
    @JsonProperty("app_secret") String appSecret;
  }

  private static class AppTokenResp {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("app_access_token") String appAccessToken;
  }

  private static class ExchangeReq {
    @JsonProperty("code") String code;
    @JsonProperty("grant_type") String grantType;
  }

  private static class ExchangeResp {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") ExchangeData data;
  }

  private static class ExchangeData {
    @JsonProperty("access_token") String accessToken;
    @JsonProperty("refresh_token") String refreshToken;
    @JsonProperty("token_type") String tokenType;
    @JsonProperty("expires_in") long expiresIn;
  }

  private static class RefreshReq {
    @JsonProperty("app_id") String appId;
    @JsonProperty("app_secret") String appSecret;
    @JsonProperty("grant_type") String grantType;
    @JsonProperty("refresh_token") String refreshToken;
  }

  private static class RefreshResp {
    @JsonProperty("code") int code;
    @JsonProperty("msg") String msg;
    @JsonProperty("data") RefreshData data;
  }

  private static class RefreshData {
    @JsonProperty("access_token") String accessToken;
    @JsonProperty("refresh_token") String refreshToken;
    @JsonProperty("token_type") String tokenType;
    @JsonProperty("expires_in") long expiresIn;
  }
}