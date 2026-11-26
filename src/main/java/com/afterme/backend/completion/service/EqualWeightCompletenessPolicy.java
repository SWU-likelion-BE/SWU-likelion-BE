package com.afterme.backend.completion.service;

import com.afterme.backend.completion.port.CompletenessPolicy;
import com.afterme.backend.learning.port.LearningDocumentReader.PageCount;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.List;
import java.util.OptionalInt;

/** Four equally weighted categories. Multiple-successor project aggregation needs a team policy. */
public class EqualWeightCompletenessPolicy implements CompletenessPolicy {
    public record ParticipantScore(Long userId, Integer score, BigDecimal pageViewRate,
                                   BigDecimal checklistRate, BigDecimal quizScore, BigDecimal questionResolutionRate) {}

    @Override
    public OptionalInt score(ReadinessService.ScoringEvidence evidence) {
        if (!evidence.documents().available() || evidence.participantScores().size() != 1) return OptionalInt.empty();
        Integer score = evidence.participantScores().get(0).score();
        return score == null ? OptionalInt.empty() : OptionalInt.of(score);
    }

    public static ParticipantScore calculate(Long userId, PageCount pages, int checklistTotal, int checklistCompleted,
                                             List<ReadinessService.QuizOutcome> quizzes, long questions, long resolved) {
        if (checklistTotal < 0 || checklistCompleted < 0 || checklistCompleted > checklistTotal
                || questions < 0 || resolved < 0 || resolved > questions)
            throw new IllegalArgumentException("Invalid scoring counts");
        BigDecimal pageRate = pages == null ? null : percentage(pages.viewedPages(), pages.totalPages());
        // An empty checklist needs a separate team decision; do not award free credit or change weights.
        BigDecimal checklistRate = checklistTotal == 0 ? null : percentage(checklistCompleted, checklistTotal);
        BigDecimal quizRate = quizzes.isEmpty() ? null : quizzes.stream()
                .map(q -> BigDecimal.valueOf(q.bestScore() == null ? 0 : q.bestScore()))
                .reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(quizzes.size()), 10, RoundingMode.DOWN);
        BigDecimal questionRate = questions == 0 ? BigDecimal.ZERO : percentage(resolved, questions);
        Integer score = null;
        if (pageRate != null && checklistRate != null && quizRate != null) {
            long quizSum = quizzes.stream().mapToLong(q -> q.bestScore() == null ? 0 : q.bestScore()).sum();
            // Exact rational arithmetic avoids dropping an integer point at repeating-decimal boundaries.
            Fraction total = Fraction.of(pages.viewedPages(), pages.totalPages())
                    .add(Fraction.of(checklistCompleted, checklistTotal))
                    .add(Fraction.of(quizSum, 100L * quizzes.size()))
                    .add(questions == 0 ? Fraction.of(0, 1) : Fraction.of(resolved, questions));
            score = total.numerator.multiply(BigInteger.valueOf(25)).divide(total.denominator).intValueExact();
        }
        return new ParticipantScore(userId, score, pageRate, checklistRate, quizRate, questionRate);
    }

    private static BigDecimal percentage(long completed, long total) {
        return BigDecimal.valueOf(completed).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 10, RoundingMode.DOWN);
    }

    private record Fraction(BigInteger numerator, BigInteger denominator) {
        static Fraction of(long numerator, long denominator) {
            return new Fraction(BigInteger.valueOf(numerator), BigInteger.valueOf(denominator));
        }
        Fraction add(Fraction other) {
            return new Fraction(numerator.multiply(other.denominator).add(other.numerator.multiply(denominator)),
                    denominator.multiply(other.denominator));
        }
    }
}
