package mera.mera_v2.lark.webhook.config;

import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.stereotype.Component;

/**
 * Initializer để set ApplicationContext cho MemoryLogAppender
 */
@Component
public class LogAppenderInitializer implements ApplicationListener<ContextRefreshedEvent> {
    
    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        ApplicationContext context = event.getApplicationContext();
        MemoryLogAppender.setApplicationContext(context);
    }
}
