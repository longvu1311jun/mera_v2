package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "lark_pos_sync_log")
public class LarkPosSyncLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "entity_type", length = 64, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, nullable = false)
    private String entityId;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false)
    private SyncDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SyncStatus status = SyncStatus.PENDING;

    @Column(name = "payload", columnDefinition = "LONGTEXT")
    private String payload;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "synced_at")
    private LocalDateTime syncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public enum SyncDirection {
        LARK_TO_POS, POS_TO_LARK
    }

    public enum SyncStatus {
        SUCCESS, FAILED, PENDING
    }
}

