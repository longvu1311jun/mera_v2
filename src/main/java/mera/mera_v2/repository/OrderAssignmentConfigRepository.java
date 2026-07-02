package mera.mera_v2.repository;

import mera.mera_v2.entity.OrderAssignmentConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderAssignmentConfigRepository extends JpaRepository<OrderAssignmentConfig, Long> {

    Optional<OrderAssignmentConfig> findByConfigKey(String configKey);

    boolean existsByConfigKey(String configKey);
}
