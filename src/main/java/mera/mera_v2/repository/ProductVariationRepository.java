package mera.mera_v2.repository;

import mera.mera_v2.entity.ProductVariation;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductVariationRepository extends JpaRepository<ProductVariation, String> {
}