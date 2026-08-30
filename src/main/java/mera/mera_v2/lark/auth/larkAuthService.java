package mera.mera_v2.lark.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * OAuth Lark: doi code -> user_access_token va lam moi token.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class larkAuthService {

  @Value("${lark.app-id}")
  private String appId;

  @Value("${lark.app-secret}")
  private String appSecret;

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final RestTemplate restTemplate;

  /**
   * Lay app_access_token tu Lark
   */
  public String getAppAccessToken() throws Exception {
    String url = "https://open.larksuite.com/open-apis/auth/v3/app_access_token/internal/";

    Map<String, String> body = Map.of(
        "app_id", appId,
        "app_secret", appSecret
    );

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<String> resp;
    try {
      resp = restTemplate.postForEntity(url, entity, String.class);
    } catch (RestClientException e) {
      log.error("Error calling getAppAccessToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to get app access token: " + e.getMessage(), e);
    }

    if (!resp.getStatusCode().is2xxSuccessful()) {
      throw new RuntimeException("getAppAccessToken HTTP error: " + resp.getStatusCode());
    }

    JsonNode json = objectMapper.readTree(resp.getBody());
    int code = json.path("code").asInt();
    if (code != 0) {
      throw new RuntimeException("Lark error getAppAccessToken: " + resp.getBody());
    }

    JsonNode tokenNode = json.get("app_access_token");
    if (tokenNode == null || tokenNode.isNull()) {
      throw new RuntimeException("app_access_token not found in response");
    }
    return objectMapper.convertValue(tokenNode, String.class);
  }

  /**
   * Doi authorization code -> user_access_token + refresh_token
   */
  public larkTokenResponse exchangeCodeForUserToken(String code) throws Exception {
    String appAccessToken = getAppAccessToken();

    String url = "https://open.larksuite.com/open-apis/authen/v1/access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(appAccessToken);

    Map<String, String> body = Map.of(
        "grant_type", "authorization_code",
        "code", code
    );

    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<larkTokenResponse> resp;
    try {
      resp = restTemplate.postForEntity(url, entity, larkTokenResponse.class);
    } catch (RestClientException e) {
      log.error("Error calling exchangeCodeForUserToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to exchange code for user token: " + e.getMessage(), e);
    }

    larkTokenResponse result = resp.getBody();
    if (result == null) {
      throw new RuntimeException("Empty response from Lark");
    }
    if (result.getCode() != 0) {
      throw new RuntimeException("Lark error: " + result.getCode() + " - " + result.getMsg());
    }

    return result;
  }

  /**
   * Lam moi user_access_token bang refresh_token
   */
  public larkTokenResponse refreshUserAccessToken(String refreshToken) throws Exception {
    String appAccessToken = getAppAccessToken();

    String url = "https://open.larksuite.com/open-apis/authen/v1/refresh_access_token";

    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    headers.setBearerAuth(appAccessToken);

    Map<String, String> body = Map.of(
        "grant_type", "refresh_token",
        "refresh_token", refreshToken
    );

    HttpEntity<Map<String, String>> entity = new HttpEntity<>(body, headers);

    ResponseEntity<larkTokenResponse> resp;
    try {
      log.info("Calling refresh_access_token API...");
      resp = restTemplate.postForEntity(url, entity, larkTokenResponse.class);
    } catch (RestClientException e) {
      log.error("Error calling refreshUserAccessToken API: {}", e.getMessage(), e);
      throw new RuntimeException("Failed to refresh user token: " + e.getMessage(), e);
    }

    larkTokenResponse result = resp.getBody();
    if (result == null) {
      throw new RuntimeException("Empty response from Lark refresh token API");
    }
    if (result.getCode() != 0) {
      throw new RuntimeException("Lark refresh token error: " + result.getCode() + " - " + result.getMsg());
    }

    log.info("Successfully refreshed user access token");
    return result;
  }
}
