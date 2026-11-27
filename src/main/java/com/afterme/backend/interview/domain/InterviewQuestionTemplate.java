package com.afterme.backend.interview.domain;

import jakarta.persistence.*;
import lombok.Getter;

/** 기본 질문 원본. 인터뷰 시작 시 InterviewQuestion으로 복사된다. */
@Entity
@Getter
public class InterviewQuestionTemplate {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 50) private String templateKey;
    @Column(nullable = false, length = 30) private String topic;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false, unique = true) private int sequence;
    @Column(nullable = false) private boolean required;
    @Column(nullable = false) private boolean active;

    protected InterviewQuestionTemplate() {
    }
}