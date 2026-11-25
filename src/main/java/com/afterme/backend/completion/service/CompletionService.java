package com.afterme.backend.completion.service;

import com.afterme.backend.completion.domain.*;
import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.completion.repository.*;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.*;
import com.afterme.backend.handover.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletionService {
    private final HandoverProjectRepository projects;
    private final ProjectParticipantRepository participants;
    private final CompletionCriteriaRepository criteriaRepository;
    private final CompletionConfirmationRepository confirmations;
    private final Clock clock;

    @Transactional
    public Criteria updateCriteria(Long userId, Long projectId, UpdateCriteria request) {
        requireRole(userId, projectId, ParticipantRole.PREDECESSOR);
        HandoverProject project = lockedProject(projectId);
        requireMutable(project);
        CompletionCriteria criteria = criteria(projectId);
        if (!Objects.equals(request.revision(), criteria.getRevision())) fail(ErrorCode.CONFLICT);
        criteria.update(request.minimumCompleteness(), request.quizPassingScore(), userId, clock.instant());
        return view(criteriaRepository.save(criteria));
    }

    @Transactional
    public Confirmation confirm(Long userId, Long projectId, Confirm request) {
        ProjectParticipant participant = requireRole(userId, projectId, request.role());
        HandoverProject project = lockedProject(projectId);
        Long memberId = participant.getMember().getId();
        var existing = confirmations.findFirstByProjectIdAndMemberIdAndRoleOrderByConfirmedAtAscIdAsc(
                projectId, memberId, request.role());
        if (existing.isPresent()) return view(existing.get());
        requireMutable(project);
        CompletionCriteria criteria = criteria(projectId);
        CompletionConfirmation confirmation = confirmations.save(CompletionConfirmation.confirm(
                projectId, memberId, request.role(), criteria.getRevision(), clock.instant()));
        return view(confirmation);
    }

    public State state(Long userId, Long projectId) {
        HandoverProject project = requireAccess(userId, projectId);
        CompletionCriteria criteria = criteria(projectId);
        var recordedConfirmations = confirmations.findByProjectIdOrderByConfirmedAtAscIdAsc(projectId);
        List<ParticipantConfirmation> items = participants.findParticipantsByProjectId(projectId).stream()
                .filter(p -> p.getMember().isActive())
                .map(p -> {
                    var confirmation = recordedConfirmations.stream().filter(c -> c.getMemberId().equals(p.getMember().getId())
                            && c.getRole() == p.getRole()).findFirst();
                    return new ParticipantConfirmation(p.getMember().getId(), p.getMember().getUser().getId(),
                            p.getMember().getUser().getName(), p.getRole(), confirmation.isPresent(),
                            confirmation.map(CompletionConfirmation::getConfirmedAt).orElse(null));
                }).toList();
        // Identity/role confirmations alone cannot establish completeness or approve a project.
        return new State(projectId, project.getStatus(), project.getCompletedAt(), view(criteria), items,
                allConfirmed(items, ParticipantRole.PREDECESSOR), allConfirmed(items, ParticipantRole.SUCCESSOR), false);
    }

    private boolean allConfirmed(List<ParticipantConfirmation> items, ParticipantRole role) {
        var roleItems = items.stream().filter(p -> p.role() == role).toList();
        return !roleItems.isEmpty() && roleItems.stream().allMatch(ParticipantConfirmation::confirmed);
    }

    private CompletionCriteria criteria(Long projectId) {
        return criteriaRepository.findById(projectId).orElseGet(() -> CompletionCriteria.defaults(projectId));
    }

    private HandoverProject requireAccess(Long userId, Long projectId) {
        return projects.findAccessibleById(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private ProjectParticipant requireRole(Long userId, Long projectId, ParticipantRole role) {
        requireAccess(userId, projectId);
        return participants.findParticipantsByProjectId(projectId).stream()
                .filter(p -> p.getRole() == role && p.getMember().isActive() && p.getMember().getUser().getId().equals(userId))
                .findFirst().orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));
    }

    private HandoverProject lockedProject(Long projectId) {
        return projects.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireMutable(HandoverProject project) {
        if (project.getCompletedAt() != null || "COMPLETED".equals(project.getStatus())) fail(ErrorCode.CONFLICT);
    }

    private Criteria view(CompletionCriteria c) {
        return new Criteria(c.getMinimumCompleteness(), c.getQuizPassingScore(), c.getRevision(), c.getUpdatedBy(), c.getUpdatedAt());
    }

    private Confirmation view(CompletionConfirmation c) {
        return new Confirmation(c.getId(), c.getMemberId(), c.getRole(), c.getCriteriaRevision(), c.getConfirmedAt());
    }

    private static void fail(ErrorCode code) { throw new BusinessException(code); }
}
