package mera.mera_v2.lark.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class larkTokenResponse {
  private int code;
  private String msg;
  private TokenData data;   // <== field tên "data" -> Lombok sinh getData()

  @Data
  public static class TokenData {

    @JsonProperty("access_token")
    private String accessToken;

    @JsonProperty("refresh_token")
    private String refreshToken;

    @JsonProperty("expires_in")
    private long expiresIn;

    @JsonProperty("refresh_expires_in")
    private long refreshExpiresIn;

    @JsonProperty("token_type")
    private String tokenType;

    @JsonProperty("user_id")
    private String userId;

    @JsonProperty("tenant_key")
    private String tenantKey;
  }
}