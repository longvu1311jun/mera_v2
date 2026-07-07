package mera.mera_v2.repository;

import mera.mera_v2.entity.CustomerPhoneNumber;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CustomerPhoneNumberRepository extends JpaRepository<CustomerPhoneNumber, Long> {

    List<CustomerPhoneNumber> findByCustomerId(String customerId);

    List<CustomerPhoneNumber> findByCustomerIdIn(Collection<String> customerIds);

    @Query("SELECT c FROM CustomerPhoneNumber c WHERE c.customerId = :customerId AND REPLACE(REPLACE(REPLACE(c.phoneNumber, '-', ''), ' ', ''), '.', '') = :normalizedPhone")
    List<CustomerPhoneNumber> findByCustomerIdAndNormalizedPhone(@Param("customerId") String customerId, @Param("normalizedPhone") String normalizedPhone);
}