package com.afterme.backend.quiz.domain;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.Getter;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "quiz_attempt", uniqueConstraints = @UniqueConstraint(columnNames = {"quiz_set_id", "user_id", "attempt_no"}))
@Getter
public class QuizAttempt {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "quiz_set_id") private QuizSet quizSet;
    @Column(nullable = false) private Long userId;
    @Column(nullable = false) private int attemptNo;
    @Column(nullable = false) private int passingScore;
    @Column(nullable = false) private Instant startedAt;
    private Instant submittedAt;
    private Instant nextAllowedAt;
    private Integer correctCount;
    private Integer totalCount;
    private Integer score;
    private Boolean passed;
    @ElementCollection
    @CollectionTable(name = "quiz_response", joinColumns = @JoinColumn(name = "attempt_id"))
    @MapKeyColumn(name = "question_id")
    @Column(name = "selected_option", nullable = false)
    private Map<Long, Integer> answers = new HashMap<>();

    protected QuizAttempt() {}

    public static QuizAttempt start(QuizSet set, Long userId, int attemptNo, Instant now) {
        return start(set, userId, attemptNo, set.getPassingScore(), now);
    }

    public static QuizAttempt start(QuizSet set, Long userId, int attemptNo, int passingScore, Instant now) {
        if (passingScore < 70 || passingScore > 100) throw new BusinessException(ErrorCode.INVALID_INPUT);
        QuizAttempt attempt = new QuizAttempt();
        attempt.quizSet = set;
        attempt.userId = userId;
        attempt.attemptNo = attemptNo;
        attempt.passingScore = passingScore;
        attempt.startedAt = now;
        return attempt;
    }

    public void submit(Map<Long, Integer> submitted, Instant now) {
        if (submittedAt != null) throw new BusinessException(ErrorCode.CONFLICT);
        List<QuizQuestion> questions = quizSet.getQuestions();
        if (submitted == null || submitted.size() != questions.size()) invalid();
        int correct = 0;
        for (QuizQuestion question : questions) {
            Integer choice = submitted.get(question.getId());
            if (choice == null || choice < 0 || choice >= question.getOptions().size()) invalid();
            if (choice == question.getCorrectOption()) correct++;
        }
        this.answers = new HashMap<>(submitted);
        this.correctCount = correct;
        this.totalCount = questions.size();
        this.score = correct * 100 / totalCount;
        // Compare the exact ratio, without rounded display scores.
        this.passed = (long) correct * 100 >= (long) passingScore * totalCount;
        this.submittedAt = now;
        this.nextAllowedAt = passed ? null : now.plus(Duration.ofDays(3));
    }

    private static void invalid() { throw new BusinessException(ErrorCode.INVALID_INPUT); }
    public Map<Long, Integer> getAnswers() { return Map.copyOf(answers); }
}
