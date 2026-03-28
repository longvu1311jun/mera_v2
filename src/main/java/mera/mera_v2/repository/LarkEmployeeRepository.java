package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkEmployee;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LarkEmployeeRepository extends JpaRepository<LarkEmployee, String> {
}

