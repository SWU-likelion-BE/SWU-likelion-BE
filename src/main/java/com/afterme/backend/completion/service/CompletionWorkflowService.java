package com.afterme.backend.completion.service;

import com.afterme.backend.completion.domain.*;
import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.completion.repository.*;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.learning.service.LearningAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompletionWorkflowService {
    private final LearningAccess access;
    private final CompletionService confirmations;
    private final ReadinessService readiness;
    private final CompletionWorkflowRepository workflows;
    private final CompletionAuditRepository audits;
    private final Clock clock;

    public record Approval(String status, Long requestedBy, Instant requestedAt, Long approvedBy, Instant approvedAt) {}
    public record Details(Long projectId, String projectStatus, OffsetDateTime completedAt, Criteria criteria,
                          List<ParticipantConfirmation> participants, boolean predecessorConfirmed, boolean successorConfirmed,
                          boolean eligibilityEvaluated, ReadinessService.Evaluation readiness, Approval approval,
                          boolean requestRequirementsMet, boolean approvalRequirementsMet) {}

    public Details state(Long userId, Long projectId) {
        var base = confirmations.state(userId, projectId);
        var evaluation = readiness.evaluate(projectId);
        var flow = workflows.findById(projectId).orElse(null);
        boolean requested = flow != null && flow.getStatus() == CompletionWorkflow.Status.REQUESTED;
        return new Details(projectId, base.projectStatus(), base.completedAt(), base.criteria(), base.participants(),
                base.predecessorConfirmed(), base.successorConfirmed(), evaluation.evaluated(), evaluation, view(flow),
                evaluation.eligible() && base.completedAt() == null,
                requested && evaluation.eligible() && base.predecessorConfirmed() && base.successorConfirmed());
    }

    @Transactional
    public Approval request(Long userId, Long projectId) {
        access.role(userId, projectId, ParticipantRole.PREDECESSOR);
        var project = access.lock(projectId);
        var existing = workflows.findById(projectId);
        if (existing.isPresent() && existing.get().getStatus() == CompletionWorkflow.Status.APPROVED) return view(existing.get());
        access.mutable(project);
        var evaluation = readiness.evaluate(projectId);
        if (!evaluation.eligible()) throw new BusinessException(ErrorCode.CONFLICT);
        if (existing.isPresent()) return view(existing.get());
        var now = clock.instant();
        var flow = workflows.save(CompletionWorkflow.request(projectId, userId, now));
        audits.save(CompletionAudit.record(projectId, "REQUESTED", userId, evaluation, now));
        return view(flow);
    }

    @Transactional
    public Approval approve(Long userId, Long projectId) {
        access.admin(userId, projectId);
        var project = access.lock(projectId);
        var flow = workflows.findById(projectId).orElseThrow(() -> new BusinessException(ErrorCode.CONFLICT));
        if (flow.getStatus() == CompletionWorkflow.Status.APPROVED) return view(flow);
        access.mutable(project);
        // Re-read current criteria, documents, quiz results, Q&A, checklist and confirmations under the project lock.
        var evaluation = readiness.evaluate(projectId);
        var identity = confirmations.state(userId, projectId);
        if (!evaluation.eligible() || !identity.predecessorConfirmed() || !identity.successorConfirmed())
            throw new BusinessException(ErrorCode.CONFLICT);
        var now = clock.instant();
        project.approveCompletion(userId, evaluation.score(), now);
        flow.approve(userId, now);
        audits.save(CompletionAudit.record(projectId, "APPROVED", userId, evaluation, now));
        return view(flow);
    }

    private Approval view(CompletionWorkflow flow) {
        return flow == null ? new Approval("NOT_REQUESTED", null, null, null, null)
                : new Approval(flow.getStatus().name(), flow.getRequestedBy(), flow.getRequestedAt(), flow.getApprovedBy(), flow.getApprovedAt());
    }
}
