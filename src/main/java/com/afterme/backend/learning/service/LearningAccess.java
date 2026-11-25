package com.afterme.backend.learning.service;

import com.afterme.backend.global.exception.*;
import com.afterme.backend.handover.domain.*;
import com.afterme.backend.handover.repository.*;
import com.afterme.backend.organization.repository.OrganizationMemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LearningAccess {
    private final HandoverProjectRepository projects;
    private final ProjectParticipantRepository participants;
    private final OrganizationMemberRepository members;

    public HandoverProject project(Long userId, Long projectId) {
        return projects.findAccessibleById(projectId, userId).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public void role(Long userId, Long projectId, ParticipantRole role) {
        project(userId, projectId);
        if (participants.findParticipantsByProjectId(projectId).stream().noneMatch(p -> p.getRole() == role
                && p.getMember().isActive() && p.getMember().getUser().getId().equals(userId))) throw error(ErrorCode.FORBIDDEN);
    }

    public void participant(Long userId, Long projectId) {
        project(userId, projectId);
        if (participants.findParticipantsByProjectId(projectId).stream().noneMatch(p -> p.getMember().isActive()
                && p.getMember().getUser().getId().equals(userId))) throw error(ErrorCode.FORBIDDEN);
    }

    public void admin(Long userId, Long projectId) {
        HandoverProject project = project(userId, projectId);
        if (members.findByOrganization_IdAndUser_Id(project.getOrganization().getId(), userId)
                .filter(m -> m.isActiveAdmin()).isEmpty()) throw error(ErrorCode.FORBIDDEN);
    }

    public HandoverProject lock(Long projectId) {
        return projects.findByIdForUpdate(projectId).orElseThrow(() -> error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public void mutable(HandoverProject project) {
        if (project.getCompletedAt() != null || "COMPLETED".equals(project.getStatus())) throw error(ErrorCode.CONFLICT);
    }

    private BusinessException error(ErrorCode code) { return new BusinessException(code); }
}
