package mera.mera_v2.config;

import lombok.RequiredArgsConstructor;
import mera.mera_v2.ltkach.LtKhachConfigInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final LtKhachConfigInterceptor ltKhachConfigInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(ltKhachConfigInterceptor)
                .addPathPatterns("/lt-khach/combo", "/lt-khach/substitution")
                .excludePathPatterns("/lt-khach/config-login");
    }
}
