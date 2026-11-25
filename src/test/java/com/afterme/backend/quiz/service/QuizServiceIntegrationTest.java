package com.afterme.backend.quiz.service;

import com.afterme.backend.quiz.domain.*;
import com.afterme.backend.quiz.dto.QuizDtos.*;
import com.afterme.backend.quiz.repository.QuizSetRepository;
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
class QuizServiceIntegrationTest {
    @Autowired QuizService service;
    @Autowired QuizSetRepository quizzes;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;

    @Test void persistsQuestionsOptionsAttemptsAndResponsesAcrossReloads() {
        Long userId = jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', 'Quiz successor', current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test");
        Long organizationId = jdbc.queryForObject("insert into organization(owner_id) values (?) returning id", Long.class, userId);
        Long memberId = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, 'MEMBER', 'ACTIVE') returning id
                """, Long.class, organizationId, userId);
        Long projectId = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Quiz project', 'Role', current_date + 1) returning id
                """, Long.class, organizationId);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, 'SUCCESSOR')", projectId, memberId);
        QuizSet quiz = quizzes.saveAndFlush(QuizSet.ready(projectId, 123L, 70, List.of(
                QuizQuestion.multipleChoice("Which tool?", List.of("A", "B"), 1, "Use B", 456L, "TOOLS"))));
        Long quizId = quiz.getId();
        Long questionId = quiz.getQuestions().get(0).getId();
        em.clear();

        assertThat(service.list(userId, projectId, 0, 20).items()).hasSize(1);
        Long attemptId = service.start(userId, projectId, quizId).attemptId();
        em.flush();
        em.clear();
        assertThat(service.start(userId, projectId, quizId).attemptId()).isEqualTo(attemptId);
        assertThat(service.questions(userId, projectId, quizId, attemptId).questions().get(0).options())
                .containsExactly("A", "B");
        service.submit(userId, projectId, quizId, attemptId, new Submission(List.of(new Answer(questionId, 1))));
        em.flush();
        em.clear();
        Result result = service.result(userId, projectId, quizId, attemptId);
        assertThat(result.score()).isEqualTo(100);
        assertThat(result.passed()).isTrue();
        assertThat(result.questions().get(0).selectedOption()).isEqualTo(1);
        assertThat(result.questions().get(0).explanation()).isEqualTo("Use B");
        assertThat(jdbc.queryForObject("select count(*) from quiz_response where attempt_id = ?", Long.class, attemptId)).isEqualTo(1L);
    }
}
