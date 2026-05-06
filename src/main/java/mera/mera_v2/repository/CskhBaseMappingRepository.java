package mera.mera_v2.repository;

import mera.mera_v2.entity.CskhBaseMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CskhBaseMappingRepository extends JpaRepository<CskhBaseMapping, Long> {

    List<CskhBaseMapping> findAllByOrderByIdAsc();

    List<CskhBaseMapping> findByIsActiveTrueOrderByIdAsc();

    // Tìm theo phone - trả về list để handle duplicates
    List<CskhBaseMapping> findByPosPhone(String posPhone);

    // Chỉ tìm mapping đang active
    List<CskhBaseMapping> findByPosPhoneAndIsActiveTrue(String posPhone);

    Optional<CskhBaseMapping> findByLarkBaseId(String larkBaseId);

    Optional<CskhBaseMapping> findByPosNameIgnoreCase(String posName);

    @Modifying
    @Query("UPDATE CskhBaseMapping c SET c.isActive = false")
    void deactivateAll();

    @Modifying
    @Query("DELETE FROM CskhBaseMapping c WHERE c.larkBaseId = :baseId")
    void deleteByLarkBaseId(@Param("baseId") String larkBaseId);

    List<CskhBaseMapping> findByKhachHangTableIdIsNotNull();
}
