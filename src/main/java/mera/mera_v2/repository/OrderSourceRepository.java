package mera.mera_v2.repository;

import mera.mera_v2.entity.OrderSource;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSourceRepository extends JpaRepository<OrderSource, String> {
}