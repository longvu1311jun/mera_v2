package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkEmployee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LarkEmployeeRepository extends JpaRepository<LarkEmployee, String> {
    
    Optional<LarkEmployee> findByOpenId(String openId);
}
