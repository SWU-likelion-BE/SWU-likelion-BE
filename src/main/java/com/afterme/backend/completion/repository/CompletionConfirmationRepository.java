package com.afterme.backend.completion.repository;

import com.afterme.backend.completion.domain.CompletionConfirmation;
import com.afterme.backend.handover.domain.ParticipantRole;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface CompletionConfirmationRepository extends JpaRepository<CompletionConfirmation, Long> {
    Optional<CompletionConfirmation> findFirstByProjectIdAndMemberIdAndRoleOrderByConfirmedAtAscIdAsc(
            Long projectId, Long memberId, ParticipantRole role);
    List<CompletionConfirmation> findByProjectIdOrderByConfirmedAtAscIdAsc(Long projectId);
}
