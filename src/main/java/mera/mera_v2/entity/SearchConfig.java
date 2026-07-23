package mera.mera_v2.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "search_config",
       indexes = {
           @Index(name = "idx_sync_status", columnList = "sync_status"),
           @Index(name = "idx_pos_phone", columnList = "pos_phone")
       })
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
    private LocalDateTime lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (syncStatus == null) syncStatus = 0;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // All getters
    public String getLarkBaseId() { return larkBaseId; }
    public String getLarkBaseName() { return larkBaseName; }
    public String getLarkObjType() { return larkObjType; }
    public String getPosUserId() { return posUserId; }
    public String getPosName() { return posName; }
    public String getPosPhone() { return posPhone; }
    public String getDepartmentName() { return departmentName; }
    public String getKhachHangTableId() { return khachHangTableId; }
    public String getKhachHangViewId() { return khachHangViewId; }
    public String getLichHenTableId() { return lichHenTableId; }
    public String getLichHenViewId() { return lichHenViewId; }
    public String getTraoDoiTableId() { return traoDoiTableId; }
    public String getTraoDoiViewId() { return traoDoiViewId; }
    public Integer getSyncStatus() { return syncStatus; }
    public String getErrorMessage() { return errorMessage; }
    public LocalDateTime getLastSyncedAt() { return lastSyncedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    // All setters
    public void setLarkBaseId(String larkBaseId) { this.larkBaseId = larkBaseId; }
    public void setLarkBaseName(String larkBaseName) { this.larkBaseName = larkBaseName; }
    public void setLarkObjType(String larkObjType) { this.larkObjType = larkObjType; }
    public void setPosUserId(String posUserId) { this.posUserId = posUserId; }
    public void setPosName(String posName) { this.posName = posName; }
    public void setPosPhone(String posPhone) { this.posPhone = posPhone; }
    public void setDepartmentName(String departmentName) { this.departmentName = departmentName; }
    public void setKhachHangTableId(String khachHangTableId) { this.khachHangTableId = khachHangTableId; }
    public void setKhachHangViewId(String khachHangViewId) { this.khachHangViewId = khachHangViewId; }
    public void setLichHenTableId(String lichHenTableId) { this.lichHenTableId = lichHenTableId; }
    public void setLichHenViewId(String lichHenViewId) { this.lichHenViewId = lichHenViewId; }
    public void setTraoDoiTableId(String traoDoiTableId) { this.traoDoiTableId = traoDoiTableId; }
    public void setTraoDoiViewId(String traoDoiViewId) { this.traoDoiViewId = traoDoiViewId; }
    public void setSyncStatus(Integer syncStatus) { this.syncStatus = syncStatus; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public void setLastSyncedAt(LocalDateTime lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public void setUpdatedAt(LocalDateTime updatedAt) { this.updatedAt = updatedAt; }
}
