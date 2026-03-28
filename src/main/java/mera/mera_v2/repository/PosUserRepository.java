package mera.mera_v2.repository;

import mera.mera_v2.entity.PosUser;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosUserRepository extends JpaRepository<PosUser, String> {
}