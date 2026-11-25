package com.afterme.backend.handover.repository;

import com.afterme.backend.handover.domain.HandoverInvitation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface HandoverInvitationRepository extends JpaRepository<HandoverInvitation, Long> {

    Optional<HandoverInvitation> findByCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from HandoverInvitation invitation where invitation.code = :code")
    Optional<HandoverInvitation> findByCodeForUpdate(@Param("code") String code);
}
