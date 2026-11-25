package com.afterme.backend.quiz.domain;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.Instant;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QuizAttemptTest {
    private static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");

    static QuizSet quiz(int count, int cutoff) {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            QuizQuestion q = QuizQuestion.multipleChoice("Question " + i, List.of("A", "B"), 0,
                    "Evidence", 100L + i, "PROCEDURE");
            ReflectionTestUtils.setField(q, "id", (long) i + 1);
            questions.add(q);
        }
        return QuizSet.ready(1L, 10L, cutoff, questions);
    }

    @Test void seventyPercentPassesAndHasNoCooldown() {
        QuizAttempt attempt = QuizAttempt.start(quiz(10, 70), 2L, 1, NOW);
        Map<Long, Integer> answers = new HashMap<>();
        for (long i = 1; i <= 10; i++) answers.put(i, i <= 7 ? 0 : 1);
        attempt.submit(answers, NOW);
        assertThat(attempt.getCorrectCount()).isEqualTo(7);
        assertThat(attempt.getScore()).isEqualTo(70);
        assertThat(attempt.getPassed()).isTrue();
        assertThat(attempt.getNextAllowedAt()).isNull();
    }

    @Test void failedAttemptPreservesAnswersAndWaits72Hours() {
        QuizAttempt attempt = QuizAttempt.start(quiz(3, 70), 2L, 1, NOW);
        Map<Long, Integer> answers = Map.of(1L, 0, 2L, 0, 3L, 1);
        attempt.submit(answers, NOW);
        assertThat(attempt.getScore()).isEqualTo(66);
        assertThat(attempt.getPassed()).isFalse();
        assertThat(attempt.getNextAllowedAt()).isEqualTo(NOW.plus(Duration.ofDays(3)));
        assertThat(attempt.getAnswers()).isEqualTo(answers);
    }

    @Test void incompleteForeignAndOutOfRangeAnswersAreRejectedWithoutSubmitting() {
        QuizAttempt attempt = QuizAttempt.start(quiz(2, 70), 2L, 1, NOW);
        for (Map<Long, Integer> invalid : List.of(Map.of(1L, 0), Map.of(1L, 0, 99L, 0), Map.of(1L, 0, 2L, 2))) {
            assertThatThrownBy(() -> attempt.submit(invalid, NOW)).isInstanceOf(BusinessException.class)
                    .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.INVALID_INPUT);
            assertThat(attempt.getSubmittedAt()).isNull();
            assertThat(attempt.getAnswers()).isEmpty();
        }
    }

    @Test void secondSubmissionCannotChangeResult() {
        QuizAttempt attempt = QuizAttempt.start(quiz(1, 70), 2L, 1, NOW);
        attempt.submit(Map.of(1L, 1), NOW);
        assertThatThrownBy(() -> attempt.submit(Map.of(1L, 0), NOW.plusSeconds(1)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(ErrorCode.CONFLICT);
        assertThat(attempt.getScore()).isZero();
    }

    @Test void gradingUsesAttemptCutoffInsteadOfQuizGenerationCutoff() {
        QuizAttempt attempt = QuizAttempt.start(quiz(10, 70), 2L, 1, 80, NOW);
        Map<Long, Integer> answers = new HashMap<>();
        for (long i = 1; i <= 10; i++) answers.put(i, i <= 7 ? 0 : 1);
        attempt.submit(answers, NOW);
        assertThat(attempt.getScore()).isEqualTo(70);
        assertThat(attempt.getPassingScore()).isEqualTo(80);
        assertThat(attempt.getPassed()).isFalse();
    }
}
