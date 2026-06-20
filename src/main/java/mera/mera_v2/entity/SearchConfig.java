package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "search_config",
       indexes = {
           @Index(name = "idx_sync_status", columnList = "sync_status"),
           @Index(name = "idx_pos_phone", columnList = "pos_phone")
       })
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchConfig {

    @Id
    @Column(name = "lark_base_id", nullable = false, length = 64)
    private String larkBaseId;

    @Column(name = "lark_base_name", length = 255)
    private String larkBaseName;

    @Column(name = "lark_obj_type", length = 50)
    private String larkObjType;

    @Column(name = "pos_user_id", length = 100)
    private String posUserId;

    @Column(name = "pos_name", length = 255)
    private String posName;

    @Column(name = "pos_phone", length = 20)
    private String posPhone;

    @Column(name = "department_name", length = 100)
    private String departmentName;

    @Column(name = "khach_hang_table_id", length = 64)
    private String khachHangTableId;

    @Column(name = "khach_hang_view_id", length = 64)
    private String khachHangViewId;

    @Column(name = "lich_hen_table_id", length = 64)
    private String lichHenTableId;

    @Column(name = "lich_hen_view_id", length = 64)
    private String lichHenViewId;

    @Column(name = "trao_doi_table_id", length = 64)
    private String traoDoiTableId;

    @Column(name = "trao_doi_view_id", length = 64)
    private String traoDoiViewId;

    @Column(name = "sync_status", nullable = false)
    private Integer syncStatus;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "last_synced_at")
    private java.time.LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private java.time.LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private java.time.LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = java.time.LocalDateTime.now();
        updatedAt = java.time.LocalDateTime.now();
        if (syncStatus == null) syncStatus = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = java.time.LocalDateTime.now();
    }
}
