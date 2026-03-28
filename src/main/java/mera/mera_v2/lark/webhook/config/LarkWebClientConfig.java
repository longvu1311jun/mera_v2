package mera.mera_v2.lark.webhook.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class LarkWebClientConfig {

  @Bean
  public WebClient larkWebClient() {
    // Tăng buffer size lên 20MB để xử lý response lớn
    int maxSize = 20 * 1024 * 1024; // 20MB

    ExchangeStrategies strategies = ExchangeStrategies.builder()
        .codecs(c -> c.defaultCodecs().maxInMemorySize(maxSize))
        .build();

    // WebClient sẽ tự động sử dụng timeout từ Spring Boot config
    // Buffer size đã được tăng lên 20MB để xử lý response lớn
    return WebClient.builder()
        .exchangeStrategies(strategies)
        .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
        .build();
  }
}
