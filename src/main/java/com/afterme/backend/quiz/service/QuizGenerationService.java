package com.afterme.backend.quiz.service;

import com.afterme.backend.completion.domain.CompletionCriteria;
import com.afterme.backend.completion.repository.CompletionCriteriaRepository;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.learning.port.LearningDocumentReader;
import com.afterme.backend.learning.port.LearningDocumentReader.Section;
import com.afterme.backend.learning.service.LearningAccess;
import com.afterme.backend.quiz.domain.*;
import com.afterme.backend.quiz.repository.*;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QuizGenerationService {
    private final LearningAccess access;
    private final LearningDocumentReader documents;
    private final QuizGenerationJobRepository jobs;
    private final QuizSetRepository quizzes;
    private final CompletionCriteriaRepository criteria;
    private final Clock clock;
    public record Request(@NotNull @Positive Long versionId, @NotBlank @Size(max = 100) String idempotencyKey) {}
    public record Job(String jobId, String type, Long versionId, String status, Long quizSetId, String errorCode, Instant createdAt) {}
    public record Input(Long internalJobId, Long projectId, Long versionId, List<Section> sections) {}
    public record GeneratedQuestion(String stem, List<String> options, int correctOption, String explanation, Long sectionId) {}
    public record Output(List<GeneratedQuestion> questions, String provider, String model, String promptVersion) {}

    @Transactional
    public Job request(Long userId, Long projectId, Request request) {
        access.participant(userId, projectId);
        access.lock(projectId);
        if (request.idempotencyKey() == null || request.idempotencyKey().isBlank() || request.idempotencyKey().length() > 100)
            throw error(ErrorCode.INVALID_INPUT);
        String key = request.idempotencyKey().strip();
        var existing = jobs.findByProjectIdAndRequestKey(projectId, key);
        if (existing.isPresent()) {
            if (!Objects.equals(existing.get().getVersionId(), request.versionId())) throw error(ErrorCode.CONFLICT);
            return view(existing.get());
        }
        var version = documents.requirePublishedVersion(projectId, request.versionId());
        validateVersion(projectId, request.versionId(), version);
        int passingScore = criteria.findById(projectId).map(CompletionCriteria::getQuizPassingScore).orElse(70);
        return view(jobs.save(QuizGenerationJob.queue(projectId, version.documentId(), version.versionId(), userId,
                key, passingScore, version.sections(), clock.instant())));
    }

    /** Called by the shared AI job adapter, not an unauthenticated HTTP endpoint. */
    public Job status(Long userId, Long projectId, String jobId) {
        access.project(userId, projectId);
        return view(jobs.findByIdAndProjectId(parseJobId(jobId), projectId)
                .orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND)));
    }

    /** Atomically claimed by an AI worker; the external AI call happens after this transaction returns. */
    @Transactional
    public Optional<Input> claim(Long id) {
        var job = jobs.findForUpdate(id).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getStatus() != QuizGenerationJob.Status.QUEUED) return Optional.empty();
        job.start(clock.instant());
        return Optional.of(new Input(job.getId(), job.getProjectId(), job.getVersionId(), job.getSources().stream()
                .map(s -> new Section(s.getSectionId(), s.getType(), s.getContent())).toList()));
    }

    @Transactional
    public Long complete(Long id, Output output) {
        var job = jobs.findForUpdate(id).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getStatus() == QuizGenerationJob.Status.SUCCEEDED) return job.getQuizSetId();
        if (job.getStatus() != QuizGenerationJob.Status.RUNNING) throw error(ErrorCode.CONFLICT);
        access.lock(job.getProjectId());
        var version = documents.requirePublishedVersion(job.getProjectId(), job.getVersionId());
        validateVersion(job.getProjectId(), job.getVersionId(), version);
        if (!Objects.equals(version.documentId(), job.getDocumentId())) throw error(ErrorCode.RESOURCE_NOT_FOUND);
        if (output == null || output.questions() == null || output.questions().isEmpty() || output.questions().size() > 100
                || invalidMetadata(output.provider()) || invalidMetadata(output.model()) || invalidMetadata(output.promptVersion()))
            throw error(ErrorCode.INVALID_INPUT);
        Map<Long, String> originalSources = job.getSources().stream().collect(Collectors.toMap(QuizGenerationJob.Source::getSectionId, QuizGenerationJob.Source::getType));
        Set<Long> currentSources = version.sections().stream().map(Section::sectionId).collect(Collectors.toSet());
        List<QuizQuestion> questions = new ArrayList<>();
        try {
            for (var q : output.questions()) {
                if (q == null || !originalSources.containsKey(q.sectionId()) || !currentSources.contains(q.sectionId())) throw error(ErrorCode.INVALID_INPUT);
                questions.add(QuizQuestion.multipleChoice(q.stem(), q.options(), q.correctOption(), q.explanation(), q.sectionId(), originalSources.get(q.sectionId())));
            }
        } catch (IllegalArgumentException e) { throw error(ErrorCode.INVALID_INPUT); }
        var quiz = quizzes.save(QuizSet.ready(job.getProjectId(), job.getVersionId(), job.getPassingScore(), questions));
        job.succeed(quiz.getId(), output.provider(), output.model(), output.promptVersion(), clock.instant());
        return quiz.getId();
    }

    @Transactional
    public void fail(Long id, String errorCode) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,100}")) throw error(ErrorCode.INVALID_INPUT);
        var job = jobs.findForUpdate(id).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getStatus() == QuizGenerationJob.Status.SUCCEEDED) throw error(ErrorCode.CONFLICT);
        job.fail(errorCode, clock.instant());
    }

    /** 사용자 재시도: FAILED 작업만 다시 대기열로 보낸다. */
    @Transactional
    public Job retry(Long userId, Long projectId, String jobId) {
        access.participant(userId, projectId);
        access.lock(projectId);
        var job = jobs.findForUpdate(parseJobId(jobId))
                .filter(j -> Objects.equals(j.getProjectId(), projectId))
                .orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getStatus() != QuizGenerationJob.Status.FAILED) throw error(ErrorCode.CONFLICT);
        job.retry();
        return view(job);
    }

    /** 워커 자동 재시도: 시도 횟수가 남았으면 대기열로, 다 썼으면 실패로 기록한다. */
    @Transactional
    public void retryOrFail(Long id, String errorCode, int maxAttempts) {
        if (errorCode == null || !errorCode.matches("[A-Z0-9_]{1,100}")) throw error(ErrorCode.INVALID_INPUT);
        var job = jobs.findForUpdate(id).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getStatus() != QuizGenerationJob.Status.RUNNING) return;
        if (job.getAttemptCount() < maxAttempts) job.requeue();
        else job.fail(errorCode, clock.instant());
    }

    /** 워커 복구: staleBefore 이전에 시작해 아직 RUNNING인 작업을 되살린다. */
    @Transactional
    public void recoverStale(Long id, Instant staleBefore, int maxAttempts) {
        var job = jobs.findForUpdate(id).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        if (job.getStatus() != QuizGenerationJob.Status.RUNNING
                || job.getStartedAt() == null || !job.getStartedAt().isBefore(staleBefore)) return;
        if (job.getAttemptCount() < maxAttempts) job.requeue();
        else job.fail("AI_JOB_STALE", clock.instant());
    }

    public List<Long> queuedIds(int limit) {
        return jobs.findIdsByStatus(QuizGenerationJob.Status.QUEUED, PageRequest.of(0, limit));
    }

    public List<Long> staleIds(Instant staleBefore, int limit) {
        return jobs.findIdsByStatusAndStartedAtBefore(QuizGenerationJob.Status.RUNNING, staleBefore, PageRequest.of(0, limit));
    }

    private Long parseJobId(String jobId) {
        try {
            if (jobId == null || !jobId.startsWith("quiz-")) throw new NumberFormatException();
            return Long.valueOf(jobId.substring(5));
        } catch (NumberFormatException e) { throw error(ErrorCode.RESOURCE_NOT_FOUND); }
    }

    private boolean invalidMetadata(String value) { return value == null || value.isBlank() || value.length() > 255; }
    private void validateVersion(Long projectId, Long versionId, LearningDocumentReader.PublishedVersion version) {
        if (version == null || !Objects.equals(version.projectId(), projectId) || !Objects.equals(version.versionId(), versionId)
                || version.documentId() == null) throw error(ErrorCode.RESOURCE_NOT_FOUND);
        Set<Long> ids = new HashSet<>();
        if (version.sections().isEmpty()) throw error(ErrorCode.CONFLICT);
        for (var section : version.sections()) {
            if (section.sectionId() == null || !ids.add(section.sectionId()) || section.type() == null || section.type().isBlank()
                    || section.content() == null || section.content().isBlank()) throw error(ErrorCode.CONFLICT);
        }
    }
    private Job view(QuizGenerationJob job) {
        return new Job("quiz-" + job.getId(), "QUIZ_GENERATION", job.getVersionId(), job.getStatus().name(), job.getQuizSetId(), job.getErrorCode(), job.getCreatedAt());
    }
    private BusinessException error(ErrorCode code) { return new BusinessException(code); }
}
