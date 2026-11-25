package com.afterme.backend.qa.dto;

import com.afterme.backend.qa.domain.QaStatus;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class QaDtos {
    private QaDtos() {}
    public record CreateQuestion(@NotNull @Positive Long sectionId,
                                 @NotBlank @Size(max = 10000) String content) {}
    public record EditQuestion(@NotNull @Min(1) Integer revision,
                               @NotBlank @Size(max = 10000) String content) {}
    public record CreateReply(@NotNull @Min(1) Integer questionRevision,
                              @NotBlank @Size(max = 10000) String content) {}
    public record Question(Long threadId, Long projectId, Long versionId, Long sectionId, Long authorId,
                           String content, QaStatus status, int revision, int replyCount,
                           Instant createdAt, Instant updatedAt) {}
    public record Reply(Long replyId, Long authorId, String content, int questionRevision,
                        String questionContent, Instant createdAt) {}
    public record Detail(Question question, List<Reply> replies) {}
    public record QuestionPage(List<Question> items, int page, int size, long totalElements, int totalPages) {}
}
