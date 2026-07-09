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
}
