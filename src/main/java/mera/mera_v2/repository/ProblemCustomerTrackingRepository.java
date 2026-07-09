package mera.mera_v2.repository;

import mera.mera_v2.entity.ProblemCustomerTracking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Truy cập bảng snapshot thành viên danh sách Số thả nổi (dùng cho job đối soát).
 */
@Repository
public interface ProblemCustomerTrackingRepository extends JpaRepository<ProblemCustomerTracking, String> {
}
