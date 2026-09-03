package mera.mera_v2.repository;

import mera.mera_v2.entity.OrderPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OrderPaymentRepository extends JpaRepository<OrderPayment, Long> {

    /** Payment hien co cua mot don — dung de chong tao trung khi webhook khong gui id payment. */
    List<OrderPayment> findAllByOrderId(Long orderId);
}
