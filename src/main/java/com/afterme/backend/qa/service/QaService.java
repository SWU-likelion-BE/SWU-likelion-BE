package com.afterme.backend.qa.service;

import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.repository.*;
import com.afterme.backend.qa.domain.*;
import com.afterme.backend.qa.dto.QaDtos.*;
import com.afterme.backend.qa.port.PublishedSectionReader;
import com.afterme.backend.qa.port.PublishedSectionReader.SectionReference;
import com.afterme.backend.qa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class QaService {
    private final HandoverProjectRepository projects;
    private final ProjectParticipantRepository participants;
    private final QaThreadRepository threads;
    private final QaReplyRepository replies;
    private final QaOutboxEventRepository events;
    private final PublishedSectionReader sections;
    private final QaAttachmentRepository attachments;
    private final Clock clock;

    @Transactional
    public Question create(Long userId, Long projectId, CreateQuestion request) {
        requireRole(userId, projectId, ParticipantRole.SUCCESSOR);
        lockProject(projectId);
        SectionReference section = section(projectId, request.sectionId());
        var now = clock.instant();
        QaThread thread = threads.save(QaThread.create(projectId, section.versionId(), section.sectionId(),
                userId, request.content(), request.title(), now));
        events.save(QaOutboxEvent.questionCreated(thread, now));
        return question(thread, section);
    }

    @Transactional
    public Question edit(Long userId, Long projectId, Long threadId, EditQuestion request) {
        requireRole(userId, projectId, ParticipantRole.SUCCESSOR);
        lockProject(projectId);
        QaThread thread = lockedThread(projectId, threadId);
        if (!thread.getAuthorId().equals(userId)) fail(ErrorCode.FORBIDDEN);
        SectionReference section = verifySection(thread);
        if (!Objects.equals(request.revision(), thread.getRevision())) fail(ErrorCode.CONFLICT);
        thread.edit(request.content(), request.title(), clock.instant());
        return question(thread, section);
    }

    public List<SectionOption> listSections(Long userId, Long projectId) {
        requireAccess(userId, projectId);
        return sections.listPublishedSections(projectId).stream()
                .map(section -> new SectionOption(section.sectionId(), section.versionId(), section.taskId(),
                        section.title(), section.displayOrder()))
                .toList();
    }

    public QuestionPage list(Long userId, Long projectId, QaStatus status, int page, int size) {
        requireAccess(userId, projectId);
        if (page < 0 || size < 1 || size > 100) fail(ErrorCode.INVALID_INPUT);
        var result = threads.findPage(projectId, status, PageRequest.of(page, size, Sort.by("id").descending()));
        return new QuestionPage(result.map(thread -> question(thread, verifySection(thread))).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    public Detail detail(Long userId, Long projectId, Long threadId) {
        requireAccess(userId, projectId);
        QaThread thread = threads.findByIdAndProjectId(threadId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        SectionReference section = verifySection(thread);
        return new Detail(question(thread, section),
                replies.findByThread_IdOrderByIdAsc(threadId).stream().map(this::reply).toList(),
                attachments.listMetadata(threadId).stream()
                        .map(file -> QaAttachmentService.metadata(projectId, threadId, file)).toList());
    }

    @Transactional
    public Reply reply(Long userId, Long projectId, Long threadId, CreateReply request) {
        requireRole(userId, projectId, ParticipantRole.PREDECESSOR);
        lockProject(projectId);
        QaThread thread = lockedThread(projectId, threadId);
        verifySection(thread);
        if (!Objects.equals(request.questionRevision(), thread.getRevision())) fail(ErrorCode.CONFLICT);
        var now = clock.instant();
        QaReply reply = replies.save(QaReply.create(thread, userId, request.content(), now));
        thread.answered(now);
        events.save(QaOutboxEvent.answerCreated(thread, reply, now));
        return reply(reply);
    }

    private void lockProject(Long projectId) {
        projects.findByIdForUpdate(projectId).orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private QaThread lockedThread(Long projectId, Long threadId) {
        return threads.findForUpdate(threadId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private SectionReference section(Long projectId, Long sectionId) {
        SectionReference section = sections.requirePublishedSection(projectId, sectionId);
        if (section == null || !Objects.equals(projectId, section.projectId())
                || !Objects.equals(sectionId, section.sectionId()) || section.versionId() == null) {
            fail(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return section;
    }

    private SectionReference verifySection(QaThread thread) {
        SectionReference reference = section(thread.getProjectId(), thread.getSectionId());
        if (!Objects.equals(thread.getVersionId(), reference.versionId())) {
            fail(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return reference;
    }

    private void requireAccess(Long userId, Long projectId) {
        projects.findAccessibleById(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireRole(Long userId, Long projectId, ParticipantRole role) {
        requireAccess(userId, projectId);
        boolean allowed = participants.findParticipantsByProjectId(projectId).stream().anyMatch(p ->
                p.getRole() == role && p.getMember().isActive() && p.getMember().getUser().getId().equals(userId));
        if (!allowed) fail(ErrorCode.FORBIDDEN);
    }

    private Question question(QaThread thread, SectionReference section) {
        return new Question(thread.getId(), thread.getProjectId(), thread.getVersionId(), thread.getSectionId(),
                thread.getAuthorId(), thread.getContent(), thread.getStatus(), thread.getRevision(),
                thread.getReplyCount(), thread.getCreatedAt(), thread.getUpdatedAt(), section.title(),
                section.displayOrder(), thread.getTitle());
    }

    private Reply reply(QaReply reply) {
        return new Reply(reply.getId(), reply.getAuthorId(), reply.getContent(), reply.getQuestionRevision(),
                reply.getQuestionContent(), reply.getCreatedAt());
    }

    private static void fail(ErrorCode code) { throw new BusinessException(code); }
}
