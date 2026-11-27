package com.afterme.backend.interview.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/** 업무당 1개. 확정 후 답변이 바뀌면 IN_PROGRESS로 돌아가 다시 확정해야 한다. */
@Entity
@Getter
public class InterviewSession {
    public enum Status { IN_PROGRESS, CONFIRMED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false, unique = true) private Long taskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Status status;
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected InterviewSession() {
    }

    public InterviewSession(Long projectId, Long taskId, Long createdBy, Instant now) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.status = Status.IN_PROGRESS;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void confirm(Instant now) {
        this.status = Status.CONFIRMED;
        this.updatedAt = now;
    }

    /** 확정 후 답변이 수정되면 호출한다. 이전 확정 기록은 그대로 남는다. */
    public void reopen(Instant now) {
        this.status = Status.IN_PROGRESS;
        this.updatedAt = now;
    }
}