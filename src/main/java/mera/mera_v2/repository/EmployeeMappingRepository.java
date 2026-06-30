package mera.mera_v2.repository;

import mera.mera_v2.entity.EmployeeMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeMappingRepository extends JpaRepository<EmployeeMapping, Long> {

    Optional<EmployeeMapping> findByLarkEmployeeId(String larkEmployeeId);

    Optional<EmployeeMapping> findByPosUserId(String posUserId);

    @Query("SELECT e FROM EmployeeMapping e WHERE " +
           "LOWER(e.larkEmployeeName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "LOWER(e.posUserName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
           "e.larkEmployeeId = :query OR " +
           "e.posUserId = :query OR " +
           "e.larkEmployeeNo LIKE CONCAT('%', :query, '%')")
    List<EmployeeMapping> searchByQuery(@Param("query") String query);

    List<EmployeeMapping> findByIsMappedTrue();

    List<EmployeeMapping> findByIsMappedFalse();

    @Query("SELECT COUNT(e) FROM EmployeeMapping e WHERE e.isMapped = true")
    long countMapped();

    @Query("SELECT COUNT(e) FROM EmployeeMapping e WHERE e.isMapped = false")
    long countUnmapped();

    @Query("SELECT e FROM EmployeeMapping e ORDER BY e.larkEmployeeName ASC")
    List<EmployeeMapping> findAllOrderByLarkName();

    @Query("SELECT e FROM EmployeeMapping e ORDER BY e.posUserName ASC")
    List<EmployeeMapping> findAllOrderByPosName();
}
