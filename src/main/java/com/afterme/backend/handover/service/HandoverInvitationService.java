package com.afterme.backend.handover.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.domain.HandoverInvitation;
import com.afterme.backend.handover.domain.HandoverProject;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.domain.ProjectParticipant;
import com.afterme.backend.handover.dto.CreateHandoverInvitationRequest;
import com.afterme.backend.handover.dto.HandoverInvitationPreviewResponse;
import com.afterme.backend.handover.dto.HandoverInvitationResponse;
import com.afterme.backend.handover.dto.JoinHandoverResponse;
import com.afterme.backend.handover.repository.HandoverInvitationRepository;
import com.afterme.backend.handover.repository.HandoverProjectRepository;
import com.afterme.backend.handover.repository.ProjectParticipantRepository;
import com.afterme.backend.organization.domain.Organization;
import com.afterme.backend.organization.domain.OrganizationMember;
import com.afterme.backend.organization.repository.OrganizationMemberRepository;
import com.afterme.backend.user.domain.User;
import com.afterme.backend.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HandoverInvitationService {

    private final HandoverInvitationRepository invitations;
    private final HandoverProjectRepository projects;
    private final ProjectParticipantRepository participants;
    private final OrganizationMemberRepository members;
    private final UserRepository users;

    @Transactional
    public HandoverInvitationResponse create(Long userId, Long projectId, CreateHandoverInvitationRequest request) {
        HandoverProject project = projects.findById(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!participants.existsByProject_IdAndMember_User_IdAndRole(projectId, userId, ParticipantRole.PREDECESSOR)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        HandoverInvitation invitation = invitations.save(new HandoverInvitation(
                project, request.name(), request.email(), request.roleName(), UUID.randomUUID().toString()
        ));
        return new HandoverInvitationResponse(invitation.getCode());
    }

    @Transactional(readOnly = true)
    public HandoverInvitationPreviewResponse preview(Long userId, String code) {
        User user = users.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        HandoverInvitation invitation = invitations.findByCode(code.strip())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        validateInvitation(invitation, user);

        HandoverProject project = invitation.getProject();
        return new HandoverInvitationPreviewResponse(
                project.getOrganization().getId().toString(), project.getId().toString(),
                project.getTitle(), invitation.getInviteRole()
        );
    }

    @Transactional
    public JoinHandoverResponse join(Long userId, String code) {
        // 같은 계정의 동시 참여 요청이 조직 멤버를 중복 생성하지 않도록 잠근다.
        User user = users.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        HandoverInvitation invitation = invitations.findByCodeForUpdate(code.strip())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT));
        validateInvitation(invitation, user);

        HandoverProject project = invitation.getProject();
        Organization organization = project.getOrganization();
        if (participants.existsByProject_IdAndMember_User_IdAndRole(
                project.getId(), userId, ParticipantRole.SUCCESSOR)) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        OrganizationMember member = members.findByOrganization_IdAndUser_Id(organization.getId(), userId)
                .orElseGet(() -> members.save(OrganizationMember.activeMember(organization, user)));
        if (!member.isActive()) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        participants.save(ProjectParticipant.successor(project, member));
        invitation.markUsed(Instant.now());
        return new JoinHandoverResponse(project.getId().toString());
    }

    private void validateInvitation(HandoverInvitation invitation, User user) {
        if (user.isWithdrawn() || !invitation.getInviteEmail().equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        if (invitation.isUsed()) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
    }
}
