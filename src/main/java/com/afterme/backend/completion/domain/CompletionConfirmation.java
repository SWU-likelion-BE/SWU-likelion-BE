package com.afterme.backend.completion.domain;

import com.afterme.backend.handover.domain.ParticipantRole;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "completion_confirmation", uniqueConstraints = @UniqueConstraint(
        columnNames = {"project_id", "member_id", "role", "criteria_revision"}))
@Getter
public class CompletionConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long memberId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private ParticipantRole role;
    @Column(nullable = false) private long criteriaRevision;
    @Column(nullable = false) private Instant confirmedAt;

    protected CompletionConfirmation() {}

    public static CompletionConfirmation confirm(Long projectId, Long memberId, ParticipantRole role,
                                                  long criteriaRevision, Instant now) {
        CompletionConfirmation confirmation = new CompletionConfirmation();
        confirmation.projectId = projectId;
        confirmation.memberId = memberId;
        confirmation.role = role;
        confirmation.criteriaRevision = criteriaRevision;
        confirmation.confirmedAt = now;
        return confirmation;
    }
}
