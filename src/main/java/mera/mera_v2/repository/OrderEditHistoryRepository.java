package mera.mera_v2.repository;

import mera.mera_v2.entity.OrderEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderEditHistoryRepository extends JpaRepository<OrderEditHistory, Long> {
}

