package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Bảng lưu trữ khách hàng "đã được tối ưu": từng nằm trong danh sách Số thả nổi và sau khi sale
 * chăm lại thì đã lên đơn (đang xử lý / đã nhận) nên rời khỏi danh sách.
 *
 * <p>Mỗi khách chỉ giữ một dòng (customer_id UNIQUE); nếu khách quay lại danh sách rồi lại được
 * tối ưu lần nữa, dòng cũ được cập nhật (optimized_at mới).</p>
 */
@Getter
@Setter
@Entity
@Table(name = "optimized_customer")
public class OptimizedCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "customer_id", length = 64, nullable = false, unique = true)
    private String customerId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    /** Số ghi chú tại thời điểm khách còn trong danh sách Số thả nổi. */
    @Column(name = "note_count")
    private int noteCount;

    /** Lý do khi còn bị thả nổi (nhóm A/B/C). */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Ngày tạo KH — chuỗi hiển thị "dd/MM/yyyy HH:mm". */
    @Column(name = "customer_created_text", length = 32)
    private String customerCreatedText;

    /** Tổng đơn khi còn bị thả nổi. */
    @Column(name = "order_count_before")
    private int orderCountBefore;

    /** Đơn thành công khi còn bị thả nổi. */
    @Column(name = "succeed_before")
    private int succeedBefore;

    /** Tổng đơn tại thời điểm phát hiện đã tối ưu (đối chiếu theo mã KH + SĐT). */
    @Column(name = "order_count_after")
    private int orderCountAfter;

    /** Đơn đã nhận tại thời điểm phát hiện đã tối ưu. */
    @Column(name = "succeed_after")
    private int succeedAfter;

    /** Lần đầu khách xuất hiện trong danh sách Số thả nổi. */
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    /** Thời điểm phát hiện khách đã rời danh sách vì đã lên đơn. */
    @Column(name = "optimized_at")
    private LocalDateTime optimizedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public int getNoteCount() { return noteCount; }
    public void setNoteCount(int noteCount) { this.noteCount = noteCount; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getCustomerCreatedText() { return customerCreatedText; }
    public void setCustomerCreatedText(String customerCreatedText) { this.customerCreatedText = customerCreatedText; }

    public int getOrderCountBefore() { return orderCountBefore; }
    public void setOrderCountBefore(int orderCountBefore) { this.orderCountBefore = orderCountBefore; }

    public int getSucceedBefore() { return succeedBefore; }
    public void setSucceedBefore(int succeedBefore) { this.succeedBefore = succeedBefore; }

    public int getOrderCountAfter() { return orderCountAfter; }
    public void setOrderCountAfter(int orderCountAfter) { this.orderCountAfter = orderCountAfter; }

    public int getSucceedAfter() { return succeedAfter; }
    public void setSucceedAfter(int succeedAfter) { this.succeedAfter = succeedAfter; }

    public LocalDateTime getFirstSeenAt() { return firstSeenAt; }
    public void setFirstSeenAt(LocalDateTime firstSeenAt) { this.firstSeenAt = firstSeenAt; }

    public LocalDateTime getOptimizedAt() { return optimizedAt; }
    public void setOptimizedAt(LocalDateTime optimizedAt) { this.optimizedAt = optimizedAt; }
}
