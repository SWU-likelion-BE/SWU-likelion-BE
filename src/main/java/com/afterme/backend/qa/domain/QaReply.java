package com.afterme.backend.qa.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "qa_reply")
@Getter
public class QaReply {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "thread_id", nullable = false) private QaThread thread;
    @Column(nullable = false) private Long authorId;
    @Column(nullable = false, columnDefinition = "text") private String content;
    @Column(nullable = false) private int questionRevision;
    @Column(nullable = false, columnDefinition = "text") private String questionContent;
    @Column(nullable = false) private Instant createdAt;

    protected QaReply() {}

    public static QaReply create(QaThread thread, Long authorId, String content, Instant now) {
        QaReply reply = new QaReply();
        reply.thread = thread;
        reply.authorId = authorId;
        reply.content = QaThread.validatedContent(content);
        reply.questionRevision = thread.getRevision();
        reply.questionContent = thread.getContent();
        reply.createdAt = now;
        return reply;
    }
}
