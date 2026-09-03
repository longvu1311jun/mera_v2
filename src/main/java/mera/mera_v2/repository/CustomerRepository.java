package mera.mera_v2.repository;

import mera.mera_v2.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {

  List<Customer> findAllByIdIn(List<String> ids);

  /**
   * Cập nhật đúng 3 cột mà webhook thực sự biết, trong một transaction ngắn nhất có thể.
   *
   * Không dùng save(entity) ở luồng webhook: Hibernate sẽ sinh UPDATE toàn bộ cột (kể cả
   * lt_count) nên vừa ghi đè lt_count cũ đã đọc (lost update khi job LT chạy song song),
   * vừa kéo dài thời gian giữ X-lock trên dòng customers.
   *
   * @return số dòng được cập nhật (0 = khách chưa tồn tại)
   */
  @Modifying
  @Transactional
  @Query(value = "UPDATE customers SET name = :name, shop_id = :shopId, updated_at = :updatedAt "
      + "WHERE id = :id", nativeQuery = true)
  int updateBasicInfo(@Param("id") String id,
                      @Param("name") String name,
                      @Param("shopId") Long shopId,
                      @Param("updatedAt") LocalDateTime updatedAt);
}
