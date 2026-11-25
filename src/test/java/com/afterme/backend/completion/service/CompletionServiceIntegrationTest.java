package com.afterme.backend.completion.service;

import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.quiz.domain.*;
import com.afterme.backend.quiz.dto.QuizDtos.*;
import com.afterme.backend.quiz.repository.QuizSetRepository;
import com.afterme.backend.quiz.service.QuizService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@Transactional
class CompletionServiceIntegrationTest {
    @Autowired CompletionService service;
    @Autowired QuizService quizService;
    @Autowired QuizSetRepository quizzes;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    record Fixture(Long project, Long predecessor, Long successor) {}

    @Test void identityConfirmationsRemainValidAfterCriteriaChanges() {
        Fixture f = fixture();
        var initial = service.state(f.successor(), f.project());
        assertThat(initial.criteria().minimumCompleteness()).isEqualTo(80);
        assertThat(initial.criteria().quizPassingScore()).isEqualTo(70);
        var predecessor = service.confirm(f.predecessor(), f.project(), new Confirm(ParticipantRole.PREDECESSOR));
        var repeated = service.confirm(f.predecessor(), f.project(), new Confirm(ParticipantRole.PREDECESSOR));
        assertThat(repeated.confirmationId()).isEqualTo(predecessor.confirmationId());
        var successor = service.confirm(f.successor(), f.project(), new Confirm(ParticipantRole.SUCCESSOR));
        assertThat(service.state(f.successor(), f.project()).predecessorConfirmed()).isTrue();
        assertThat(service.state(f.successor(), f.project()).successorConfirmed()).isTrue();

        service.updateCriteria(f.predecessor(), f.project(), new UpdateCriteria(90, 80, 0L));
        em.flush();
        em.clear();
        var changed = service.state(f.successor(), f.project());
        assertThat(changed.criteria().revision()).isEqualTo(1);
        assertThat(changed.predecessorConfirmed()).isTrue();
        assertThat(changed.successorConfirmed()).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from completion_confirmation where project_id = ?", Long.class, f.project())).isEqualTo(2);

        assertThat(service.confirm(f.predecessor(), f.project(), new Confirm(ParticipantRole.PREDECESSOR)).confirmationId())
                .isEqualTo(predecessor.confirmationId());
        assertThat(service.confirm(f.successor(), f.project(), new Confirm(ParticipantRole.SUCCESSOR)).confirmationId())
                .isEqualTo(successor.confirmationId());
        assertThat(service.updateCriteria(f.predecessor(), f.project(), new UpdateCriteria(90, null, 1L)).revision()).isEqualTo(1);
        em.flush();
        em.clear();
        var state = service.state(f.successor(), f.project());
        assertThat(state.predecessorConfirmed()).isTrue();
        assertThat(state.successorConfirmed()).isTrue();
        assertThat(state.eligibilityEvaluated()).isFalse();
        assertThat(state.projectStatus()).isEqualTo("DRAFT");
        assertThat(state.completedAt()).isNull();
        assertThat(jdbc.queryForObject("select count(*) from completion_confirmation where project_id = ?", Long.class, f.project())).isEqualTo(2);
    }

    @Test void changedCutoffAffectsNewAttemptsAndPreservesOngoingAndSubmittedResults() {
        Fixture f = fixture();
        QuizSet oldQuiz = quiz(f.project(), 123L);
        Long oldQuizId = oldQuiz.getId();
        Long oldAttempt = quizService.start(f.successor(), f.project(), oldQuizId).attemptId();
        service.updateCriteria(f.predecessor(), f.project(), new UpdateCriteria(null, 80, 0L));
        assertThat(quizService.questions(f.successor(), f.project(), oldQuizId, oldAttempt).passingScore()).isEqualTo(70);
        Result oldResult = quizService.submit(f.successor(), f.project(), oldQuizId, oldAttempt, sevenCorrect(oldQuiz));
        assertThat(oldResult.passed()).isTrue();
        assertThat(oldResult.passingScore()).isEqualTo(70);

        QuizSet newQuiz = quiz(f.project(), 124L);
        Long newQuizId = newQuiz.getId();
        var started = quizService.start(f.successor(), f.project(), newQuizId);
        assertThat(started.passingScore()).isEqualTo(80);
        Result failed = quizService.submit(f.successor(), f.project(), newQuizId, started.attemptId(), sevenCorrect(newQuiz));
        assertThat(failed.score()).isEqualTo(70);
        assertThat(failed.passed()).isFalse();
        service.updateCriteria(f.predecessor(), f.project(), new UpdateCriteria(null, 70, 1L));
        em.flush();
        em.clear();
        var preserved = quizService.result(f.successor(), f.project(), newQuizId, started.attemptId());
        assertThat(preserved.passingScore()).isEqualTo(80);
        assertThat(preserved.passed()).isFalse();
        assertThat(preserved.nextAllowedAt()).isCloseTo(failed.nextAllowedAt(), within(1, java.time.temporal.ChronoUnit.MICROS));
        assertThat(quizService.result(f.successor(), f.project(), oldQuizId, oldAttempt).passed()).isTrue();
    }

    QuizSet quiz(Long projectId, Long versionId) {
        List<QuizQuestion> questions = new ArrayList<>();
        for (int i = 0; i < 10; i++) questions.add(QuizQuestion.multipleChoice("Question " + i,
                List.of("A", "B"), 0, "Evidence", 100L + i, "TOOLS"));
        return quizzes.saveAndFlush(QuizSet.ready(projectId, versionId, 70, questions));
    }

    Submission sevenCorrect(QuizSet quiz) {
        List<Answer> answers = new ArrayList<>();
        for (int i = 0; i < 10; i++) answers.add(new Answer(quiz.getQuestions().get(i).getId(), i < 7 ? 0 : 1));
        return new Submission(answers);
    }

    Fixture fixture() {
        Long predecessor = user("Predecessor");
        Long successor = user("Successor");
        Long organization = jdbc.queryForObject("insert into organization(owner_id) values (?) returning id", Long.class, predecessor);
        Long project = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Completion project', 'Role', current_date + 1) returning id
                """, Long.class, organization);
        participant(organization, project, predecessor, "PREDECESSOR");
        participant(organization, project, successor, "SUCCESSOR");
        return new Fixture(project, predecessor, successor);
    }

    Long user(String name) {
        return jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', ?, current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test", name);
    }

    void participant(Long organization, Long project, Long userId, String role) {
        Long member = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, 'MEMBER', 'ACTIVE') returning id
                """, Long.class, organization, userId);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)", project, member, role);
    }
}
