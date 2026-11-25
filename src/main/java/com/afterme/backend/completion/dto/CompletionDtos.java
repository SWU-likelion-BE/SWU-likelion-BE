package com.afterme.backend.completion.dto;

import com.afterme.backend.handover.domain.ParticipantRole;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

public final class CompletionDtos {
    private CompletionDtos() {}
    public record UpdateCriteria(@Min(80) @Max(100) Integer minimumCompleteness,
                                 @Min(70) @Max(100) Integer quizPassingScore,
                                 @NotNull @Min(0) Long revision) {}
    public record Criteria(int minimumCompleteness, int quizPassingScore, long revision,
                           Long updatedBy, Instant updatedAt) {}
    public record Confirm(@NotNull ParticipantRole role) {}
    public record Confirmation(Long confirmationId, Long memberId, ParticipantRole role,
                               long criteriaRevision, Instant confirmedAt) {}
    public record ParticipantConfirmation(Long memberId, Long userId, String name, ParticipantRole role,
                                           boolean confirmed, Instant confirmedAt) {}
    public record State(Long projectId, String projectStatus, OffsetDateTime completedAt, Criteria criteria,
                        List<ParticipantConfirmation> participants, boolean predecessorConfirmed,
                        boolean successorConfirmed, boolean eligibilityEvaluated) {}
}
