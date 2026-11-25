package com.afterme.backend.completion.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "completion_workflow")
@Getter
public class CompletionWorkflow {
    public enum Status { REQUESTED, APPROVED }
    @Id private Long projectId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Long requestedBy;
    @Column(nullable = false) private Instant requestedAt;
    private Long approvedBy;
    private Instant approvedAt;
    protected CompletionWorkflow() {}
    public static CompletionWorkflow request(Long projectId, Long actorId, Instant now) {
        var flow = new CompletionWorkflow();
        flow.projectId = projectId; flow.status = Status.REQUESTED; flow.requestedBy = actorId; flow.requestedAt = now;
        return flow;
    }
    public void approve(Long actorId, Instant now) { status = Status.APPROVED; approvedBy = actorId; approvedAt = now; }
}
