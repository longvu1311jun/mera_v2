package mera.mera_v2.repository;

import java.util.List;
import mera.mera_v2.entity.OrderStatusHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {
  List<OrderStatusHistory> findAllByOrder_IdIn(List<Long> orderIds);
}

