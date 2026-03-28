package mera.mera_v2.repository;

import mera.mera_v2.entity.CustomerAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, String> {
}