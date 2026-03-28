package mera.mera_v2.lark.webhook.lark;


import lombok.Data;
import lombok.RequiredArgsConstructor;
import mera.mera_v2.lark.webhook.config.LarkBaseProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class LarkAuthClient {

  private final LarkBaseProperties props;

  private final WebClient webClient = WebClient.builder().build();

  private volatile String cachedToken;
  private volatile Instant tokenExpireAt = Instant.EPOCH;

  public String getTenantAccessToken() {
    if (cachedToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(60))) {
      return cachedToken;
    }

    synchronized (this) {
      if (cachedToken != null && Instant.now().isBefore(tokenExpireAt.minusSeconds(60))) {
        return cachedToken;
      }

      System.out.println(">>> CALLING TOKEN API...");

      TokenResponse resp = webClient.post()
          .uri(props.getBaseUrl() + "/open-apis/auth/v3/tenant_access_token/internal")
          .bodyValue(new TokenRequest(props.getAppId(), props.getAppSecret()))
          .retrieve()
          .bodyToMono(TokenResponse.class)
          .doOnNext(r -> System.out.println(">>> TOKEN RESPONSE: " + r))
          .block();

      if (resp == null || resp.code != 0) {
        System.out.println(">>> TOKEN ERROR! Response = " + resp);
        throw new RuntimeException("Failed to get tenant_access_token");
      }

      cachedToken = resp.tenant_access_token;
      tokenExpireAt = Instant.now().plusSeconds(resp.expire);

      System.out.println(">>> TOKEN OK = " + cachedToken);

      return cachedToken;
    }
  }
  @Data
  private static class TokenRequest {
    private final String app_id;
    private final String app_secret;
  }

  @Data
  private static class TokenResponse {
    private int code;
    private String msg;
    private long expire;
    private String tenant_access_token;
  }
}
