package com.afterme.backend.completion.service;

import com.afterme.backend.completion.domain.*;
import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.completion.repository.*;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.*;
import com.afterme.backend.handover.repository.*;
import com.afterme.backend.organization.domain.*;
import com.afterme.backend.user.domain.User;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CompletionServiceTest {
    static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");
    @Mock HandoverProjectRepository projects;
    @Mock ProjectParticipantRepository participants;
    @Mock CompletionCriteriaRepository criteria;
    @Mock CompletionConfirmationRepository confirmations;
    CompletionService service;
    HandoverProject project;

    @BeforeEach void setup() {
        service = new CompletionService(projects, participants, criteria, confirmations, Clock.fixed(NOW, ZoneOffset.UTC));
        project = new HandoverProject();
        ReflectionTestUtils.setField(project, "status", "DRAFT");
    }

    void access() { given(projects.findAccessibleById(1L, 7L)).willReturn(Optional.of(project)); }
    void role() {
        access();
        given(participants.findParticipantsByProjectId(1L)).willReturn(List.of(participant(5L, 7L, ParticipantRole.PREDECESSOR)));
        given(projects.findByIdForUpdate(1L)).willReturn(Optional.of(project));
    }

    ProjectParticipant participant(Long memberId, Long userId, ParticipantRole role) {
        User user = User.create("user" + userId + "@example.test", "hash", "Name");
        ReflectionTestUtils.setField(user, "id", userId);
        OrganizationMember member = new OrganizationMember();
        ReflectionTestUtils.setField(member, "id", memberId);
        ReflectionTestUtils.setField(member, "user", user);
        ReflectionTestUtils.setField(member, "status", OrganizationMemberStatus.ACTIVE);
        ProjectParticipant p = ProjectParticipant.predecessor(project, member);
        ReflectionTestUtils.setField(p, "role", role);
        return p;
    }

    @Test void outsiderCannotReadUpdateOrConfirm() {
        error(() -> service.state(7L, 1L), ErrorCode.RESOURCE_NOT_FOUND);
        error(() -> service.updateCriteria(7L, 1L, new UpdateCriteria(90, 80, 0L)), ErrorCode.RESOURCE_NOT_FOUND);
        error(() -> service.confirm(7L, 1L, new Confirm(ParticipantRole.PREDECESSOR)), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(criteria, confirmations, participants);
    }

    @Test void adminWithoutAssignedRoleCannotUpdateOrImpersonateParticipant() {
        access();
        error(() -> service.updateCriteria(7L, 1L, new UpdateCriteria(90, 80, 0L)), ErrorCode.FORBIDDEN);
        error(() -> service.confirm(7L, 1L, new Confirm(ParticipantRole.SUCCESSOR)), ErrorCode.FORBIDDEN);
        verifyNoInteractions(criteria, confirmations);
    }

    @Test void predecessorCanPartiallyUpdateCriteria() {
        role();
        given(criteria.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        Criteria result = service.updateCriteria(7L, 1L, new UpdateCriteria(null, 90, 0L));
        assertThat(result.minimumCompleteness()).isEqualTo(80);
        assertThat(result.quizPassingScore()).isEqualTo(90);
        assertThat(result.revision()).isEqualTo(1);
        assertThat(result.updatedBy()).isEqualTo(7L);
    }

    @Test void staleAndEmptyCriteriaUpdatesDoNotSave() {
        role();
        error(() -> service.updateCriteria(7L, 1L, new UpdateCriteria(90, 80, 9L)), ErrorCode.CONFLICT);
        error(() -> service.updateCriteria(7L, 1L, new UpdateCriteria(null, null, 0L)), ErrorCode.INVALID_INPUT);
        verify(criteria, never()).save(any());
    }

    @Test void confirmationIsIdempotentForSameMemberAndRole() {
        role();
        var existing = CompletionConfirmation.confirm(1L, 5L, ParticipantRole.PREDECESSOR, 0, NOW);
        ReflectionTestUtils.setField(existing, "id", 19L);
        given(confirmations.findFirstByProjectIdAndMemberIdAndRoleOrderByConfirmedAtAscIdAsc(1L, 5L, ParticipantRole.PREDECESSOR))
                .willReturn(Optional.of(existing));
        assertThat(service.confirm(7L, 1L, new Confirm(ParticipantRole.PREDECESSOR)).confirmationId()).isEqualTo(19L);
        verify(confirmations, never()).save(any());
        verifyNoInteractions(criteria);
    }

    @Test void newConfirmationUsesAuthenticatedMembershipAndServerTime() {
        role();
        given(confirmations.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        Confirmation result = service.confirm(7L, 1L, new Confirm(ParticipantRole.PREDECESSOR));
        assertThat(result.memberId()).isEqualTo(5L);
        assertThat(result.confirmedAt()).isEqualTo(NOW);
        assertThat(result.criteriaRevision()).isZero();
    }

    @Test void newConfirmationRecordsCurrentCriteriaAsMetadata() {
        role();
        var changed = CompletionCriteria.defaults(1L);
        changed.update(null, 90, 7L, NOW);
        given(criteria.findById(1L)).willReturn(Optional.of(changed));
        given(confirmations.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.confirm(7L, 1L, new Confirm(ParticipantRole.PREDECESSOR)).criteriaRevision()).isEqualTo(1);
    }

    @Test void completedProjectRejectsNewWrites() {
        role();
        ReflectionTestUtils.setField(project, "status", "COMPLETED");
        error(() -> service.updateCriteria(7L, 1L, new UpdateCriteria(90, 80, 0L)), ErrorCode.CONFLICT);
        error(() -> service.confirm(7L, 1L, new Confirm(ParticipantRole.PREDECESSOR)), ErrorCode.CONFLICT);
        verify(criteria, never()).save(any());
        verify(confirmations, never()).save(any());
    }

    @Test void missingParticipantsAreNotConsideredConfirmed() {
        access();
        State result = service.state(7L, 1L);
        assertThat(result.criteria().minimumCompleteness()).isEqualTo(80);
        assertThat(result.criteria().quizPassingScore()).isEqualTo(70);
        assertThat(result.predecessorConfirmed()).isFalse();
        assertThat(result.successorConfirmed()).isFalse();
        assertThat(result.eligibilityEvaluated()).isFalse();
    }

    @Test void statusRequiresEveryAssignedParticipantToConfirm() {
        access();
        given(participants.findParticipantsByProjectId(1L)).willReturn(List.of(
                participant(5L, 7L, ParticipantRole.PREDECESSOR), participant(6L, 8L, ParticipantRole.SUCCESSOR)));
        given(confirmations.findByProjectIdOrderByConfirmedAtAscIdAsc(1L)).willReturn(List.of(
                CompletionConfirmation.confirm(1L, 5L, ParticipantRole.PREDECESSOR, 0, NOW)));
        State result = service.state(7L, 1L);
        assertThat(result.predecessorConfirmed()).isTrue();
        assertThat(result.successorConfirmed()).isFalse();
        assertThat(result.projectStatus()).isEqualTo("DRAFT");
    }

    void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}
