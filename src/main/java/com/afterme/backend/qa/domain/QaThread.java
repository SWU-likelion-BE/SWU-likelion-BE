package com.afterme.backend.qa.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "qa_thread")
@Getter
public class QaThread {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long versionId;
    @Column(nullable = false) private Long sectionId;
    @Column(nullable = false) private Long authorId;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20) private QaStatus status;
    @Column(nullable = false) private int revision;
    @Column(nullable = false) private int replyCount;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;

    protected QaThread() {}

    public static QaThread create(Long projectId, Long versionId, Long sectionId, Long authorId,
                                  String content, Instant now) {
        QaThread thread = new QaThread();
        thread.projectId = projectId;
        thread.versionId = versionId;
        thread.sectionId = sectionId;
        thread.authorId = authorId;
        thread.content = validatedContent(content);
        thread.status = QaStatus.OPEN;
        thread.revision = 1;
        thread.createdAt = now;
        thread.updatedAt = now;
        return thread;
    }

    public void edit(String content, Instant now) {
        String validated = validatedContent(content);
        if (this.content.equals(validated)) return;
        this.content = validated;
        this.revision++;
        this.updatedAt = now;
    }

    public void answered(Instant now) {
        this.status = QaStatus.RESOLVED;
        this.replyCount++;
        this.updatedAt = now;
    }

    static String validatedContent(String content) {
        if (content == null || content.isBlank() || content.length() > 10000) {
            throw new com.afterme.backend.global.exception.BusinessException(
                    com.afterme.backend.global.exception.ErrorCode.INVALID_INPUT);
        }
        return content.strip();
    }
}
