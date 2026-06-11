package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Lưu pending notification cần check sau 30 phút.
 * Sau khi tao record Bitable, neu link_record_ids = null thi tao ban ghi nay.
 * Scheduler se doc, search theo SDT, gui tin nhan neu van chua co link.
 */
@Getter
@Setter
@Entity
@Table(name = "pending_followup_notifications", indexes = {
    @Index(name = "idx_pending_scheduled_at", columnList = "scheduled_at"),
    @Index(name = "idx_pending_processed", columnList = "processed")
})
public class PendingFollowupNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Số điện thoại khách hàng */
    @Column(name = "phone_number", length = 20, nullable = false)
    private String phoneNumber;

    /** Base ID (appToken) của bảng Khách hàng cần search */
    @Column(name = "base_id", length = 64, nullable = false)
    private String baseId;

    /** Table ID của bảng Khách hàng */
    @Column(name = "table_id", length = 64, nullable = false)
    private String tableId;

    /** View ID để search */
    @Column(name = "view_id", length = 64)
    private String viewId;

    /** Tên khách hàng (để đưa vào tin nhắn) */
    @Column(name = "customer_name", length = 255)
    private String customerName;

    /** ID record vừa tạo trong bảng Liệu trình (dùng để ghi log) */
    @Column(name = "created_record_id", length = 64)
    private String createdRecordId;

    /** Thời điểm cần check (created_at + 30 phút) */
    @Column(name = "scheduled_at", nullable = false)
    private LocalDateTime scheduledAt;

    /** Da xu ly chua */
    @Column(name = "processed", nullable = false)
    private Boolean processed = false;

    /** Thoi diem xu ly (neu da xu ly) */
    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    /** Ghi chú / ket qua */
    @Column(name = "note", length = 500)
    private String note;

    /** So lan retry */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /** Thoi diem tao */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) createdAt = LocalDateTime.now();
        if (scheduledAt == null) scheduledAt = LocalDateTime.now().plusMinutes(30);
        if (processed == null) processed = false;
        if (retryCount == null) retryCount = 0;
    }
}
