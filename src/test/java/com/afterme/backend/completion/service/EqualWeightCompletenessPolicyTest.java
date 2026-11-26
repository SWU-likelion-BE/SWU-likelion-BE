package com.afterme.backend.completion.service;

import com.afterme.backend.learning.port.LearningDocumentReader.PageCount;
import com.afterme.backend.learning.port.LearningDocumentReader.PageViews;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class EqualWeightCompletenessPolicyTest {
    @Test void eachCategoryContributesExactlyOneQuarter() {
        var score = calculate(new PageCount(10, 10), 10, 8, List.of(80), 2, 2);
        assertThat(score.score()).isEqualTo(90);
        assertThat(score.pageViewRate()).isEqualByComparingTo("100");
        assertThat(score.checklistRate()).isEqualByComparingTo("80");
        assertThat(score.quizScore()).isEqualByComparingTo("80");
        assertThat(score.questionResolutionRate()).isEqualByComparingTo("100");
    }
    @Test void missingQuizIsZeroAndVersionsHaveEqualWeight() {
        var score = calculate(new PageCount(1, 1), 1, 1, Arrays.asList(100, null), 1, 1);
        assertThat(score.quizScore()).isEqualByComparingTo("50");
        assertThat(score.score()).isEqualTo(87);
    }
    @Test void exactFractionsDoNotLoseIntegerPoints() {
        assertThat(calculate(new PageCount(3, 1), 3, 2, List.of(100), 1, 1).score()).isEqualTo(75);
    }
    @Test void noQuestionsDoesNotAwardResolutionPoints() {
        assertThat(calculate(new PageCount(1, 1), 1, 1, List.of(100), 0, 0).score()).isEqualTo(75);
    }
    @Test void unavailableViewsAndEmptyChecklistDoNotInventScores() {
        assertThat(calculate(null, 1, 1, List.of(100), 1, 1).score()).isNull();
        assertThat(calculate(new PageCount(1, 1), 0, 0, List.of(100), 1, 1).score()).isNull();
    }
    @Test void invalidCountsAndContradictoryPageEvidenceAreRejected() {
        assertThatThrownBy(() -> new PageCount(0, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageCount(2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PageViews(true, Set.of(1L), Map.of(1L, new PageCount(2, 1))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> calculate(new PageCount(1, 1), 1, 2, List.of(100), 1, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private EqualWeightCompletenessPolicy.ParticipantScore calculate(PageCount pages, int total, int completed,
                                                                     List<Integer> scores, int questions, int resolved) {
        List<ReadinessService.QuizOutcome> quizzes = new ArrayList<>();
        for (int i = 0; i < scores.size(); i++) quizzes.add(new ReadinessService.QuizOutcome(1L, (long)i, scores.get(i), false));
        return EqualWeightCompletenessPolicy.calculate(1L, pages, total, completed, quizzes, questions, resolved);
    }
}
