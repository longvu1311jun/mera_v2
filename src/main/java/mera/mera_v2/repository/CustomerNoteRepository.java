package mera.mera_v2.repository;

import mera.mera_v2.entity.CustomerNote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, String> {

    List<CustomerNote> findByCustomerId(String customerId);

    List<CustomerNote> findByCustomerIdIn(Collection<String> customerIds);

    @Modifying
    @Query("DELETE FROM CustomerNote n WHERE n.customerId IN :customerIds")
    void deleteByCustomerIdIn(@Param("customerIds") Collection<String> customerIds);
}