package com.afterme.backend.quiz.service;

import com.afterme.backend.completion.domain.CompletionCriteria;
import com.afterme.backend.completion.repository.CompletionCriteriaRepository;
import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.*;
import com.afterme.backend.handover.repository.*;
import com.afterme.backend.organization.domain.OrganizationMember;
import com.afterme.backend.quiz.domain.*;
import com.afterme.backend.quiz.dto.QuizDtos.*;
import com.afterme.backend.quiz.repository.*;
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
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {
    static final Instant NOW = Instant.parse("2026-11-25T00:00:00Z");
    @Mock HandoverProjectRepository projects;
    @Mock ProjectParticipantRepository participants;
    @Mock QuizSetRepository quizzes;
    @Mock QuizAttemptRepository attempts;
    @Mock CompletionCriteriaRepository criteriaRepository;
    QuizService service;
    QuizSet quiz;

    @BeforeEach void setup() {
        service = at(NOW);
        QuizQuestion q = QuizQuestion.multipleChoice("Question", List.of("A", "B"), 0, "Explanation", 9L, "TOOLS");
        ReflectionTestUtils.setField(q, "id", 5L);
        quiz = QuizSet.ready(1L, 3L, 70, List.of(q));
        ReflectionTestUtils.setField(quiz, "id", 4L);
    }

    QuizService at(Instant now) {
        return new QuizService(projects, participants, quizzes, attempts, criteriaRepository, Clock.fixed(now, ZoneOffset.UTC));
    }

    void successorAccess() {
        given(projects.findAccessibleById(1L, 2L)).willReturn(Optional.of(new HandoverProject()));
        lenient().when(projects.findByIdForUpdate(1L)).thenReturn(Optional.of(new HandoverProject()));
        User user = mock(User.class);
        given(user.getId()).willReturn(2L);
        OrganizationMember member = mock(OrganizationMember.class);
        given(member.isActive()).willReturn(true);
        given(member.getUser()).willReturn(user);
        ProjectParticipant participant = mock(ProjectParticipant.class);
        given(participant.getRole()).willReturn(ParticipantRole.SUCCESSOR);
        given(participant.getMember()).willReturn(member);
        given(participants.findParticipantsByProjectId(1L)).willReturn(List.of(participant));
    }

    QuizAttempt attempt() {
        QuizAttempt attempt = QuizAttempt.start(quiz, 2L, 1, NOW);
        ReflectionTestUtils.setField(attempt, "id", 6L);
        return attempt;
    }

    @Test void inaccessibleProjectIsHidden() {
        given(projects.findAccessibleById(1L, 2L)).willReturn(Optional.empty());
        error(() -> service.start(2L, 1L, 4L), ErrorCode.RESOURCE_NOT_FOUND);
        verifyNoInteractions(quizzes, attempts);
    }

    @Test void predecessorCannotTakeQuiz() {
        given(projects.findAccessibleById(1L, 2L)).willReturn(Optional.of(new HandoverProject()));
        given(participants.findParticipantsByProjectId(1L)).willReturn(List.of());
        error(() -> service.start(2L, 1L, 4L), ErrorCode.FORBIDDEN);
        verifyNoInteractions(quizzes, attempts);
    }

    @Test void ongoingAttemptIsResumed() {
        successorAccess();
        given(quizzes.findForUpdate(4L, 1L)).willReturn(Optional.of(quiz));
        given(attempts.findFirstByQuizSet_IdAndUserIdOrderByAttemptNoDesc(4L, 2L)).willReturn(Optional.of(attempt()));
        assertThat(service.start(2L, 1L, 4L).attemptId()).isEqualTo(6L);
        verify(attempts, never()).save(any());
        verifyNoInteractions(criteriaRepository);
    }

    @Test void newAttemptUsesProjectCutoffAndKeepsItWhenCriteriaChanges() {
        successorAccess();
        given(quizzes.findForUpdate(4L, 1L)).willReturn(Optional.of(quiz));
        CompletionCriteria criteria = CompletionCriteria.defaults(1L);
        criteria.update(null, 90, 9L, NOW);
        given(criteriaRepository.findById(1L)).willReturn(Optional.of(criteria));
        given(attempts.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.start(2L, 1L, 4L).passingScore()).isEqualTo(90);
        QuizAttempt ongoing = QuizAttempt.start(quiz, 2L, 1, 90, NOW);
        criteria.update(null, 70, 9L, NOW.plusSeconds(1));
        given(attempts.findFirstByQuizSet_IdAndUserIdOrderByAttemptNoDesc(4L, 2L)).willReturn(Optional.of(ongoing));
        assertThat(service.start(2L, 1L, 4L).passingScore()).isEqualTo(90);
    }

    @Test void retryBlockedBefore72HoursAndAllowedAtExactBoundary() {
        successorAccess();
        QuizAttempt failed = attempt();
        failed.submit(Map.of(5L, 1), NOW);
        given(quizzes.findForUpdate(4L, 1L)).willReturn(Optional.of(quiz));
        given(attempts.findFirstByQuizSet_IdAndUserIdOrderByAttemptNoDesc(4L, 2L)).willReturn(Optional.of(failed));
        error(() -> at(NOW.plus(Duration.ofDays(3)).minusSeconds(1)).start(2L, 1L, 4L), ErrorCode.CONFLICT);
        given(attempts.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(at(NOW.plus(Duration.ofDays(3))).start(2L, 1L, 4L).attemptNo()).isEqualTo(2);
    }

    @Test void passingDoesNotIntroduceAnUndocumentedRetakeBan() {
        successorAccess();
        QuizAttempt passed = attempt();
        passed.submit(Map.of(5L, 0), NOW);
        given(quizzes.findForUpdate(4L, 1L)).willReturn(Optional.of(quiz));
        given(attempts.findFirstByQuizSet_IdAndUserIdOrderByAttemptNoDesc(4L, 2L)).willReturn(Optional.of(passed));
        given(attempts.save(any())).willAnswer(invocation -> invocation.getArgument(0));
        assertThat(service.start(2L, 1L, 4L).attemptNo()).isEqualTo(2);
        assertThat(passed.getScore()).isEqualTo(100);
    }

    @Test void anotherUsersAttemptIsNotFound() {
        successorAccess();
        given(quizzes.findByIdAndProjectId(4L, 1L)).willReturn(Optional.of(quiz));
        given(attempts.findByIdAndQuizSet_IdAndUserId(6L, 4L, 2L)).willReturn(Optional.empty());
        error(() -> service.questions(2L, 1L, 4L, 6L), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test void duplicateQuestionIdsAreRejectedAndResultsUnavailableBeforeSubmission() {
        successorAccess();
        QuizAttempt attempt = attempt();
        given(quizzes.findByIdAndProjectId(4L, 1L)).willReturn(Optional.of(quiz));
        given(attempts.findByIdAndQuizSet_IdAndUserId(6L, 4L, 2L)).willReturn(Optional.of(attempt));
        error(() -> service.result(2L, 1L, 4L, 6L), ErrorCode.CONFLICT);
        given(quizzes.findForUpdate(4L, 1L)).willReturn(Optional.of(quiz));
        error(() -> service.submit(2L, 1L, 4L, 6L,
                new Submission(List.of(new Answer(5L, 0), new Answer(5L, 1)))), ErrorCode.INVALID_INPUT);
        assertThat(attempt.getSubmittedAt()).isNull();
    }

    @Test void questionsExposeOnlySafeDtoAndResultsIncludeEvidence() {
        successorAccess();
        QuizAttempt attempt = attempt();
        given(quizzes.findByIdAndProjectId(4L, 1L)).willReturn(Optional.of(quiz));
        given(attempts.findByIdAndQuizSet_IdAndUserId(6L, 4L, 2L)).willReturn(Optional.of(attempt));
        assertThat(Arrays.stream(Question.class.getRecordComponents()).map(c -> c.getName()))
                .doesNotContain("correctOption", "explanation");
        assertThat(service.questions(2L, 1L, 4L, 6L).questions()).hasSize(1);
        given(quizzes.findForUpdate(4L, 1L)).willReturn(Optional.of(quiz));
        Result result = service.submit(2L, 1L, 4L, 6L, new Submission(List.of(new Answer(5L, 0))));
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.questions().get(0).sectionId()).isEqualTo(9L);
        assertThat(result.topics().get(0).correctCount()).isEqualTo(1);
    }

    private void error(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}
