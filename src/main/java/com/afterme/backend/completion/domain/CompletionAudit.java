package com.afterme.backend.completion.domain;

import com.afterme.backend.completion.service.ReadinessService.Evaluation;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "completion_audit", uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "action"}))
@Getter
public class CompletionAudit {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false, length = 20) private String action;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false) private int score;
    @Column(nullable = false) private int minimumCompleteness;
    @Column(nullable = false) private int quizPassingScore;
    @Column(nullable = false) private Instant occurredAt;
    protected CompletionAudit() {}
    public static CompletionAudit record(Long projectId, String action, Long actorId, Evaluation evaluation, Instant now) {
        var audit = new CompletionAudit();
        audit.projectId = projectId; audit.action = action; audit.actorId = actorId; audit.score = evaluation.score();
        audit.minimumCompleteness = evaluation.minimumCompleteness(); audit.quizPassingScore = evaluation.quizPassingScore();
        audit.occurredAt = now;
        return audit;
    }
}
