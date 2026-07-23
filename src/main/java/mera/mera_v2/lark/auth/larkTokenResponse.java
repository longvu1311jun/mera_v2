package mera.mera_v2.lark.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public class larkTokenResponse {
  private int code;
  private String msg;
  private TokenData data;

  public int getCode() { return code; }
  public void setCode(int code) { this.code = code; }

  public String getMsg() { return msg; }
  public void setMsg(String msg) { this.msg = msg; }

  public TokenData getData() { return data; }
  public void setData(TokenData data) { this.data = data; }

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

    public String getAccessToken() { return accessToken; }
    public void setAccessToken(String accessToken) { this.accessToken = accessToken; }

    public String getRefreshToken() { return refreshToken; }
    public void setRefreshToken(String refreshToken) { this.refreshToken = refreshToken; }

    public long getExpiresIn() { return expiresIn; }
    public void setExpiresIn(long expiresIn) { this.expiresIn = expiresIn; }

    public long getRefreshExpiresIn() { return refreshExpiresIn; }
    public void setRefreshExpiresIn(long refreshExpiresIn) { this.refreshExpiresIn = refreshExpiresIn; }

    public String getTokenType() { return tokenType; }
    public void setTokenType(String tokenType) { this.tokenType = tokenType; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getTenantKey() { return tenantKey; }
    public void setTenantKey(String tenantKey) { this.tenantKey = tenantKey; }
  }
}
