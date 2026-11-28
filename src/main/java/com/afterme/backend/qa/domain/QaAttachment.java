package com.afterme.backend.qa.domain;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.Instant;

@Entity
@Table(name = "qa_attachment")
@Getter
public class QaAttachment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private Long threadId;

    @Column(nullable = false, updatable = false)
    private String originalFilename;

    @Column(nullable = false, updatable = false)
    private String contentType;

    @Column(nullable = false, updatable = false)
    private long sizeBytes;

    @Column(nullable = false, updatable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected QaAttachment() {}

    public QaAttachment(Long threadId, String originalFilename, String contentType, byte[] content, Instant createdAt) {
        this.threadId = threadId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = content.length;
        this.content = content;
        this.createdAt = createdAt;
    }
}
