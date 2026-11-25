package com.afterme.backend.completion.domain;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Instant;

@Entity
@Table(name = "completion_criteria")
@Getter
public class CompletionCriteria {
    public static final int DEFAULT_MINIMUM_COMPLETENESS = 80;
    public static final int DEFAULT_QUIZ_PASSING_SCORE = 70;

    @Id private Long projectId;
    @Column(nullable = false) private int minimumCompleteness = DEFAULT_MINIMUM_COMPLETENESS;
    @Column(nullable = false) private int quizPassingScore = DEFAULT_QUIZ_PASSING_SCORE;
    @Column(nullable = false) private long revision;
    private Long updatedBy;
    private Instant updatedAt;

    protected CompletionCriteria() {}

    public static CompletionCriteria defaults(Long projectId) {
        CompletionCriteria criteria = new CompletionCriteria();
        criteria.projectId = projectId;
        return criteria;
    }

    public void update(Integer minimumCompleteness, Integer quizPassingScore, Long actorId, Instant now) {
        if ((minimumCompleteness == null && quizPassingScore == null)
                || (minimumCompleteness != null && (minimumCompleteness < 80 || minimumCompleteness > 100))
                || (quizPassingScore != null && (quizPassingScore < 70 || quizPassingScore > 100))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        int minimum = minimumCompleteness == null ? this.minimumCompleteness : minimumCompleteness;
        int passingScore = quizPassingScore == null ? this.quizPassingScore : quizPassingScore;
        if (minimum == this.minimumCompleteness && passingScore == this.quizPassingScore) return;
        this.minimumCompleteness = minimum;
        this.quizPassingScore = passingScore;
        this.revision++;
        this.updatedBy = actorId;
        this.updatedAt = now;
    }
}
