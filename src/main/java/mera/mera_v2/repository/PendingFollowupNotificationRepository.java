package mera.mera_v2.repository;

import mera.mera_v2.entity.PendingFollowupNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PendingFollowupNotificationRepository extends JpaRepository<PendingFollowupNotification, Long> {

    /**
     * Lay tat ca ban ghi chua xu ly va da den gio can xu ly.
     * Order by created_at ASC de xu ly theo thu tu.
     */
    @Query("SELECT p FROM PendingFollowupNotification p " +
           "WHERE p.processed = false " +
           "AND p.scheduledAt <= :now " +
           "ORDER BY p.createdAt ASC")
    List<PendingFollowupNotification> findPendingDue(@Param("now") LocalDateTime now);

    /**
     * Lay ban ghi chua xu ly (chua den gio) — dung de log.
     */
    List<PendingFollowupNotification> findByProcessedFalseOrderByScheduledAtAsc();

    /**
     * Xoa ban ghi da xu ly qua x (luu keep 7 ngay).
     */
    void deleteByProcessedTrueAndProcessedAtBefore(LocalDateTime before);

    /**
     * Dem so ban ghi chua xu ly.
     */
    long countByProcessedFalse();
}
