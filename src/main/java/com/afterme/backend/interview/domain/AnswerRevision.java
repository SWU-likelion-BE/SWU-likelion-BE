package com.afterme.backend.interview.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/** 원본 답변의 한 버전. 한번 저장되면 바뀌지 않는다. */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"answer_id", "revision_no"}))
@Getter
public class AnswerRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long answerId;
    @Column(nullable = false) private int revisionNo;
    @Column(nullable = false, columnDefinition = "text") private String rawText;
    @Column(nullable = false) private Long createdBy;
    @Column(nullable = false) private Instant createdAt;

    protected AnswerRevision() {
    }

    public AnswerRevision(Long answerId, int revisionNo, String rawText, Long createdBy, Instant now) {
        this.answerId = answerId;
        this.revisionNo = revisionNo;
        this.rawText = rawText;
        this.createdBy = createdBy;
        this.createdAt = now;
    }
}