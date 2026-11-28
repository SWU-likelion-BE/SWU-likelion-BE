package com.afterme.backend.qa.service;

import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.*;
import com.afterme.backend.handover.repository.*;
import com.afterme.backend.organization.domain.OrganizationMember;
import com.afterme.backend.qa.domain.*;
import com.afterme.backend.qa.dto.QaDtos.*;
import com.afterme.backend.qa.port.PublishedSectionReader;
import com.afterme.backend.qa.port.PublishedSectionReader.SectionReference;
import com.afterme.backend.qa.repository.*;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QaServiceTest {
    static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");
    @Mock HandoverProjectRepository projects;
    @Mock ProjectParticipantRepository participants;
    @Mock QaThreadRepository threads;
    @Mock QaReplyRepository replies;
    @Mock QaOutboxEventRepository events;
    @Mock PublishedSectionReader sections;
    @Mock QaAttachmentRepository attachments;
    @Mock UserRepository users;
    QaService service;

    @BeforeEach void setup() {
        service = new QaService(projects, participants, threads, replies, events, sections, attachments, users, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    void access() {
        given(projects.findAccessibleById(1L, 2L)).willReturn(Optional.of(new HandoverProject()));
    }

    void role(ParticipantRole role) {
        access();
        given(projects.findByIdForUpdate(1L)).willReturn(Optional.of(new HandoverProject()));
        User user = mock(User.class);
        given(user.getId()).willReturn(2L);
        OrganizationMember member = mock(OrganizationMember.class);
        given(member.isActive()).willReturn(true);
        given(member.getUser()).willReturn(user);
        ProjectParticipant participant = mock(ProjectParticipant.class);
        given(participant.getRole()).willReturn(role);
        given(participant.getMember()).willReturn(member);
        given(participants.findParticipantsByProjectId(1L)).willReturn(List.of(participant));
    }

    QaThread thread(Long author) {
        QaThread thread = QaThread.create(1L, 3L, 4L, author, "Original", NOW);
        ReflectionTestUtils.setField(thread, "id", 5L);
        return thread;
    }

    void published() { given(sections.requirePublishedSection(1L, 4L)).willReturn(new SectionReference(1L, 3L, 4L)); }

    @Test void outsiderCannotReadOrWrite() {
        error(() -> service.create(2L, 1L, new CreateQuestion(4L, "Question")), ErrorCode.RESOURCE_NOT_FOUND);
        error(() -> service.detail(2L, 1L, 5L), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(threads, replies, events, sections);
    }

    @Test void projectAdminWithoutSuccessorRoleCannotAsk() {
        access();
        given(participants.findParticipantsByProjectId(1L)).willReturn(List.of());
        error(() -> service.create(2L, 1L, new CreateQuestion(4L, "Question")), ErrorCode.FORBIDDEN);
        verifyNoInteractions(threads, events, sections);
    }

    @Test void successorCreatesQuestionAndDurableNotificationEvent() {
        role(ParticipantRole.SUCCESSOR);
        published();
        given(threads.save(any())).willAnswer(invocation -> {
            QaThread thread = invocation.getArgument(0);
            ReflectionTestUtils.setField(thread, "id", 5L);
            return thread;
        });
        Question result = service.create(2L, 1L, new CreateQuestion(4L, " Question "));
        assertThat(result.status()).isEqualTo(QaStatus.OPEN);
        assertThat(result.versionId()).isEqualTo(3L);
        verify(events).save(argThat(e -> e.getType() == QaOutboxEvent.Type.QUESTION_CREATED
                && e.getDedupeKey().equals("QUESTION_CREATED:5")));
    }

    @Test void unpublishedOrWrongProjectSectionCannotBeUsed() {
        role(ParticipantRole.SUCCESSOR);
        given(sections.requirePublishedSection(1L, 4L)).willThrow(new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        error(() -> service.create(2L, 1L, new CreateQuestion(4L, "Question")), ErrorCode.RESOURCE_NOT_FOUND);
        doReturn(new SectionReference(99L, 3L, 4L)).when(sections).requirePublishedSection(1L, 4L);
        error(() -> service.create(2L, 1L, new CreateQuestion(4L, "Question")), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(threads, events);
    }

    @Test void successorCannotEditAnotherAuthorsQuestion() {
        role(ParticipantRole.SUCCESSOR);
        given(threads.findForUpdate(5L, 1L)).willReturn(Optional.of(thread(99L)));
        error(() -> service.edit(2L, 1L, 5L, new EditQuestion(1, "Changed")), ErrorCode.FORBIDDEN);
        verifyNoInteractions(sections, events);
    }

    @Test void staleEditAndStaleAnswerAreConflicts() {
        role(ParticipantRole.SUCCESSOR);
        published();
        QaThread thread = thread(2L);
        thread.edit("Changed", NOW);
        given(threads.findForUpdate(5L, 1L)).willReturn(Optional.of(thread));
        error(() -> service.edit(2L, 1L, 5L, new EditQuestion(1, "Stale")), ErrorCode.CONFLICT);
        // This user is reassigned as predecessor for the answer check.
        role(ParticipantRole.PREDECESSOR);
        error(() -> service.reply(2L, 1L, 5L, new CreateReply(1, "Stale answer")), ErrorCode.CONFLICT);
        verifyNoInteractions(replies, events);
    }

    @Test void predecessorReplyResolvesQuestionAndRecordsEvent() {
        role(ParticipantRole.PREDECESSOR);
        published();
        QaThread thread = thread(9L);
        given(threads.findForUpdate(5L, 1L)).willReturn(Optional.of(thread));
        given(replies.save(any())).willAnswer(invocation -> {
            QaReply reply = invocation.getArgument(0);
            ReflectionTestUtils.setField(reply, "id", 6L);
            return reply;
        });
        Reply result = service.reply(2L, 1L, 5L, new CreateReply(1, "Answer"));
        assertThat(result.questionContent()).isEqualTo("Original");
        assertThat(thread.getStatus()).isEqualTo(QaStatus.RESOLVED);
        verify(events).save(argThat(e -> e.getType() == QaOutboxEvent.Type.ANSWER_CREATED
                && e.getDedupeKey().equals("ANSWER_CREATED:6")));
    }

    @Test void wrongProjectThreadIsNotFoundAndInvalidPagingIsRejected() {
        access();
        error(() -> service.detail(2L, 1L, 5L), ErrorCode.RESOURCE_NOT_FOUND);
        error(() -> service.list(2L, 1L, null, -1, 20), ErrorCode.INVALID_INPUT);
        error(() -> service.list(2L, 1L, null, 0, 101), ErrorCode.INVALID_INPUT);
        verifyNoInteractions(replies, sections);
    }

    @Test void listSupportsStatusAndDetailIncludesReplies() {
        access();
        published();
        QaThread thread = thread(2L);
        given(threads.findPage(eq(1L), eq(QaStatus.OPEN), any())).willReturn(new PageImpl<>(List.of(thread)));
        assertThat(service.list(2L, 1L, QaStatus.OPEN, 0, 20).items()).hasSize(1);
        given(threads.findByIdAndProjectId(5L, 1L)).willReturn(Optional.of(thread));
        given(replies.findByThread_IdOrderByIdAsc(5L)).willReturn(List.of(QaReply.create(thread, 9L, "Answer", NOW)));
        assertThat(service.detail(2L, 1L, 5L).replies().get(0).content()).isEqualTo("Answer");
    }

    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}
