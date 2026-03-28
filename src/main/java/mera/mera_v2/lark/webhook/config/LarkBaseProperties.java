package mera.mera_v2.lark.webhook.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
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
}
