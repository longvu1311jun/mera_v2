package mera.mera_v2.repository;

import mera.mera_v2.entity.Customer;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CustomerRepository extends JpaRepository<Customer, String> {

  List<Customer> findAllByIdIn(List<String> ids);
}

