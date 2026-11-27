package com.afterme.backend.interview.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/** 질문당 1개. 내용은 AnswerRevision에 쌓고 여기서는 최신 버전만 가리킨다. */
@Entity
@Getter
public class InterviewAnswer {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true) private Long questionId;
    @Column(nullable = false) private Long authorId;
    private Long currentRevisionId;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected InterviewAnswer() {
    }

    public InterviewAnswer(Long questionId, Long authorId, Instant now) {
        this.questionId = questionId;
        this.authorId = authorId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void pointTo(AnswerRevision revision, Instant now) {
        this.currentRevisionId = revision.getId();
        this.updatedAt = now;
    }
}