package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkBitableConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LarkBitableConfigRepository extends JpaRepository<LarkBitableConfig, Long> {
    
    List<LarkBitableConfig> findAllByOrderByIdAsc();
    
    Optional<LarkBitableConfig> findByIsDefaultTrue();
    
    Optional<LarkBitableConfig> findByShopIdAndIsDefaultTrue(Long shopId);
    
    List<LarkBitableConfig> findByShopId(Long shopId);
}
