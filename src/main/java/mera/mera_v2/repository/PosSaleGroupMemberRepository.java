package mera.mera_v2.repository;

import mera.mera_v2.entity.PosSaleGroupMember;
import mera.mera_v2.entity.PosSaleGroupMemberId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PosSaleGroupMemberRepository extends JpaRepository<PosSaleGroupMember, PosSaleGroupMemberId> {
}

