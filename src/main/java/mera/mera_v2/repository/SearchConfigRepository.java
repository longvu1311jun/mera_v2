package mera.mera_v2.repository;

import mera.mera_v2.entity.SearchConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SearchConfigRepository extends JpaRepository<SearchConfig, String> {

    List<SearchConfig> findBySyncStatusOrderByUpdatedAtDesc(Integer syncStatus);

    List<SearchConfig> findByPosPhoneAndSyncStatusOrderByUpdatedAtDesc(String posPhone, Integer syncStatus);

    List<SearchConfig> findByPosPhoneOrderByUpdatedAtDesc(String posPhone);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE SearchConfig s SET s.syncStatus = 0, s.errorMessage = NULL")
    int resetAllToPending();

    @Modifying
    @Query("UPDATE SearchConfig s SET s.syncStatus = :status, s.errorMessage = :error, s.lastSyncedAt = CURRENT_TIMESTAMP WHERE s.larkBaseId = :baseId")
    int updateStatus(@org.springframework.data.repository.query.Param("baseId") String baseId,
                    @org.springframework.data.repository.query.Param("status") Integer status,
                    @org.springframework.data.repository.query.Param("error") String error);

    List<SearchConfig> findBySyncStatusAndLarkBaseNameIsNotNull(Integer syncStatus);

    @Modifying
    @Query("DELETE FROM SearchConfig")
    void deleteAllRows();

    @Modifying
    @Query(value = "UPDATE search_config SET khach_hang_view_id = :kh, lich_hen_view_id = :lh, trao_doi_view_id = :td WHERE lark_base_id = :baseId", nativeQuery = true)
    int updateViewIds(@org.springframework.data.repository.query.Param("baseId") String baseId,
                     @org.springframework.data.repository.query.Param("kh") String khViewId,
                     @org.springframework.data.repository.query.Param("lh") String lhViewId,
                     @org.springframework.data.repository.query.Param("td") String tdViewId);
}
