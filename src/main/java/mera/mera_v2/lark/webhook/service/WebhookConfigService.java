package mera.mera_v2.lark.webhook.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Service quản lý config bật/tắt xử lý webhook theo status
 */
@Slf4j
@Service
public class WebhookConfigService {
    
    // Config cho status = 1 (đã xác nhận)
    @Getter
    private final AtomicBoolean processStatus1 = new AtomicBoolean(true);
    
    // Config cho status = 6
    @Getter
    private final AtomicBoolean processStatus6 = new AtomicBoolean(false);
    
    /**
     * Bật/tắt xử lý cho status = 1
     */
    public void setProcessStatus1(boolean enabled) {
        processStatus1.set(enabled);
        log.info("⚙️ Config: Process status=1 is now {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Bật/tắt xử lý cho status = 6
     */
    public void setProcessStatus6(boolean enabled) {
        processStatus6.set(enabled);
        log.info("⚙️ Config: Process status=6 is now {}", enabled ? "ENABLED" : "DISABLED");
    }
    
    /**
     * Kiểm tra có nên xử lý status này không
     */
    public boolean shouldProcess(int status) {
        if (status == 1) {
            return processStatus1.get();
        } else if (status == 6) {
            return processStatus6.get();
        }
        return false;
    }
    
    /**
     * Lấy tất cả config hiện tại
     */
    public ConfigStatus getConfig() {
        return new ConfigStatus(
            processStatus1.get(),
            processStatus6.get()
        );
    }
    
    /**
     * DTO cho response
     */
    @Getter
    public static class ConfigStatus {
        private final boolean status1Enabled;
        private final boolean status6Enabled;
        
        public ConfigStatus(boolean status1Enabled, boolean status6Enabled) {
            this.status1Enabled = status1Enabled;
            this.status6Enabled = status6Enabled;
        }
    }
}
