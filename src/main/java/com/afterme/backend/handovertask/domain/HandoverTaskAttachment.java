package com.afterme.backend.handovertask.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.OffsetDateTime;

@Entity
@Table(name = "handover_task_attachments")
@Getter
public class HandoverTaskAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "task_id", nullable = false)
    private Long taskId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    protected HandoverTaskAttachment() {
    }

    public HandoverTaskAttachment(Long taskId, String originalFilename, String contentType,
                                 byte[] content, OffsetDateTime createdAt) {
        this.taskId = taskId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = content.length;
        this.content = content;
        this.createdAt = createdAt;
    }
}
