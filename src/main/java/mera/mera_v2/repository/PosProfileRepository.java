package mera.mera_v2.repository;

import mera.mera_v2.entity.PosProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PosProfileRepository extends JpaRepository<PosProfile, String> {
}
