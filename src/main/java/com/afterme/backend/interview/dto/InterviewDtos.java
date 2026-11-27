package com.afterme.backend.interview.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class InterviewDtos {
    private InterviewDtos() {
    }

    /** @param expectedRevisionNo 화면이 알고 있는 최신 버전 번호(첫 답변은 0). 생략하면 검사하지 않는다. */
    public record SaveAnswer(@NotBlank @Size(max = 10000) String content, @PositiveOrZero Integer expectedRevisionNo) {
    }

    public record SessionDetail(Long sessionId, Long taskId, String status, List<QuestionView> questions) {
    }

    public record QuestionView(Long questionId, String type, String topic, String content, Long parentQuestionId,
                               int sequence, boolean required, AnswerView answer) {
    }

    public record AnswerView(Long answerId, int revisionNo, String content, Instant updatedAt) {
    }

    public record SavedAnswer(Long answerId, Long questionId, int revisionNo, String content,
                              String sessionStatus, Instant updatedAt) {
    }

    public record RevisionView(Long revisionId, int revisionNo, String content, Long createdBy, Instant createdAt) {
    }

    public record Confirmation(Long confirmationId, Long sessionId, int revisionCount, Instant confirmedAt) {
    }
}