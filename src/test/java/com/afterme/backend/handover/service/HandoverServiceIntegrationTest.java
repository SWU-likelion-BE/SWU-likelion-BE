package com.afterme.backend.handover.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.dto.CreateHandoverRequest;
import com.afterme.backend.handover.dto.ExtendHandoverDeadlineRequest;
import com.afterme.backend.handover.repository.ProjectParticipantRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
@Transactional
class HandoverServiceIntegrationTest {
    @Autowired HandoverService service;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;
    @Autowired PlatformTransactionManager transactionManager;
    @MockitoSpyBean ProjectParticipantRepository participantRepository;

    Long userId;
    Long organizationId;
    Long memberId;
    LocalDate today;

    @BeforeEach
    void setUp() {
        today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        userId = jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, name, created_at, updated_at)
                VALUES (?, 'test-hash', 'Test admin', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, UUID.randomUUID() + "@example.test");
        organizationId = jdbc.queryForObject(
                "INSERT INTO organization (owner_id) VALUES (?) RETURNING id", Long.class, userId);
        memberId = jdbc.queryForObject("""
                INSERT INTO organization_member (organization_id, user_id, organization_role, status)
                VALUES (?, ?, 'ADMIN', 'ACTIVE') RETURNING id
                """, Long.class, organizationId, userId);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 31})
    void createsProjectAtAllowedDeadlineBoundaries(int days) {
        var response = service.create(userId, request(days, List.of(memberId.toString())));

        assertThat(response.organizationId()).isEqualTo(organizationId.toString());
        assertThat(response.title()).isEqualTo("Test handover");
        assertThat(response.roleName()).isEqualTo("Test role");
        assertThat(response.deadline()).isEqualTo(today.plusDays(days));
        assertThat(response.dDay()).isEqualTo(days);
        assertThat(response.overdue()).isFalse();
        assertThat(response.status()).isEqualTo("DRAFT");
        assertThat(response.completeness()).isZero();
        assertThat(response.resourceVersion()).isZero();
        assertThat(response.createdAt()).isNotNull();
        assertThat(response.completedAt()).isNull();
        assertThat(response.myRoles()).containsExactly(ParticipantRole.PREDECESSOR);
        assertThat(response.participants()).singleElement().satisfies(participant -> {
            assertThat(participant.memberId()).isEqualTo(memberId.toString());
            assertThat(participant.name()).isEqualTo("Test admin");
            assertThat(participant.role()).isEqualTo(ParticipantRole.PREDECESSOR);
        });
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM project_participant WHERE project_id = ?",
                Long.class, Long.valueOf(response.id()))).isEqualTo(1L);
    }

    @Test
    void listsAccessibleProjectsWithFiltersAndPageMetadata() {
        service.create(userId, request(1, List.of(memberId.toString())));
        var latest = service.create(userId, request(2, List.of(memberId.toString())));

        var response = service.findAll(userId, organizationId, "DRAFT", 0, 1);

        assertThat(response.totalElements()).isEqualTo(2);
        assertThat(response.totalPages()).isEqualTo(2);
        assertThat(response.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(Long.valueOf(latest.id()));
            assertThat(item.myRoles()).containsExactly("PREDECESSOR");
            assertThat(item.dDay()).isEqualTo(2);
        });
        assertThat(service.findAll(userId, organizationId, "COMPLETED", 0, 20).items()).isEmpty();
        assertThat(service.findAll(userId, -1L, null, 0, 20).items()).isEmpty();
        assertThat(service.findAll(-1L, null, null, 0, 20).items()).isEmpty();
    }

    @Test
    void returnsProjectDetailsOnlyToAuthorizedUsers() {
        var created = service.create(userId, new CreateHandoverRequest(organizationId.toString(), "Test handover",
                "Test role", today.plusDays(1), List.of(memberId.toString()), "LINKER", "26기 홍보팀"));
        assertThat(created.organizationName()).isEqualTo("LINKER");
        assertThat(created.affiliationName()).isEqualTo("26기 홍보팀");
        Long projectId = Long.valueOf(created.id());

        var response = service.findDetail(userId, projectId);

        assertThat(response.id()).isEqualTo(created.id());
        assertThat(response.deadline()).isEqualTo(created.deadline());
        assertThat(response.organizationName()).isEqualTo(created.organizationName());
        assertThat(response.affiliationName()).isEqualTo(created.affiliationName());
        assertThat(response.myRoles()).containsExactly(ParticipantRole.PREDECESSOR);
        assertThat(response.participants()).singleElement()
                .satisfies(participant -> assertThat(participant.memberId()).isEqualTo(memberId.toString()));
        assertThatThrownBy(() -> service.findDetail(-1L, projectId))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 32})
    void rejectsDeadlineOutsideAllowedRange(int days) {
        assertRejected(userId, request(days, List.of(memberId.toString())), ErrorCode.INVALID_INPUT);
    }

    @Test
    void rejectsNonAdmin() {
        jdbc.update("UPDATE organization_member SET organization_role = 'MEMBER' WHERE id = ?", memberId);
        assertRejected(userId, request(1, List.of(memberId.toString())), ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsUserWithoutMembership() {
        assertRejected(-1L, request(1, List.of(memberId.toString())), ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsMissingPredecessor() {
        assertRejected(userId, request(1, List.of(memberId.toString(), "-1")), ErrorCode.INVALID_INPUT);
    }

    @Test
    void rejectsPredecessorFromAnotherOrganization() {
        Long otherOrganizationId = jdbc.queryForObject(
                "INSERT INTO organization (owner_id) VALUES (?) RETURNING id", Long.class, userId);
        Long otherMemberId = jdbc.queryForObject("""
                INSERT INTO organization_member (organization_id, user_id, organization_role, status)
                VALUES (?, ?, 'MEMBER', 'ACTIVE') RETURNING id
                """, Long.class, otherOrganizationId, userId);
        assertRejected(userId, request(1, List.of(otherMemberId.toString())), ErrorCode.INVALID_INPUT);
    }

    @Test
    void assignsDuplicatePredecessorOnlyOnce() {
        var response = service.create(userId,
                request(1, List.of(memberId.toString(), memberId.toString())));
        assertThat(response.participants()).hasSize(1);
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM project_participant WHERE project_id = ?",
                Long.class, Long.valueOf(response.id()))).isEqualTo(1L);
    }

    @Test
    void rollsBackProjectWhenParticipantSaveFails() {
        var transaction = new TransactionTemplate(transactionManager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        // Commit isolated fixtures so the service owns its write transaction.
        transaction.executeWithoutResult(status -> setUp());
        try {
            doThrow(new IllegalStateException("Simulated participant write failure"))
                    .when(participantRepository).saveAll(any());
            var withoutTransaction = new TransactionTemplate(transactionManager);
            withoutTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_NOT_SUPPORTED);
            assertThatThrownBy(() -> withoutTransaction.executeWithoutResult(status ->
                    service.create(userId, request(1, List.of(memberId.toString())))))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Simulated participant write failure");
            assertThat(jdbc.queryForObject(
                    "SELECT count(*) FROM handover_project WHERE organization_id = ?",
                    Long.class, organizationId)).isZero();
        } finally {
            transaction.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM organization_member WHERE organization_id = ?", organizationId);
                jdbc.update("DELETE FROM organization WHERE id = ?", organizationId);
                jdbc.update("DELETE FROM app_user WHERE id = ?", userId);
            });
        }
    }

    @Test
    void extendsDeadlineAtUpperBoundAndRejectsStaleVersion() {
        Long projectId = createProjectForExtension();
        LocalDate deadline = today.plusDays(31);

        var response = service.extendDeadline(userId, projectId,
                new ExtendHandoverDeadlineRequest(deadline, "More time needed", 0L));

        assertThat(response.id()).isEqualTo(projectId.toString());
        assertThat(response.deadline()).isEqualTo(deadline);
        assertThat(response.dDay()).isEqualTo(31);
        assertThat(response.overdue()).isFalse();
        assertThat(response.resourceVersion()).isEqualTo(1L);

        entityManager.flush();
        entityManager.clear();
        var detail = service.findDetail(userId, projectId);
        assertThat(detail.deadline()).isEqualTo(deadline);
        assertThat(detail.resourceVersion()).isEqualTo(1L);
        assertExtensionRejected(userId, projectId, deadline, 0L, ErrorCode.CONFLICT);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 32})
    void rejectsInvalidExtendedDeadline(int days) {
        assertExtensionRejected(userId, createProjectForExtension(), today.plusDays(days),
                0L, ErrorCode.INVALID_INPUT);
    }

    @Test
    void rejectsDeadlineExtensionByNonAdmin() {
        Long projectId = createProjectForExtension();
        jdbc.update("UPDATE organization_member SET organization_role = 'MEMBER' WHERE id = ?", memberId);
        entityManager.clear();

        assertExtensionRejected(userId, projectId, today.plusDays(2), 0L, ErrorCode.FORBIDDEN);
    }

    @Test
    void rejectsDeadlineExtensionForCompletedProject() {
        Long projectId = createProjectForExtension();
        jdbc.update("UPDATE handover_project SET status = 'COMPLETED' WHERE id = ?", projectId);
        entityManager.clear();

        assertExtensionRejected(userId, projectId, today.plusDays(2), 0L, ErrorCode.CONFLICT);
    }

    private Long createProjectForExtension() {
        return Long.valueOf(service.create(userId, request(1, List.of(memberId.toString()))).id());
    }

    private void assertExtensionRejected(Long actorId, Long projectId, LocalDate deadline, Long version,
                                         ErrorCode code) {
        assertThatThrownBy(() -> service.extendDeadline(actorId, projectId,
                new ExtendHandoverDeadlineRequest(deadline, "More time needed", version)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    private CreateHandoverRequest request(int days, List<String> predecessors) {
        return new CreateHandoverRequest(organizationId.toString(), "Test handover",
                "Test role", today.plusDays(days), predecessors);
    }

    private void assertRejected(Long actorId, CreateHandoverRequest request, ErrorCode code) {
        assertThatThrownBy(() -> service.create(actorId, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM handover_project WHERE organization_id = ?",
                Long.class, organizationId)).isZero();
    }
}
