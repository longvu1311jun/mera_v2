package mera.mera_v2.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Nhanh nay khong dung Thymeleaf: trang search-info la file tinh trong /static.
 * Map URL /search-info sang file do de giu nguyen duong dan cu.
 */
@Configuration
public class WebRoutesConfig implements WebMvcConfigurer {

  @Override
  public void addViewControllers(ViewControllerRegistry registry) {
    registry.addViewController("/search-info").setViewName("forward:/search-info.html");
  }
}
