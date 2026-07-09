package mera.mera_v2.repository;

import java.util.List;
import java.util.Optional;

import mera.mera_v2.entity.OptimizedCustomer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Bảng lưu trữ khách hàng đã được tối ưu (rời danh sách Số thả nổi vì đã lên đơn).
 */
@Repository
public interface OptimizedCustomerRepository extends JpaRepository<OptimizedCustomer, Long> {

    Optional<OptimizedCustomer> findByCustomerId(String customerId);

    /** Danh sách hiển thị: tối ưu gần nhất lên đầu. */
    List<OptimizedCustomer> findAllByOrderByOptimizedAtDesc();
}
