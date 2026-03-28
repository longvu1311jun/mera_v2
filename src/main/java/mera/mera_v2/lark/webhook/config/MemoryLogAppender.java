package mera.mera_v2.lark.webhook.config;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.classic.spi.ThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import mera.mera_v2.lark.webhook.service.LogStorageService;
import org.springframework.context.ApplicationContext;

/**
 * Custom Logback Appender để lưu log vào memory
 */
public class MemoryLogAppender extends AppenderBase<ILoggingEvent> {
    
    private static ApplicationContext applicationContext;
    private static LogStorageService logStorageService;
    
    /**
     * Được gọi từ Spring để set application context
     */
    public static void setApplicationContext(ApplicationContext context) {
        MemoryLogAppender.applicationContext = context;
        if (context != null) {
            try {
                logStorageService = context.getBean(LogStorageService.class);
            } catch (Exception e) {
                // LogStorageService chưa được khởi tạo
            }
        }
    }
    
    @Override
    protected void append(ILoggingEvent event) {
        // Lấy LogStorageService từ Spring context nếu chưa có
        if (logStorageService == null && applicationContext != null) {
            try {
                logStorageService = applicationContext.getBean(LogStorageService.class);
            } catch (Exception e) {
                // Ignore - có thể context chưa sẵn sàng
                return;
            }
        }
        
        if (logStorageService != null) {
            String level = event.getLevel().toString();
            String logger = event.getLoggerName();
            String message = event.getFormattedMessage();
            String thread = event.getThreadName();
            Throwable throwable = null;
            String stackTrace = null;
            
            // Lấy throwable từ event
            if (event.getThrowableProxy() != null) {
                ThrowableProxy throwableProxy = (ThrowableProxy) event.getThrowableProxy();
                if (throwableProxy.getThrowable() != null) {
                    throwable = throwableProxy.getThrowable();
                } else {
                    // Nếu không lấy được throwable object, build stack trace từ proxy
                    stackTrace = buildStackTraceFromProxy(throwableProxy);
                }
            }
            
            // Nếu có stack trace riêng, thêm vào message
            if (stackTrace != null && throwable == null) {
                String fullMessage = message + "\n" + stackTrace;
                logStorageService.addLog(level, logger, fullMessage, thread, null);
            } else {
                logStorageService.addLog(level, logger, message, thread, throwable);
            }
        }
    }
    
    private String buildStackTraceFromProxy(ThrowableProxy proxy) {
        StringBuilder sb = new StringBuilder();
        sb.append(proxy.getClassName()).append(": ").append(proxy.getMessage()).append("\n");
        
        StackTraceElementProxy[] stackTrace = proxy.getStackTraceElementProxyArray();
        if (stackTrace != null) {
            for (StackTraceElementProxy element : stackTrace) {
                sb.append("  at ").append(element.getStackTraceElement()).append("\n");
            }
        }
        
        return sb.toString();
    }
}
