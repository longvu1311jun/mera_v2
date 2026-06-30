package mera.mera_v2.repository;

import mera.mera_v2.entity.OrderAssignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OrderAssignmentRepository extends JpaRepository<OrderAssignment, Long> {

    List<OrderAssignment> findByOrderId(Long orderId);

    List<OrderAssignment> findByLarkEmployeeId(String larkEmployeeId);

    @Query("SELECT oa FROM OrderAssignment oa WHERE oa.assignedAt >= :startOfDay AND oa.assignedAt < :endOfDay ORDER BY oa.assignedAt DESC")
    List<OrderAssignment> findTodayAssignments(
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );

    @Query("SELECT COUNT(oa) FROM OrderAssignment oa WHERE oa.larkEmployeeId = :larkEmployeeId AND oa.assignedAt >= :startOfDay AND oa.assignedAt < :endOfDay")
    long countTodayByEmployee(
            @Param("larkEmployeeId") String larkEmployeeId,
            @Param("startOfDay") LocalDateTime startOfDay,
            @Param("endOfDay") LocalDateTime endOfDay
    );
}
