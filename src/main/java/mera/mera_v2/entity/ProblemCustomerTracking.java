package mera.mera_v2.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * Snapshot "ai đang nằm trong danh sách Số thả nổi" tại lần đối soát gần nhất.
 */
@Entity
@Table(name = "problem_customer_tracking")
public class ProblemCustomerTracking {

    @Id
    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    @Column(name = "note_count")
    private int noteCount;

    @Column(name = "order_count")
    private int orderCount;

    @Column(name = "succeed_order_count")
    private int succeedOrderCount;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "customer_created_text", length = 32)
    private String customerCreatedText;

    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    // Getters
    public String getCustomerId() { return customerId; }
    public String getName() { return name; }
    public String getPhone() { return phone; }
    public int getNoteCount() { return noteCount; }
    public int getOrderCount() { return orderCount; }
    public int getSucceedOrderCount() { return succeedOrderCount; }
    public String getReason() { return reason; }
    public String getCustomerCreatedText() { return customerCreatedText; }
    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public LocalDateTime getLastSeenAt() { return lastSeenAt; }

    // Setters
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public void setName(String name) { this.name = name; }
    public void setPhone(String phone) { this.phone = phone; }
    public void setNoteCount(int noteCount) { this.noteCount = noteCount; }
    public void setOrderCount(int orderCount) { this.orderCount = orderCount; }
    public void setSucceedOrderCount(int succeedOrderCount) { this.succeedOrderCount = succeedOrderCount; }
    public void setReason(String reason) { this.reason = reason; }
    public void setCustomerCreatedText(String customerCreatedText) { this.customerCreatedText = customerCreatedText; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }
    public void setLastSeenAt(LocalDateTime lastSeenAt) { this.lastSeenAt = lastSeenAt; }
}
