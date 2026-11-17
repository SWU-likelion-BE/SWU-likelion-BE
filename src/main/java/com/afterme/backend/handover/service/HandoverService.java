package com.afterme.backend.handover.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.domain.HandoverProject;
import com.afterme.backend.handover.domain.ProjectParticipant;
import com.afterme.backend.handover.dto.CreateHandoverRequest;
import com.afterme.backend.handover.dto.HandoverProjectResponse;
import com.afterme.backend.handover.repository.HandoverProjectRepository;
import com.afterme.backend.handover.repository.ProjectParticipantRepository;
import com.afterme.backend.organization.domain.Organization;
import com.afterme.backend.organization.domain.OrganizationMember;
import com.afterme.backend.organization.repository.OrganizationMemberRepository;
import com.afterme.backend.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.util.List;

@Service
@RequiredArgsConstructor
public class HandoverService {

    private final HandoverProjectRepository handoverProjectRepository;
    private final ProjectParticipantRepository projectParticipantRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;

    @Transactional
    public HandoverProjectResponse create(Long userId, CreateHandoverRequest request){

        Long organizationId;
        List<Long> predecessorIds;

        try{
            organizationId = Long.valueOf(request.organizationId());
            predecessorIds = request.predecessorMemberIds().stream()
                    .map(Long::valueOf)
                    .toList();
        } catch (NumberFormatException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        List<OrganizationMember> predecessors = organizationMemberRepository
                .findAllByOrganization_IdAndIdIn(organizationId, predecessorIds);

        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        OrganizationMember organizationMember = organizationMemberRepository.findByOrganization_IdAndUser_Id(organizationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN));

        HandoverProject newProject = new HandoverProject(
                organization,
                request.title(),
                request.roleName(),
                request.deadline()
        );

        HandoverProject handoverProject = handoverProjectRepository.save(newProject);

        List<ProjectParticipant> participants = predecessors.stream()
                .map(member -> ProjectParticipant.predecessor(handoverProject, member))
                .toList();

        projectParticipantRepository.saveAll(participants);

        return new HandoverProjectResponse(handoverProject.getId().toString());
    }
}
