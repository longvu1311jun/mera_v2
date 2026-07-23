package mera.mera_v2.customer.DTO;

/**
 * Kết quả khớp SĐT (9 số cuối) → mã khách hàng, dùng khi tải danh sách "Từ chối chăm" từ Excel.
 */
public interface PhoneMatchView {
    /** 9 số cuối của SĐT (khoá khớp). */
    String getP9();

    /** Mã khách hàng khớp với SĐT này. */
    String getCustomerId();
}
