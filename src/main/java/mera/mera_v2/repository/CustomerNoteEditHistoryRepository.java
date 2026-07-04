package mera.mera_v2.repository;

import mera.mera_v2.entity.CustomerNoteEditHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;

public interface CustomerNoteEditHistoryRepository extends JpaRepository<CustomerNoteEditHistory, String> {

    @Modifying
    @Query("DELETE FROM CustomerNoteEditHistory h WHERE h.noteId IN :noteIds")
    void deleteByNoteIdIn(@Param("noteIds") Collection<String> noteIds);
}