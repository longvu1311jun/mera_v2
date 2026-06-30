package mera.mera_v2.repository;

import mera.mera_v2.entity.LarkSyncJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LarkSyncJobRepository extends JpaRepository<LarkSyncJob, String> {
    
    Optional<LarkSyncJob> findFirstByLockedKeyAndStatusOrderByCreatedAtDesc(String lockedKey, String status);
    
    Optional<LarkSyncJob> findTopByJobTypeAndStatusOrderByCreatedAtDesc(String jobType, String status);
}
