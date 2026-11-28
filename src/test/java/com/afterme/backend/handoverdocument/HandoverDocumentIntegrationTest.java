package com.afterme.backend.handoverdocument;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handoverdocument.dto.DraftResponse;
import com.afterme.backend.handoverdocument.dto.DraftSectionInput;
import com.afterme.backend.handoverdocument.dto.UpdateDraftRequest;
import com.afterme.backend.handoverdocument.service.HandoverDocumentService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class HandoverDocumentIntegrationTest {
    @Autowired HandoverDocumentService documents;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    private Long predecessor;
    private Long successor;
    private Long project;
    private Long task;
    private Long confirmation;

    @BeforeEach
    void fixture() {
        predecessor = user();
        successor = user();
        Long organization = insert("insert into organization(owner_id) values (?) returning id", predecessor);
        project = insert("""
                insert into handover_project(organization_id, title, role_name, dead_line)
                values (?, 'Handover', 'Role', current_date + 1) returning id
                """, organization);
        participant(organization, predecessor, "ADMIN", "PREDECESSOR");
        participant(organization, successor, "MEMBER", "SUCCESSOR");
        task = insert("""
                insert into handover_task(project_id, title, created_at, updated_at)
                values (?, 'Task', current_timestamp, current_timestamp) returning id
                """, project);
        Long session = insert("""
                insert into interview_session(project_id, task_id, status, created_by, created_at, updated_at)
                values (?, ?, 'CONFIRMED', ?, current_timestamp, current_timestamp) returning id
                """, project, task, predecessor);
        confirmation = insert("""
                insert into interview_confirmation(session_id, confirmed_by, confirmed_at)
                values (?, ?, current_timestamp) returning id
                """, session, predecessor);
    }

    @Test
    void retriesReturnOriginalVersionAndRegenerationCreatesNewVersion() {
        String jobId = "document-" + UUID.randomUUID();
        Long first = generate(jobId);
        entityManager.flush();
        entityManager.clear();

        Long regenerated = generate("document-" + UUID.randomUUID());
        assertThat(regenerated).isNotEqualTo(first);
        assertThat(generate(jobId)).isEqualTo(first);

        DraftResponse original = documents.getDraft(predecessor, project, task, first);
        DraftResponse next = documents.getDraft(predecessor, project, task, regenerated);
        assertThat(original.documentId()).isEqualTo(next.documentId());
        assertThat(original.sections()).extracting(DraftResponse.Section::title)
                .containsExactly("First", "Second", "Third");
    }

    @Test
    void savesReorderedSectionsAndRejectsStaleRevision() {
        Long id = generate("document-" + UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();
        DraftResponse draft = documents.getDraft(predecessor, project, task, id);
        Long first = draft.sections().get(0).sectionId();
        Long second = draft.sections().get(1).sectionId();
        var request = new UpdateDraftRequest(draft.revision(), "Edited", List.of(
                new UpdateDraftRequest.Section(second, "PROCESS", "Second", "Updated"),
                new UpdateDraftRequest.Section(first, "PROCESS", "First", ""),
                new UpdateDraftRequest.Section(null, "PROCESS", "New", "Body")
        ));

        DraftResponse saved = documents.updateDraft(predecessor, project, task, id, request);
        jdbc.execute("set constraints all immediate");
        entityManager.clear();

        assertThat(saved.revision()).isEqualTo(1);
        assertThat(saved.sections()).extracting(DraftResponse.Section::title)
                .containsExactly("Second", "First", "New");
        assertThat(saved.sections().get(0).sectionId()).isEqualTo(second);
        assertThat(saved.sections().get(1).sectionId()).isEqualTo(first);
        assertThat(saved.sections().get(2).sectionId()).isNotNull();
        assertThat(documents.getDraft(predecessor, project, task, id)).isEqualTo(saved);
        assertError(() -> documents.updateDraft(predecessor, project, task, id, request), ErrorCode.CONFLICT);
    }

    @Test
    void successorCannotReadOrEditDraft() {
        Long id = generate("document-" + UUID.randomUUID());
        assertError(() -> documents.getDraft(successor, project, task, id), ErrorCode.FORBIDDEN);
        assertError(() -> documents.updateDraft(successor, project, task, id,
                new UpdateDraftRequest(0L, "Edited", List.of())), ErrorCode.FORBIDDEN);
    }

    private Long generate(String jobId) {
        return documents.createDraftFromGeneration(task, confirmation, List.of(
                new DraftSectionInput("PROCESS", "First", "One"),
                new DraftSectionInput("PROCESS", "Second", "Two"),
                new DraftSectionInput("PROCESS", "Third", "Three")
        ), jobId);
    }

    private Long user() {
        return insert("""
                insert into app_user(email, password_hash, name, created_at, updated_at)
                values (?, 'test-hash', 'Document test user', current_timestamp, current_timestamp) returning id
                """, UUID.randomUUID() + "@example.test");
    }

    private void participant(Long organization, Long user, String organizationRole, String role) {
        Long member = insert("""
                insert into organization_member(organization_id, user_id, organization_role, status)
                values (?, ?, ?, 'ACTIVE') returning id
                """, organization, user, organizationRole);
        jdbc.update("insert into project_participant(project_id, member_id, role) values (?, ?, ?)",
                project, member, role);
    }

    private Long insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args);
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }
}
