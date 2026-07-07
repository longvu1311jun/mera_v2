package mera.mera_v2.customer.DTO;

import java.time.LocalDateTime;

/**
 * Projection cho query "Số thả nổi" (khách hàng cảnh báo).
 * Tên getter phải khớp alias cột trong native query của ProblemCustomerRepository.
 */
public interface ProblemCustomerView {

    String getCustomerId();

    String getName();

    String getPhone();

    LocalDateTime getInsertedAt();

    Integer getOrderCount();

    Integer getSucceedOrderCount();

    LocalDateTime getLastOrderAt();

    Integer getNoteCount();

    LocalDateTime getLastNoteAt();
}
