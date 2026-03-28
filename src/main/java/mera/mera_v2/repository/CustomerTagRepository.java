package mera.mera_v2.repository;

import mera.mera_v2.entity.CustomerTag;
import mera.mera_v2.entity.CustomerTagId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerTagRepository extends JpaRepository<CustomerTag, CustomerTagId> {
}

