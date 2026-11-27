package com.afterme.backend.interview.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.interview.dto.InterviewDtos.*;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class InterviewServiceIntegrationTest {

    @Autowired InterviewService interviews;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    Long predecessor, successor, organization, project, task;

    @BeforeEach
    void fixture() {
        predecessor = user("Predecessor");
        successor = user("Successor");
        organization = jdbc.queryForObject("insert into organization(owner_id) values (?) returning id", Long.class, predecessor);
        project = project();
        participant(project, predecessor, "ADMIN", "PREDECESSOR");
        participant(project, successor, "MEMBER", "SUCCESSOR");
        task = task(project);
    }

    @Test
    void startCopiesActiveTemplatesAndIsIdempotent() {
        var first = interviews.start(predecessor, project, task);
        var again = interviews.start(predecessor, project, task);

        Long templateCount = jdbc.queryForObject("select count(*) from interview_question_template where active", Long.class);
        assertThat(first.created()).isTrue();
        assertThat(first.session().questions()).hasSize(templateCount.intValue())
                .allSatisfy(q -> assertThat(q.type()).isEqualTo("BASE"));
        assertThat(again.created()).isFalse();
        assertThat(again.session().sessionId()).isEqualTo(first.session().sessionId());
    }

    @Test
    void startRejectsTaskFromAnotherProject() {
        Long otherProject = project();
        Long otherTask = task(otherProject);
        assertError(() -> interviews.start(predecessor, project, otherTask), ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Test
    void successorCannotStartOrReadInterview() {
        Long sessionId = start();
        assertError(() -> interviews.start(successor, project, task), ErrorCode.FORBIDDEN);
        assertError(() -> interviews.get(successor, project, sessionId), ErrorCode.FORBIDDEN);
    }

    @Test
    void savingKeepsEveryRevision() {
        Long sessionId = start();
        Long questionId = firstQuestion(sessionId);

        interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("첫 답변", null));
        var second = interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("고친 답변", null));

        assertThat(second.revisionNo()).isEqualTo(2);
        assertThat(interviews.revisions(predecessor, project, sessionId, second.answerId()))
                .extracting(RevisionView::content).containsExactly("고친 답변", "첫 답변");
        assertThat(interviews.get(predecessor, project, sessionId).questions().get(0).answer().content())
                .isEqualTo("고친 답변");
    }

    @Test
    void sameContentDoesNotCreateNewRevision() {
        Long sessionId = start();
        Long questionId = firstQuestion(sessionId);

        interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("같은 답변", null));
        var again = interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("같은 답변", null));

        assertThat(again.revisionNo()).isEqualTo(1);
        assertThat(interviews.revisions(predecessor, project, sessionId, again.answerId())).hasSize(1);
    }

    @Test
    void staleExpectedRevisionIsRejected() {
        Long sessionId = start();
        Long questionId = firstQuestion(sessionId);

        interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("탭 A", 0));
        assertError(() -> interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("탭 B", 0)),
                ErrorCode.CONFLICT);
    }

    @Test
    void confirmRequiresEveryRequiredAnswer() {
        Long sessionId = start();
        assertError(() -> interviews.confirm(predecessor, project, sessionId), ErrorCode.CONFLICT);

        int requiredCount = answerAllRequired(sessionId, "답변");
        var confirmation = interviews.confirm(predecessor, project, sessionId);

        assertThat(confirmation.revisionCount()).isEqualTo(requiredCount);
        assertThat(interviews.get(predecessor, project, sessionId).status()).isEqualTo("CONFIRMED");
    }

    @Test
    void editingAfterConfirmReopensAndKeepsPreviousConfirmation() {
        Long sessionId = start();
        answerAllRequired(sessionId, "처음");
        var first = interviews.confirm(predecessor, project, sessionId);

        var edited = interviews.saveAnswer(predecessor, project, sessionId, firstQuestion(sessionId), new SaveAnswer("수정", null));
        assertThat(edited.sessionStatus()).isEqualTo("IN_PROGRESS");

        var second = interviews.confirm(predecessor, project, sessionId);
        assertThat(second.confirmationId()).isNotEqualTo(first.confirmationId());
        em.flush();
        assertThat(jdbc.queryForObject("select count(*) from interview_confirmation_item where confirmation_id = ?",
                Integer.class, first.confirmationId())).isEqualTo(first.revisionCount());
    }

    @Test
    void confirmingTwiceWithoutChangesReturnsSameConfirmation() {
        Long sessionId = start();
        answerAllRequired(sessionId, "답변");

        var first = interviews.confirm(predecessor, project, sessionId);
        var again = interviews.confirm(predecessor, project, sessionId);

        assertThat(again.confirmationId()).isEqualTo(first.confirmationId());
    }

    @Test
    void completedProjectIsReadOnly() {
        Long sessionId = start();
        Long questionId = firstQuestion(sessionId);
        em.flush();
        jdbc.update("update handover_project set completed_at = current_timestamp where id = ?", project);
        em.clear();

        assertError(() -> interviews.saveAnswer(predecessor, project, sessionId, questionId, new SaveAnswer("답변", null)),
                ErrorCode.CONFLICT);
    }

    private Long start() {
        return interviews.start(predecessor, project, task).session().sessionId();
    }

    private Long firstQuestion(Long sessionId) {
        return interviews.get(predecessor, project, sessionId).questions().get(0).questionId();
    }

    private int answerAllRequired(Long sessionId, String content) {
        var required = interviews.get(predecessor, project, sessionId).questions().stream()
                .filter(QuestionView::required).toList();
        required.forEach(q -> interviews.saveAnswer(predecessor, project, sessionId, q.questionId(),
                new SaveAnswer(content + " " + q.sequence(), null)));
        return required.size();
    }

    private Long project() {
        return jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Interview project', 'Role', current_date + 1) returning id
                """, Long.class, organization);
    }

    private Long task(Long projectId) {
        return jdbc.queryForObject("""
                insert into handover_task(project_id, title, created_at, updated_at)
                values (?, '회계 보고', current_timestamp, current_timestamp) returning id
                """, Long.class, projectId);
    }

    private Long user(String name) {
        return jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', ?, current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test", name);
    }

    private void participant(Long projectId, Long userId, String organizationRole, String role) {
        Long member = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, ?, 'ACTIVE') returning id
                """, Long.class, organization, userId, organizationRole);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)", projectId, member, role);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}