package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkDepartment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LarkDepartmentRepository extends JpaRepository<LarkDepartment, String> {
    
    Optional<LarkDepartment> findByOpenId(String openId);
}
