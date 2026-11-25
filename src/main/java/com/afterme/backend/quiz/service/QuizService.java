package com.afterme.backend.quiz.service;

import com.afterme.backend.completion.domain.CompletionCriteria;
import com.afterme.backend.completion.repository.CompletionCriteriaRepository;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.repository.HandoverProjectRepository;
import com.afterme.backend.handover.repository.ProjectParticipantRepository;
import com.afterme.backend.quiz.domain.*;
import com.afterme.backend.quiz.dto.QuizDtos.*;
import com.afterme.backend.quiz.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizService {
    private final HandoverProjectRepository projects;
    private final ProjectParticipantRepository participants;
    private final QuizSetRepository quizzes;
    private final QuizAttemptRepository attempts;
    private final CompletionCriteriaRepository criteriaRepository;
    private final Clock clock;

    public QuizPage list(Long userId, Long projectId, int page, int size) {
        requireAccess(userId, projectId);
        if (page < 0 || size < 1 || size > 100) fail(ErrorCode.INVALID_INPUT);
        var result = quizzes.findByProjectId(projectId, PageRequest.of(page, size, Sort.by("id").descending()));
        int passingScore = passingScore(projectId);
        return new QuizPage(result.map(q -> new QuizSummary(q.getId(), q.getVersionId(),
                passingScore, q.getQuestions().size())).getContent(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public AttemptStarted start(Long userId, Long projectId, Long quizSetId) {
        requireSuccessor(userId, projectId);
        // Serialize starts/submissions for this quiz to prevent duplicate attempts and cooldown races.
        QuizSet quiz = lockedQuiz(projectId, quizSetId);
        QuizAttempt latest = attempts.findFirstByQuizSet_IdAndUserIdOrderByAttemptNoDesc(quizSetId, userId).orElse(null);
        if (latest != null && latest.getSubmittedAt() == null) return started(latest);
        if (latest != null && latest.getNextAllowedAt() != null
                && clock.instant().isBefore(latest.getNextAllowedAt())) fail(ErrorCode.CONFLICT);
        QuizAttempt attempt = attempts.save(QuizAttempt.start(quiz, userId,
                latest == null ? 1 : latest.getAttemptNo() + 1, passingScore(projectId), clock.instant()));
        return started(attempt);
    }

    public AttemptQuestions questions(Long userId, Long projectId, Long quizSetId, Long attemptId) {
        requireSuccessor(userId, projectId);
        QuizAttempt attempt = ownedAttempt(userId, projectId, quizSetId, attemptId);
        return new AttemptQuestions(attempt.getId(), attempt.getAttemptNo(), attempt.getPassingScore(), attempt.getQuizSet().getQuestions().stream()
                .map(q -> new Question(q.getId(), "MULTIPLE_CHOICE", q.getStem(), q.getOptions(), q.getTopic())).toList());
    }

    @Transactional
    public Result submit(Long userId, Long projectId, Long quizSetId, Long attemptId, Submission request) {
        requireSuccessor(userId, projectId);
        projects.findByIdForUpdate(projectId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        lockedQuiz(projectId, quizSetId);
        QuizAttempt attempt = ownedAttempt(userId, projectId, quizSetId, attemptId);
        Map<Long, Integer> answers = new HashMap<>();
        for (Answer answer : request.answers()) {
            if (answers.putIfAbsent(answer.questionId(), answer.selectedOption()) != null) fail(ErrorCode.INVALID_INPUT);
        }
        attempt.submit(answers, clock.instant());
        return toResult(attempt);
    }

    public Result result(Long userId, Long projectId, Long quizSetId, Long attemptId) {
        requireSuccessor(userId, projectId);
        QuizAttempt attempt = ownedAttempt(userId, projectId, quizSetId, attemptId);
        if (attempt.getSubmittedAt() == null) fail(ErrorCode.CONFLICT);
        return toResult(attempt);
    }

    private Result toResult(QuizAttempt attempt) {
        Map<Long, Integer> answers = attempt.getAnswers();
        List<QuestionResult> questions = attempt.getQuizSet().getQuestions().stream().map(q -> {
            int selected = answers.get(q.getId());
            return new QuestionResult(q.getId(), q.getStem(), q.getOptions(), selected, q.getCorrectOption(),
                    selected == q.getCorrectOption(), q.getExplanation(), q.getSectionId(), q.getTopic());
        }).toList();
        List<TopicResult> topics = questions.stream().collect(Collectors.groupingBy(
                QuestionResult::topic, TreeMap::new, Collectors.toList())).entrySet().stream()
                .map(e -> new TopicResult(e.getKey(), (int) e.getValue().stream().filter(QuestionResult::correct).count(),
                        e.getValue().size())).toList();
        return new Result(attempt.getId(), attempt.getCorrectCount(), attempt.getTotalCount(), attempt.getScore(),
                attempt.getPassingScore(), attempt.getPassed(), attempt.getSubmittedAt(),
                attempt.getNextAllowedAt(), attempt.getQuizSet().getVersionId(), questions, topics);
    }

    private QuizAttempt ownedAttempt(Long userId, Long projectId, Long quizSetId, Long attemptId) {
        quizzes.findByIdAndProjectId(quizSetId, projectId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return attempts.findByIdAndQuizSet_IdAndUserId(attemptId, quizSetId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private QuizSet lockedQuiz(Long projectId, Long quizSetId) {
        return quizzes.findForUpdate(quizSetId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireAccess(Long userId, Long projectId) {
        projects.findAccessibleById(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireSuccessor(Long userId, Long projectId) {
        requireAccess(userId, projectId);
        boolean successor = participants.findParticipantsByProjectId(projectId).stream().anyMatch(p ->
                p.getRole() == ParticipantRole.SUCCESSOR && p.getMember().isActive()
                        && p.getMember().getUser().getId().equals(userId));
        if (!successor) fail(ErrorCode.FORBIDDEN);
    }

    private int passingScore(Long projectId) {
        return criteriaRepository.findById(projectId).map(CompletionCriteria::getQuizPassingScore)
                .orElse(CompletionCriteria.DEFAULT_QUIZ_PASSING_SCORE);
    }

    private AttemptStarted started(QuizAttempt a) { return new AttemptStarted(a.getId(), a.getAttemptNo(), a.getPassingScore(), a.getStartedAt()); }
    private static void fail(ErrorCode code) { throw new BusinessException(code); }
}
