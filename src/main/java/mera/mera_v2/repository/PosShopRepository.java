package mera.mera_v2.repository;

import mera.mera_v2.entity.PosShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PosShopRepository extends JpaRepository<PosShop, Long> {
}
