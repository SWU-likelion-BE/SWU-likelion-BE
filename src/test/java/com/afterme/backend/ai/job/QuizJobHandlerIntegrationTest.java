package com.afterme.backend.ai.job;

import com.afterme.backend.ai.client.AiCallException;
import com.afterme.backend.ai.client.OpenAiClient;
import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.learning.port.LearningDocumentReader;
import com.afterme.backend.learning.port.LearningDocumentReader.PublishedVersion;
import com.afterme.backend.learning.port.LearningDocumentReader.Section;
import com.afterme.backend.quiz.service.QuizGenerationService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
class QuizJobHandlerIntegrationTest {

    @Autowired QuizJobHandler handler;
    @Autowired AiJobRegistry registry;
    @Autowired QuizGenerationService generation;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @MockitoBean OpenAiClient openAi;
    @MockitoBean LearningDocumentReader documents;
    Long predecessor, successor, project;

    @BeforeEach
    void fixture() {
        predecessor = user("Predecessor");
        successor = user("Successor");
        Long organization = jdbc.queryForObject("insert into organization(owner_id) values (?) returning id", Long.class, predecessor);
        project = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'AI job project', 'Role', current_date + 1) returning id
                """, Long.class, organization);
        participant(organization, predecessor, "ADMIN", "PREDECESSOR");
        participant(organization, successor, "MEMBER", "SUCCESSOR");
        given(documents.requirePublishedVersion(project, 10L)).willReturn(new PublishedVersion(project, 20L, 10L,
                List.of(new Section(11L, "TOOLS", "공용 계정 비밀번호는 총무가 관리한다."))));
    }

    @Test
    void validOutputCompletesJob() {
        String jobId = request("key-success");
        given(openAi.request(any(), any(), any(), any())).willReturn(result(11L));

        handler.process(id(jobId), 3);

        assertThat(status(jobId).status()).isEqualTo("SUCCEEDED");
        assertThat(quizCount()).isEqualTo(1);
    }

    @Test
    void temporaryFailureRequeuesUntilMaxAttempts() {
        String jobId = request("key-timeout");
        given(openAi.request(any(), any(), any(), any())).willThrow(new AiCallException("AI_TIMEOUT"));

        handler.process(id(jobId), 2);
        assertThat(status(jobId).status()).isEqualTo("QUEUED");

        handler.process(id(jobId), 2);
        assertThat(status(jobId).status()).isEqualTo("FAILED");
        assertThat(status(jobId).errorCode()).isEqualTo("AI_TIMEOUT");
    }

    @Test
    void outputWithUnknownSectionIsInvalidOutput() {
        String jobId = request("key-invalid");
        given(openAi.request(any(), any(), any(), any())).willReturn(result(999L)); // 없는 근거 섹션

        handler.process(id(jobId), 1);

        assertThat(status(jobId).errorCode()).isEqualTo("AI_OUTPUT_INVALID");
        assertThat(quizCount()).isZero();
    }

    @Test
    void permanentFailureFailsImmediately() {
        String jobId = request("key-401");
        given(openAi.request(any(), any(), any(), any())).willThrow(new AiCallException("AI_HTTP_401"));

        handler.process(id(jobId), 3);

        assertThat(status(jobId).status()).isEqualTo("FAILED");
    }

    @Test
    void userRetryRequeuesOnlyFailedJobAndResetsAttempts() {
        String jobId = request("key-retry");
        given(openAi.request(any(), any(), any(), any())).willThrow(new AiCallException("AI_HTTP_401"));
        handler.process(id(jobId), 3);

        var retried = (QuizGenerationService.Job) registry.find(jobId).retry(predecessor, project, jobId);

        assertThat(retried.status()).isEqualTo("QUEUED");
        em.flush();
        assertThat(jdbc.queryForObject("select attempt_count from quiz_generation_job where id = ?", Integer.class, id(jobId))).isZero();
        assertError(() -> registry.find(jobId).retry(predecessor, project, jobId), ErrorCode.CONFLICT);
    }

    @Test
    void staleRunningJobIsRecovered() {
        String jobId = request("key-stale");
        generation.claim(id(jobId));
        em.flush();
        jdbc.update("update quiz_generation_job set started_at = now() - interval '1 hour' where id = ?", id(jobId));
        em.clear();
        Instant staleBefore = Instant.now().minus(Duration.ofMinutes(10));

        assertThat(handler.staleIds(staleBefore, 10)).contains(id(jobId));
        handler.recoverStale(id(jobId), staleBefore, 3);

        assertThat(status(jobId).status()).isEqualTo("QUEUED");
        assertThat(handler.queuedIds(10)).contains(id(jobId));
    }

    @Test
    void unknownPrefixIsNotFound() {
        assertError(() -> registry.find("unknown-1"), ErrorCode.RESOURCE_NOT_FOUND);
        assertError(() -> registry.find("noprefix"), ErrorCode.RESOURCE_NOT_FOUND);
    }

    private String request(String key) {
        return generation.request(predecessor, project, new QuizGenerationService.Request(10L, key)).jobId();
    }

    private Long id(String jobId) {
        return Long.valueOf(jobId.substring("quiz-".length()));
    }

    private QuizGenerationService.Job status(String jobId) {
        return generation.status(successor, project, jobId);
    }

    private long quizCount() {
        em.flush();
        return jdbc.queryForObject("select count(*) from quiz_set where project_id = ?", Long.class, project);
    }

    private OpenAiClient.Result result(Long sectionId) {
        var content = JsonMapper.shared().readTree("""
                {"questions":[{"stem":"공용 계정 비밀번호를 잊었다면?","options":["총무에게 문의","회장에게 문의","새로 가입","포기"],
                "correctOption":0,"explanation":"공용 계정 비밀번호는 총무가 관리한다.","sectionId":%d}]}
                """.formatted(sectionId));
        return new OpenAiClient.Result(content, "test-model");
    }

    private Long user(String name) {
        return jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', ?, current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test", name);
    }

    private void participant(Long organization, Long userId, String organizationRole, String role) {
        Long member = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, ?, 'ACTIVE') returning id
                """, Long.class, organization, userId, organizationRole);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)", project, member, role);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode()).isEqualTo(code);
    }
}