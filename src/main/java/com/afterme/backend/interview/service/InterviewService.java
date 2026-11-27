package com.afterme.backend.interview.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handovertask.repository.HandoverTaskRepository;
import com.afterme.backend.interview.domain.*;
import com.afterme.backend.interview.dto.InterviewDtos.*;
import com.afterme.backend.interview.repository.*;
import com.afterme.backend.learning.service.LearningAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.afterme.backend.handover.domain.ParticipantRole.PREDECESSOR;

/**
 * AI 인터뷰 원본 답변 관리. 조회·수정 모두 전임자만 가능하다.
 * 쓰기 작업은 완료 승인과의 경쟁을 막기 위해 프로젝트 행을 먼저 잠근다.
 */
@Service
@RequiredArgsConstructor
public class InterviewService {

    private final LearningAccess access;
    private final HandoverTaskRepository tasks;
    private final InterviewQuestionTemplateRepository templates;
    private final InterviewSessionRepository sessions;
    private final InterviewQuestionRepository questions;
    private final InterviewAnswerRepository answers;
    private final AnswerRevisionRepository revisions;
    private final InterviewConfirmationRepository confirmations;
    private final Clock clock;

    /** @param created 새로 만들었으면 true, 기존 세션을 돌려줬으면 false */
    public record Started(boolean created, SessionDetail session) {
    }

    @Transactional
    public Started start(Long userId, Long projectId, Long taskId) {
        writable(userId, projectId);
        tasks.findByIdAndProjectId(taskId, projectId).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));

        var existing = sessions.findByTaskId(taskId);
        if (existing.isPresent()) return new Started(false, detail(existing.get()));

        Instant now = clock.instant();
        var session = sessions.save(new InterviewSession(projectId, taskId, userId, now));
        questions.saveAll(templates.findAllByActiveTrueOrderBySequenceAsc().stream()
                .map(template -> InterviewQuestion.base(session.getId(), template, now))
                .toList());
        return new Started(true, detail(session));
    }

    @Transactional(readOnly = true)
    public SessionDetail get(Long userId, Long projectId, Long sessionId) {
        access.role(userId, projectId, PREDECESSOR);
        return detail(session(projectId, sessionId));
    }

    @Transactional
    public SavedAnswer saveAnswer(Long userId, Long projectId, Long sessionId, Long questionId, SaveAnswer request) {
        writable(userId, projectId);
        var session = session(projectId, sessionId);
        questions.findByIdAndSessionId(questionId, sessionId).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));

        Instant now = clock.instant();
        var answer = answers.findByQuestionId(questionId)
                .orElseGet(() -> answers.save(new InterviewAnswer(questionId, userId, now)));
        var current = currentRevision(answer);
        int currentNo = current == null ? 0 : current.getRevisionNo();

        // 다른 탭·기기에서 먼저 수정했으면 덮어쓰지 않고 409
        if (request.expectedRevisionNo() != null && request.expectedRevisionNo() != currentNo) {
            throw error(ErrorCode.CONFLICT);
        }
        // 내용이 같으면 새 버전을 만들지 않는다(같은 요청 재전송 대비)
        if (current != null && current.getRawText().equals(request.content())) {
            return saved(answer, current, session);
        }

        var revision = revisions.save(new AnswerRevision(answer.getId(), currentNo + 1, request.content(), userId, now));
        answer.pointTo(revision, now);
        if (session.getStatus() == InterviewSession.Status.CONFIRMED) session.reopen(now);
        return saved(answer, revision, session);
    }

    @Transactional(readOnly = true)
    public List<RevisionView> revisions(Long userId, Long projectId, Long sessionId, Long answerId) {
        access.role(userId, projectId, PREDECESSOR);
        session(projectId, sessionId);
        var answer = answers.findById(answerId).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        questions.findByIdAndSessionId(answer.getQuestionId(), sessionId)
                .orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
        return revisions.findAllByAnswerIdOrderByRevisionNoDesc(answerId).stream()
                .map(r -> new RevisionView(r.getId(), r.getRevisionNo(), r.getRawText(), r.getCreatedBy(), r.getCreatedAt()))
                .toList();
    }

    @Transactional
    public Confirmation confirm(Long userId, Long projectId, Long sessionId) {
        writable(userId, projectId);
        var session = session(projectId, sessionId);

        // 확정 후 바뀐 답변이 없으면 기존 확정을 그대로 돌려준다
        if (session.getStatus() == InterviewSession.Status.CONFIRMED) {
            return toView(confirmations.findFirstBySessionIdOrderByIdDesc(sessionId)
                    .orElseThrow(() -> error(ErrorCode.CONFLICT)));
        }

        var questionList = questions.findAllBySessionIdOrderBySequenceAsc(sessionId);
        var answered = answeredByQuestion(questionList);
        boolean missingRequired = questionList.stream()
                .anyMatch(q -> q.isRequired() && !answered.containsKey(q.getId()));
        if (missingRequired) throw error(ErrorCode.CONFLICT);

        Set<Long> revisionIds = answered.values().stream()
                .map(InterviewAnswer::getCurrentRevisionId)
                .collect(Collectors.toSet());
        Instant now = clock.instant();
        var confirmation = confirmations.save(new InterviewConfirmation(sessionId, userId, revisionIds, now));
        session.confirm(now);
        return toView(confirmation);
    }

    private void writable(Long userId, Long projectId) {
        access.role(userId, projectId, PREDECESSOR);
        access.mutable(access.lock(projectId));
    }

    private InterviewSession session(Long projectId, Long sessionId) {
        return sessions.findByIdAndProjectId(sessionId, projectId)
                .orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private AnswerRevision currentRevision(InterviewAnswer answer) {
        if (answer.getCurrentRevisionId() == null) return null;
        return revisions.findById(answer.getCurrentRevisionId()).orElseThrow();
    }

    /** 최신 버전이 있는 답변만 질문 ID 기준으로 모은다. */
    private Map<Long, InterviewAnswer> answeredByQuestion(List<InterviewQuestion> questionList) {
        var questionIds = questionList.stream().map(InterviewQuestion::getId).toList();
        return answers.findAllByQuestionIdIn(questionIds).stream()
                .filter(a -> a.getCurrentRevisionId() != null)
                .collect(Collectors.toMap(InterviewAnswer::getQuestionId, a -> a));
    }

    private SessionDetail detail(InterviewSession session) {
        var questionList = questions.findAllBySessionIdOrderBySequenceAsc(session.getId());
        var answered = answeredByQuestion(questionList);
        var revisionById = revisions.findAllById(answered.values().stream()
                        .map(InterviewAnswer::getCurrentRevisionId).toList()).stream()
                .collect(Collectors.toMap(AnswerRevision::getId, r -> r));

        var views = questionList.stream().map(q -> {
            var answer = answered.get(q.getId());
            AnswerView answerView = null;
            if (answer != null) {
                var r = revisionById.get(answer.getCurrentRevisionId());
                answerView = new AnswerView(answer.getId(), r.getRevisionNo(), r.getRawText(), answer.getUpdatedAt());
            }
            return new QuestionView(q.getId(), q.getType().name(), q.getTopic(), q.getContent(),
                    q.getParentQuestionId(), q.getSequence(), q.isRequired(), answerView);
        }).toList();
        return new SessionDetail(session.getId(), session.getTaskId(), session.getStatus().name(), views);
    }

    private SavedAnswer saved(InterviewAnswer answer, AnswerRevision revision, InterviewSession session) {
        return new SavedAnswer(answer.getId(), answer.getQuestionId(), revision.getRevisionNo(),
                revision.getRawText(), session.getStatus().name(), answer.getUpdatedAt());
    }

    private Confirmation toView(InterviewConfirmation c) {
        return new Confirmation(c.getId(), c.getSessionId(), c.getRevisionIds().size(), c.getConfirmedAt());
    }

    private BusinessException error(ErrorCode code) {
        return new BusinessException(code);
    }
}