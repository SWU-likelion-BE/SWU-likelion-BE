package com.afterme.backend.qa.dto;

import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.qa.domain.QaStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class QaDtos {
    private QaDtos() {}
    public record CreateQuestion(@NotNull @Positive Long sectionId,
                                 @NotBlank @Size(max = 10000) String content,
                                 @Size(max = 255) String title) {
        public CreateQuestion(Long sectionId, String content) {
            this(sectionId, content, null);
        }
    }
    public record EditQuestion(@NotNull @Min(1) Integer revision,
                               @NotBlank @Size(max = 10000) String content,
                               @Size(max = 255) String title) {
        public EditQuestion(Integer revision, String content) {
            this(revision, content, null);
        }
    }
    public record CreateReply(@NotNull @Min(1) Integer questionRevision,
                              @NotBlank @Size(max = 10000) String content) {}
    public record Question(Long threadId, Long projectId, Long versionId, Long sectionId, Long authorId,
                           String content, QaStatus status, int revision, int replyCount,
                           Instant createdAt, Instant updatedAt, String sectionTitle, Integer sectionDisplayOrder,
                           String title, String authorName, ParticipantRole authorRole) {
        public Question(Long threadId, Long projectId, Long versionId, Long sectionId, Long authorId,
                        String content, QaStatus status, int revision, int replyCount,
                        Instant createdAt, Instant updatedAt) {
            this(threadId, projectId, versionId, sectionId, authorId, content, status, revision, replyCount,
                    createdAt, updatedAt, null, null, null, null, ParticipantRole.SUCCESSOR);
        }
    }
    public record Reply(Long replyId, Long authorId, String content, int questionRevision,
                        String questionContent, Instant createdAt, String authorName, ParticipantRole authorRole) {
        public Reply(Long replyId, Long authorId, String content, int questionRevision,
                     String questionContent, Instant createdAt) {
            this(replyId, authorId, content, questionRevision, questionContent, createdAt, null,
                    ParticipantRole.PREDECESSOR);
        }
    }
    public record Detail(Question question, List<Reply> replies, List<Attachment> attachments, String sectionContent) {
        public Detail(Question question, List<Reply> replies) {
            this(question, replies, List.of(), null);
        }
    }
    public record SectionOption(Long sectionId, Long versionId, Long taskId, String title, Integer displayOrder) {}
    public record Attachment(Long attachmentId, String fileName, String contentType, long sizeBytes, String downloadUrl) {}
    public record QuestionPage(List<Question> items, int page, int size, long totalElements, int totalPages) {}
}
