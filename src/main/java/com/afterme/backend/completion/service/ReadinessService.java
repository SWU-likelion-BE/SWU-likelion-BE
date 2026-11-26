package com.afterme.backend.completion.service;

import com.afterme.backend.checklist.repository.ChecklistCompletionRepository;
import com.afterme.backend.completion.domain.CompletionCriteria;
import com.afterme.backend.completion.service.EqualWeightCompletenessPolicy.ParticipantScore;
import com.afterme.backend.completion.port.CompletenessPolicy;
import com.afterme.backend.completion.repository.CompletionCriteriaRepository;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.repository.ProjectParticipantRepository;
import com.afterme.backend.learning.port.LearningDocumentReader;
import com.afterme.backend.learning.service.LearningAccess;
import com.afterme.backend.qa.domain.QaStatus;
import com.afterme.backend.qa.repository.QaThreadRepository;
import com.afterme.backend.quiz.repository.QuizAttemptRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReadinessService {
    private final LearningAccess access;
    private final LearningDocumentReader documents;
    private final CompletenessPolicy policy;
    private final ProjectParticipantRepository participants;
    private final CompletionCriteriaRepository criteria;
    private final QaThreadRepository questions;
    private final QuizAttemptRepository attempts;
    private final ChecklistCompletionRepository checklists;

    public record Requirement(String code, Long userId, Long targetId) {}
    public record QuizOutcome(Long userId, Long versionId, Integer bestScore, boolean passed) {}
    public record ScoringEvidence(LearningDocumentReader.Evidence documents, long unresolvedQuestions,
                                  List<QuizOutcome> quizzes, int requiredChecklistCount, int completedChecklistCount,
                                  List<ParticipantScore> participantScores) {}
    public record Evaluation(Long projectId, Integer score, int minimumCompleteness, int quizPassingScore,
                             boolean evaluated, boolean eligible, List<Requirement> unmet, ScoringEvidence evidence) {}

    public Evaluation get(Long userId, Long projectId) {
        access.project(userId, projectId);
        return evaluate(projectId);
    }

    public Evaluation evaluate(Long projectId) {
        var rules = criteria.findById(projectId).orElseGet(() -> CompletionCriteria.defaults(projectId));
        Set<Long> successors = participants.findParticipantsByProjectId(projectId).stream()
                .filter(p -> p.getRole() == ParticipantRole.SUCCESSOR && p.getMember().isActive())
                .map(p -> p.getMember().getUser().getId()).collect(Collectors.toCollection(TreeSet::new));
        var documentEvidence = documents.evidence(projectId, successors);
        var doc = documentEvidence == null ? LearningDocumentReader.Evidence.unavailable() : documentEvidence;
        List<Requirement> unmet = new ArrayList<>();
        if (successors.isEmpty()) unmet.add(new Requirement("SUCCESSOR_REQUIRED", null, null));
        long unresolved = questions.countByProjectIdAndStatus(projectId, QaStatus.OPEN);
        if (unresolved > 0) unmet.add(new Requirement("UNANSWERED_QUESTIONS", null, null));
        for (Long userId : successors) {
            if (questions.countByProjectIdAndAuthorId(projectId, userId) == 0)
                unmet.add(new Requirement("QUESTION_REQUIRED", userId, null));
        }
        List<QuizOutcome> quizOutcomes = new ArrayList<>();
        List<ParticipantScore> participantScores = new ArrayList<>();
        int requiredChecks = 0;
        int completedChecks = 0;
        if (!doc.available()) unmet.add(new Requirement("DOCUMENT_DATA_UNAVAILABLE", null, null));
        else {
            if (doc.taskCount() == 0 || doc.missingRequiredTasks() > 0) unmet.add(new Requirement("REQUIRED_TASKS_MISSING", null, null));
            if (doc.answeredRequiredQuestions() < doc.requiredAnswers()) unmet.add(new Requirement("REQUIRED_ANSWERS_MISSING", null, null));
            if (doc.requiredVersionIds().isEmpty()) unmet.add(new Requirement("PUBLISHED_DOCUMENT_REQUIRED", null, null));
            var views = documents.pageViews(projectId, doc.requiredVersionIds(), successors);
            if (views == null || !views.available()) {
                unmet.add(new Requirement("PAGE_VIEW_DATA_UNAVAILABLE", null, null));
            } else {
                for (Long userId : successors) {
                    if (!views.fullyViewedSuccessorIds().contains(userId))
                        unmet.add(new Requirement("ALL_PAGES_NOT_VIEWED", userId, null));
                }
            }
            var submitted = successors.isEmpty() ? List.<com.afterme.backend.quiz.domain.QuizAttempt>of()
                    : attempts.findByQuizSet_ProjectIdAndUserIdInAndSubmittedAtIsNotNull(projectId, successors);
            var completed = checklists.findByProjectIdAndCompletedTrue(projectId);
            var scoringItems = documents.scoringChecklistItems(projectId, doc.requiredVersionIds());
            List<LearningDocumentReader.ChecklistItem> allItems = scoringItems == null ? List.of() : scoringItems.orElse(List.of());
            Set<Long> itemIds = new HashSet<>();
            boolean scoringItemsValid = allItems.stream().allMatch(item -> item != null
                    && item.itemId() != null && item.documentId() != null && Objects.equals(item.projectId(), projectId)
                    && doc.requiredVersionIds().contains(item.versionId()) && itemIds.add(item.itemId()));
            if (!scoringItemsValid) unmet.add(new Requirement("DOCUMENT_EVIDENCE_INVALID", null, null));
            for (Long userId : successors) {
                if (!doc.acknowledgedSuccessorIds().contains(userId)) unmet.add(new Requirement("DOCUMENT_ACKNOWLEDGEMENT_REQUIRED", userId, null));
                for (Long versionId : new TreeSet<>(doc.requiredVersionIds())) {
                    Integer best = submitted.stream().filter(a -> a.getUserId().equals(userId)
                            && a.getQuizSet().getVersionId().equals(versionId)).map(a -> a.getScore()).filter(Objects::nonNull)
                            .max(Integer::compareTo).orElse(null);
                    boolean passed = best != null && best >= rules.getQuizPassingScore();
                    quizOutcomes.add(new QuizOutcome(userId, versionId, best, passed));
                    if (!passed) unmet.add(new Requirement("QUIZ_NOT_PASSED", userId, versionId));
                }
                for (var item : doc.requiredChecklistItems()) {
                    requiredChecks++;
                    boolean valid = Objects.equals(item.projectId(), projectId) && doc.requiredVersionIds().contains(item.versionId());
                    boolean done = valid && completed.stream().anyMatch(c -> c.getUserId().equals(userId)
                            && Objects.equals(c.getItemId(), item.itemId()) && Objects.equals(c.getVersionId(), item.versionId())
                            && Objects.equals(c.getDocumentId(), item.documentId()));
                    if (done) completedChecks++;
                    else unmet.add(new Requirement(valid ? "REQUIRED_CHECKLIST_INCOMPLETE" : "DOCUMENT_EVIDENCE_INVALID", userId, item.itemId()));
                }
                var pageCount = views != null && views.available() ? views.progress().get(userId) : null;
                long questionCount = questions.countByProjectIdAndAuthorId(projectId, userId);
                long resolvedCount = questions.countByProjectIdAndAuthorIdAndStatus(projectId, userId, QaStatus.RESOLVED);
                int scoringTotal = scoringItemsValid ? allItems.size() : 0;
                int scoringCompleted = scoringItemsValid ? (int) allItems.stream().filter(item -> completed.stream().anyMatch(c ->
                        c.getUserId().equals(userId) && Objects.equals(c.getItemId(), item.itemId())
                                && Objects.equals(c.getVersionId(), item.versionId()) && Objects.equals(c.getDocumentId(), item.documentId()))).count() : 0;
                participantScores.add(EqualWeightCompletenessPolicy.calculate(userId, pageCount,
                        scoringTotal, scoringCompleted,
                        quizOutcomes.stream().filter(q -> q.userId().equals(userId)).toList(), questionCount, resolvedCount));
            }
        }
        ScoringEvidence evidence = new ScoringEvidence(doc, unresolved, List.copyOf(quizOutcomes), requiredChecks, completedChecks,
                List.copyOf(participantScores));
        OptionalInt calculated = doc.available() ? policy.score(evidence) : OptionalInt.empty();
        Integer score = calculated.isPresent() && calculated.getAsInt() >= 0 && calculated.getAsInt() <= 100 ? calculated.getAsInt() : null;
        if (score == null) unmet.add(new Requirement("COMPLETENESS_SCORE_UNAVAILABLE", null, null));
        else if (score < rules.getMinimumCompleteness()) unmet.add(new Requirement("MINIMUM_COMPLETENESS_NOT_MET", null, null));
        boolean evaluated = doc.available() && score != null;
        return new Evaluation(projectId, score, rules.getMinimumCompleteness(), rules.getQuizPassingScore(), evaluated,
                evaluated && unmet.isEmpty(), List.copyOf(unmet), evidence);
    }
}
