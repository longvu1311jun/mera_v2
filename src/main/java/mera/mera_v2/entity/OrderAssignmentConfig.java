package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "order_assignment_config")
public class OrderAssignmentConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, unique = true)
    private String configKey;

    @Column(name = "config_value", nullable = false, length = 1000)
    private String configValue;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public OrderAssignmentConfig() {}

    public OrderAssignmentConfig(Long id, String configKey, String configValue, String description, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.configKey = configKey;
        this.configValue = configValue;
        this.description = description;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getConfigKey() { return configKey; }
    public void setConfigKey(String configKey) { this.configKey = configKey; }

    public String getConfigValue() { return configValue; }
    public void setConfigValue(String configValue) { this.configValue = configValue; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }

    public static OrderAssignmentConfigBuilder builder() { return new OrderAssignmentConfigBuilder(); }

    public static class OrderAssignmentConfigBuilder {
        private Long id;
        private String configKey;
        private String configValue;
        private String description;
        private LocalDateTime createdAt;
        private LocalDateTime updatedAt;

        public OrderAssignmentConfigBuilder id(Long id) { this.id = id; return this; }
        public OrderAssignmentConfigBuilder configKey(String configKey) { this.configKey = configKey; return this; }
        public OrderAssignmentConfigBuilder configValue(String configValue) { this.configValue = configValue; return this; }
        public OrderAssignmentConfigBuilder description(String description) { this.description = description; return this; }
        public OrderAssignmentConfigBuilder createdAt(LocalDateTime createdAt) { this.createdAt = createdAt; return this; }
        public OrderAssignmentConfigBuilder updatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; return this; }
        public OrderAssignmentConfig build() {
            OrderAssignmentConfig config = new OrderAssignmentConfig(id, configKey, configValue, description, createdAt, updatedAt);
            return config;
        }
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
