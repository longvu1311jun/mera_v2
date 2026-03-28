package mera.mera_v2.repository;

import mera.mera_v2.entity.VariationWarehouseStock;
import mera.mera_v2.entity.VariationWarehouseStockId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VariationWarehouseStockRepository extends JpaRepository<VariationWarehouseStock, VariationWarehouseStockId> {
}

