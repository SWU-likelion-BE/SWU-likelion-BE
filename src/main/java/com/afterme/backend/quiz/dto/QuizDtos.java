package com.afterme.backend.quiz.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;

public final class QuizDtos {
    private QuizDtos() {}
    public record QuizSummary(Long quizSetId, Long versionId, int passingScore, int questionCount) {}
    public record QuizPage(List<QuizSummary> items, int page, int size, long totalElements, int totalPages) {}
    public record AttemptStarted(Long attemptId, int attemptNo, int passingScore, Instant startedAt) {}
    public record Question(Long questionId, String type, String stem, List<String> options, String topic) {}
    public record AttemptQuestions(Long attemptId, int attemptNo, int passingScore, List<Question> questions) {}
    public record Answer(@NotNull @Positive Long questionId, @NotNull @Min(0) Integer selectedOption) {}
    public record Submission(@NotEmpty @Size(max = 100) List<@NotNull @Valid Answer> answers) {}
    public record QuestionResult(Long questionId, String stem, List<String> options, int selectedOption,
                                 int correctOption, boolean correct, String explanation, Long sectionId, String topic) {}
    public record TopicResult(String topic, int correctCount, int totalCount) {}
    public record Result(Long attemptId, int correctCount, int totalCount, int score, int passingScore,
                         boolean passed, Instant submittedAt, Instant nextAllowedAt, Long versionId,
                         List<QuestionResult> questions, List<TopicResult> topics) {}
}
