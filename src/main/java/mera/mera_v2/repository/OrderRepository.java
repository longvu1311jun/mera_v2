package mera.mera_v2.repository;

import mera.mera_v2.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OrderRepository extends JpaRepository<Order, Long> {

  List<Order> findAllByIdIn(List<Long> ids);

  @Query("SELECT o.id FROM Order o WHERE o.id IN :ids")
  List<Long> findExistingIds(@Param("ids") List<Long> ids);
}

