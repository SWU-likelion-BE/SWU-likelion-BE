package com.afterme.backend.interview.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

/** 세션별 질문. BASE는 템플릿 복사본, FOLLOW_UP은 AI 추가 질문(#20). */
@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"session_id", "sequence"}))
@Getter
public class InterviewQuestion {
    public enum Type { BASE, FOLLOW_UP }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private Long sessionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Type type;
    @Column(length = 50) private String templateKey;
    @Column(nullable = false, length = 30) private String topic;
    @Column(nullable = false, columnDefinition = "text") private String content;
    private Long parentQuestionId;
    private Long sourceAnswerRevisionId;
    @Column(nullable = false) private int sequence;
    @Column(nullable = false) private boolean required;
    @Column(nullable = false) private Instant createdAt;

    protected InterviewQuestion() {
    }

    public static InterviewQuestion base(Long sessionId, InterviewQuestionTemplate template, Instant now) {
        InterviewQuestion question = new InterviewQuestion();
        question.sessionId = sessionId;
        question.type = Type.BASE;
        question.templateKey = template.getTemplateKey();
        question.topic = template.getTopic();
        question.content = template.getContent();
        question.sequence = template.getSequence();
        question.required = template.isRequired();
        question.createdAt = now;
        return question;
    }
}