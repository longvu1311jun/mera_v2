package mera.mera_v2.pos.assignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mera.mera_v2.entity.OrderAssignmentConfig;
import mera.mera_v2.repository.OrderAssignmentConfigRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderAssignmentConfigService {

    private final OrderAssignmentConfigRepository configRepository;

    // Default config values
    private static final Map<String, String> DEFAULT_CONFIGS = new HashMap<>();

    static {
        // Attendance settings
        DEFAULT_CONFIGS.put("on_time_threshold", "08:00");
        DEFAULT_CONFIGS.put("avg_calculation_include_late", "false");
        
        // Priority settings
        DEFAULT_CONFIGS.put("include_late_in_priority", "false");
        DEFAULT_CONFIGS.put("streak_enabled", "false");
        DEFAULT_CONFIGS.put("streak_threshold", "2");
        
        // Assignment settings
        DEFAULT_CONFIGS.put("auto_assign_enabled", "true");
        DEFAULT_CONFIGS.put("max_assignments_per_employee", "10");
    }

    @PostConstruct
    public void initDefaultConfigs() {
        for (Map.Entry<String, String> entry : DEFAULT_CONFIGS.entrySet()) {
            if (!configRepository.existsByConfigKey(entry.getKey())) {
                OrderAssignmentConfig config = OrderAssignmentConfig.builder()
                        .configKey(entry.getKey())
                        .configValue(entry.getValue())
                        .description(getConfigDescription(entry.getKey()))
                        .build();
                configRepository.save(config);
                log.info("Created default config: {} = {}", entry.getKey(), entry.getValue());
            }
        }
    }

    private String getConfigDescription(String key) {
        return switch (key) {
            case "on_time_threshold" -> "Giờ điểm danh tối thiểu để được coi là đúng giờ (HH:mm)";
            case "avg_calculation_include_late" -> "Tính trung bình có bao gồm nhóm đi muộn không (true/false)";
            case "include_late_in_priority" -> "Cho phép nhóm muộn được nhận ưu tiên nếu ít khách (true/false)";
            case "streak_enabled" -> "Bật tính năng streak (ngày đi đúng giờ liên tiếp) (true/false)";
            case "streak_threshold" -> "Ngưỡng streak tối thiểu để được siêu ưu tiên (số ngày)";
            case "auto_assign_enabled" -> "Bật tự động phân công khi có khách hàng mới (true/false)";
            case "max_assignments_per_employee" -> "Số khách tối đa mỗi nhân viên có thể nhận trong ngày";
            case "attendance_sync_enabled" -> "Bật/tắt scheduler đồng bộ điểm danh Lark (true/false)";
            case "order_assignment_enabled" -> "Bật/tắt chức năng phân chia khách hàng (true/false)";
            default -> "";
        };
    }

    public Map<String, String> getAllConfigs() {
        List<OrderAssignmentConfig> configs = configRepository.findAll();
        Map<String, String> result = new HashMap<>();
        for (OrderAssignmentConfig config : configs) {
            result.put(config.getConfigKey(), config.getConfigValue());
        }
        return result;
    }

    public String getConfig(String key) {
        return configRepository.findByConfigKey(key)
                .map(OrderAssignmentConfig::getConfigValue)
                .orElse(DEFAULT_CONFIGS.getOrDefault(key, ""));
    }

    public boolean getConfigAsBoolean(String key) {
        String value = getConfig(key);
        return "true".equalsIgnoreCase(value);
    }

    public int getConfigAsInt(String key) {
        String value = getConfig(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer config for key {}: {}", key, value);
            return 0;
        }
    }

    @Transactional
    public OrderAssignmentConfig updateConfig(String key, String value) {
        Optional<OrderAssignmentConfig> existingConfig = configRepository.findByConfigKey(key);
        
        if (existingConfig.isPresent()) {
            OrderAssignmentConfig config = existingConfig.get();
            config.setConfigValue(value);
            return configRepository.save(config);
        } else {
            OrderAssignmentConfig newConfig = OrderAssignmentConfig.builder()
                    .configKey(key)
                    .configValue(value)
                    .description(getConfigDescription(key))
                    .build();
            return configRepository.save(newConfig);
        }
    }

    @Transactional
    public Map<String, String> updateConfigs(Map<String, String> updates) {
        Map<String, String> results = new HashMap<>();
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            OrderAssignmentConfig saved = updateConfig(entry.getKey(), entry.getValue());
            results.put(saved.getConfigKey(), saved.getConfigValue());
        }
        return results;
    }

    public List<OrderAssignmentConfig> getAllConfigEntities() {
        return configRepository.findAll();
    }
}
