package com.afterme.backend.qa.service;

import com.afterme.backend.global.exception.*;
import com.afterme.backend.qa.domain.QaStatus;
import com.afterme.backend.qa.dto.QaDtos.*;
import com.afterme.backend.qa.port.PublishedSectionReader;
import com.afterme.backend.qa.port.PublishedSectionReader.SectionReference;
import com.afterme.backend.qa.repository.QaThreadRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@Transactional
class QaServiceIntegrationTest {
    @Autowired QaService service;
    @Autowired QaThreadRepository threads;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    // Only the missing document adapter is mocked; Q&A writes and queries use PostgreSQL.
    @MockitoBean PublishedSectionReader sections;

    @Test void persistsQuestionAnswerRevisionStatusAndOutboxAcrossReloads() {
        Long predecessor = user("Predecessor");
        Long successor = user("Successor");
        Long organization = jdbc.queryForObject("insert into organization(owner_id) values (?) returning id", Long.class, predecessor);
        Long project = jdbc.queryForObject("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Q&A project', 'Role', current_date + 1) returning id
                """, Long.class, organization);
        participant(organization, project, predecessor, "PREDECESSOR");
        participant(organization, project, successor, "SUCCESSOR");
        given(sections.requirePublishedSection(anyLong(), anyLong())).willAnswer(invocation -> {
            Long projectId = invocation.getArgument(0);
            Long sectionId = invocation.getArgument(1);
            if (!project.equals(projectId) || sectionId != 456L) throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
            return new SectionReference(project, 123L, sectionId);
        });

        Question created = service.create(successor, project, new CreateQuestion(456L, "Original question", " Original title "));
        assertThat(created.authorName()).isEqualTo("Successor");
        assertThat(created.authorRole().name()).isEqualTo("SUCCESSOR");
        Long threadId = created.threadId();
        em.flush();
        em.clear();
        assertThat(service.list(successor, project, QaStatus.OPEN, 0, 20).items())
                .singleElement().satisfies(question -> assertThat(question.title()).isEqualTo("Original title"));
        Reply reply = service.reply(predecessor, project, threadId, new CreateReply(1, "Answer"));
        em.flush();
        em.clear();
        assertThat(service.detail(successor, project, threadId).question().status()).isEqualTo(QaStatus.RESOLVED);
        assertThat(service.list(successor, project, QaStatus.OPEN, 0, 20).items()).isEmpty();
        assertThat(service.list(predecessor, project, QaStatus.RESOLVED, 0, 20).items()).hasSize(1);
        service.edit(successor, project, threadId, new EditQuestion(1, "Changed question"));
        em.flush();
        em.clear();
        Detail detail = service.detail(predecessor, project, threadId);
        assertThat(detail.question().status()).isEqualTo(QaStatus.RESOLVED);
        assertThat(detail.question().revision()).isEqualTo(2);
        assertThat(detail.question().title()).isEqualTo("Original title");
        var renamed = service.edit(successor, project, threadId, new EditQuestion(2, "Changed question", "New title"));
        assertThat(renamed.revision()).isEqualTo(3);
        em.flush();
        em.clear();
        assertThat(service.detail(predecessor, project, threadId).question().title()).isEqualTo("New title");
        assertThat(detail.replies()).singleElement().satisfies(r -> {
            assertThat(r.replyId()).isEqualTo(reply.replyId());
            assertThat(r.authorName()).isEqualTo("Predecessor");
            assertThat(r.authorRole().name()).isEqualTo("PREDECESSOR");
            assertThat(r.questionContent()).isEqualTo("Original question");
            assertThat(r.questionRevision()).isEqualTo(1);
        });
        assertThat(threads.countByProjectIdAndStatus(project, QaStatus.OPEN)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from qa_outbox_event where thread_id = ? and processed_at is null",
                Long.class, threadId)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct dedupe_key) from qa_outbox_event where thread_id = ?",
                Long.class, threadId)).isEqualTo(2);
    }

    private Long user(String name) {
        return jdbc.queryForObject("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', ?, current_timestamp, current_timestamp) returning id
                """, Long.class, UUID.randomUUID() + "@example.test", name);
    }

    private void participant(Long organization, Long project, Long userId, String role) {
        Long member = jdbc.queryForObject("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, 'MEMBER', 'ACTIVE') returning id
                """, Long.class, organization, userId);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)", project, member, role);
    }
}
