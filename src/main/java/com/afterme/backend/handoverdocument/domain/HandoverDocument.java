package com.afterme.backend.handoverdocument.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Getter
public class HandoverDocument {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private Long taskId;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected HandoverDocument() {
    }

    public HandoverDocument(Long taskId, Instant createdAt) {
        this.taskId = taskId;
        this.createdAt = createdAt;
    }
}
