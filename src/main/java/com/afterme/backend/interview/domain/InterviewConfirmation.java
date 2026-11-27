package com.afterme.backend.interview.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/** 확정 기록. 확정 시점의 답변 버전 ID 목록을 고정한다(초안 생성 입력). */
@Entity
@Getter
public class InterviewConfirmation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long sessionId;
    @Column(nullable = false) private Long confirmedBy;
    @Column(nullable = false) private Instant confirmedAt;

    @ElementCollection
    @CollectionTable(name = "interview_confirmation_item", joinColumns = @JoinColumn(name = "confirmation_id"))
    @Column(name = "answer_revision_id", nullable = false)
    private Set<Long> revisionIds = new HashSet<>();

    protected InterviewConfirmation() {
    }

    public InterviewConfirmation(Long sessionId, Long confirmedBy, Set<Long> revisionIds, Instant now) {
        this.sessionId = sessionId;
        this.confirmedBy = confirmedBy;
        this.revisionIds = new HashSet<>(revisionIds);
        this.confirmedAt = now;
    }

    public Set<Long> getRevisionIds() {
        return Set.copyOf(revisionIds);
    }
}