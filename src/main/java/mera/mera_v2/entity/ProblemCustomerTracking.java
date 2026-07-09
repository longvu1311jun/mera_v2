package mera.mera_v2.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/**
 * Snapshot "ai đang nằm trong danh sách Số thả nổi" tại lần đối soát gần nhất.
 *
 * <p>Trang Số thả nổi được tính động (query nặng) nên không có sự kiện "khách rời danh sách".
 * Bảng này lưu lại tập thành viên hiện tại để mỗi lần job đối soát chạy có thể so sánh và phát
 * hiện ai vừa rời đi. Khách rời đi vì đã lên đơn (đang xử lý / đã nhận) sẽ được ghi vào
 * {@link OptimizedCustomer}; rời vì lý do khác thì chỉ xoá khỏi bảng này.</p>
 */
@Getter
@Setter
@Entity
@Table(name = "problem_customer_tracking")
public class ProblemCustomerTracking {

    /** Mã khách hàng (khoá chính) — trùng với id ở bảng customers. */
    @Id
    @Column(name = "customer_id", length = 64, nullable = false)
    private String customerId;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    /** Số ghi chú còn hiệu lực tại thời điểm snapshot. */
    @Column(name = "note_count")
    private int noteCount;

    /** Tổng đơn tại thời điểm khách còn trong danh sách. */
    @Column(name = "order_count")
    private int orderCount;

    /** Đơn thành công (đã nhận) tại thời điểm khách còn trong danh sách. */
    @Column(name = "succeed_order_count")
    private int succeedOrderCount;

    /** Lý do cảnh báo (nhóm A/B/C) tại thời điểm snapshot — dạng text hiển thị. */
    @Column(name = "reason", length = 500)
    private String reason;

    /** Ngày tạo KH — lưu dạng chuỗi hiển thị "dd/MM/yyyy HH:mm" (chỉ để trình bày). */
    @Column(name = "customer_created_text", length = 32)
    private String customerCreatedText;

    /** Lần đầu tiên khách xuất hiện trong danh sách Số thả nổi. */
    @Column(name = "first_seen_at")
    private LocalDateTime firstSeenAt;

    /** Lần đối soát gần nhất còn thấy khách trong danh sách. */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;
}
