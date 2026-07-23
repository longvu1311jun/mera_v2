package mera.mera_v2.lark.webhook.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "lark")
public class LarkBaseProperties {

  private String baseUrl;
  private String appId;
  private String appSecret;
  private String appToken;

  private String userAccessToken;
  private String redirectUrl;
  private String defaultUserId;

  public String getBaseUrl() { return baseUrl; }
  public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

  public String getAppId() { return appId; }
  public void setAppId(String appId) { this.appId = appId; }

  public String getAppSecret() { return appSecret; }
  public void setAppSecret(String appSecret) { this.appSecret = appSecret; }

  public String getAppToken() { return appToken; }
  public void setAppToken(String appToken) { this.appToken = appToken; }

  public String getUserAccessToken() { return userAccessToken; }
  public void setUserAccessToken(String userAccessToken) { this.userAccessToken = userAccessToken; }

  public String getRedirectUrl() { return redirectUrl; }
  public void setRedirectUrl(String redirectUrl) { this.redirectUrl = redirectUrl; }

  public String getDefaultUserId() { return defaultUserId; }
  public void setDefaultUserId(String defaultUserId) { this.defaultUserId = defaultUserId; }
}
