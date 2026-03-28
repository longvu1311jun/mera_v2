package mera.mera_v2.repository;

import mera.mera_v2.entity.OrderTag;
import mera.mera_v2.entity.OrderTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderTagRepository extends JpaRepository<OrderTag, OrderTagId> {
}