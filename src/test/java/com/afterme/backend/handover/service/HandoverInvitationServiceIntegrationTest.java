package com.afterme.backend.handover.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.dto.CreateHandoverInvitationRequest;
import com.afterme.backend.handover.dto.CreateHandoverRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Transactional
class HandoverInvitationServiceIntegrationTest {

    @Autowired HandoverInvitationService invitations;
    @Autowired HandoverService handovers;
    @Autowired JdbcTemplate jdbc;

    private Long predecessorId;
    private Long successorId;
    private Long organizationId;
    private Long projectId;
    private CreateHandoverInvitationRequest request;

    @BeforeEach
    void setUp() {
        predecessorId = createUser(UUID.randomUUID() + "@example.test");
        String successorEmail = UUID.randomUUID() + "@example.test";
        successorId = createUser(successorEmail);
        request = new CreateHandoverInvitationRequest("Successor", successorEmail, "Treasurer");

        organizationId = jdbc.queryForObject(
                "INSERT INTO organization (owner_id) VALUES (?) RETURNING id", Long.class, predecessorId);
        Long memberId = jdbc.queryForObject("""
                INSERT INTO organization_member (organization_id, user_id, organization_role, status)
                VALUES (?, ?, 'ADMIN', 'ACTIVE') RETURNING id
                """, Long.class, organizationId, predecessorId);
        var project = handovers.create(predecessorId, new CreateHandoverRequest(
                organizationId.toString(), "Test handover", "Treasurer",
                LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1), List.of(memberId.toString())));
        projectId = Long.valueOf(project.id());
    }

    @Test
    void previewsWithoutJoiningAndConsumesCodeOnJoin() {
        String code = invitations.create(predecessorId, projectId, request).code();

        assertThat(invitations.preview(successorId, code).organizationId()).isEqualTo(organizationId.toString());
        assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM organization_member WHERE organization_id = ? AND user_id = ?",
                Long.class, organizationId, successorId)).isZero();

        assertThat(invitations.join(successorId, code).projectId()).isEqualTo(projectId.toString());
        assertThat(handovers.findDetail(successorId, projectId)).isNotNull();
        assertError(() -> invitations.join(successorId, code), ErrorCode.CONFLICT);
    }

    @Test
    void rejectsUnauthorizedIssuanceAndInvalidParticipation() {
        assertError(() -> invitations.create(successorId, projectId, request), ErrorCode.FORBIDDEN);
        assertError(() -> invitations.preview(successorId, "invalid-code"), ErrorCode.INVALID_INPUT);
        assertError(() -> invitations.join(successorId, "invalid-code"), ErrorCode.INVALID_INPUT);

        String code = invitations.create(predecessorId, projectId, request).code();
        assertError(() -> invitations.preview(predecessorId, code), ErrorCode.FORBIDDEN);
        assertError(() -> invitations.join(predecessorId, code), ErrorCode.FORBIDDEN);
        assertThat(invitations.join(successorId, code).projectId()).isEqualTo(projectId.toString());
    }

    private void assertError(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(
                BusinessException.class, exception -> assertThat(exception.getErrorCode()).isEqualTo(code));
    }

    private Long createUser(String email) {
        return jdbc.queryForObject("""
                INSERT INTO app_user (email, password_hash, name, created_at, updated_at)
                VALUES (?, 'test-hash', 'Test user', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                RETURNING id
                """, Long.class, email);
    }
}
