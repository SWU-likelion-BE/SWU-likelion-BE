package com.afterme.backend.qa.domain;

import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

/** Persisted atomically with Q&A writes; notification module consumes by event id. */
@Entity
@Table(name = "qa_outbox_event")
@Getter
public class QaOutboxEvent {
    public enum Type { QUESTION_CREATED, ANSWER_CREATED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30) private Type type;
    @Column(nullable = false) private Long projectId;
    @Column(nullable = false) private Long threadId;
    private Long replyId;
    @Column(nullable = false) private Long actorId;
    @Column(nullable = false, unique = true) private String dedupeKey;
    @Column(nullable = false) private Instant createdAt;
    private Instant processedAt;

    protected QaOutboxEvent() {}

    public static QaOutboxEvent questionCreated(QaThread thread, Instant now) {
        return create(Type.QUESTION_CREATED, thread, null, thread.getAuthorId(), now);
    }

    public static QaOutboxEvent answerCreated(QaThread thread, QaReply reply, Instant now) {
        return create(Type.ANSWER_CREATED, thread, reply.getId(), reply.getAuthorId(), now);
    }

    private static QaOutboxEvent create(Type type, QaThread thread, Long replyId, Long actorId, Instant now) {
        QaOutboxEvent event = new QaOutboxEvent();
        event.type = type;
        event.projectId = thread.getProjectId();
        event.threadId = thread.getId();
        event.replyId = replyId;
        event.actorId = actorId;
        event.dedupeKey = type.name() + ":" + (replyId == null ? thread.getId() : replyId);
        event.createdAt = now;
        return event;
    }
}
