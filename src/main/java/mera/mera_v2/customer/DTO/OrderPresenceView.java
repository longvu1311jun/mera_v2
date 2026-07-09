package mera.mera_v2.customer.DTO;

/**
 * Projection cho query kiểm tra "khách đã có đơn hay chưa" khi đối soát Số thả nổi.
 * Đơn được đối chiếu theo mã KH HOẶC theo SĐT (9 số cuối), trùng logic query gốc.
 * Tên getter phải khớp alias cột trong query của ProblemCustomerRepository.
 */
public interface OrderPresenceView {

    String getCustomerId();

    /** Số đơn đang xử lý (11,1,8,9,2). */
    Integer getActiveCount();

    /** Số đơn đã nhận (3,16). */
    Integer getReceivedCount();

    /** Tổng số đơn (mọi trạng thái). */
    Integer getTotalCount();
}
