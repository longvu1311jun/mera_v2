package mera.mera_v2.repository;

import mera.mera_v2.entity.CustomerNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerNoteRepository extends JpaRepository<CustomerNote, String> {
}

