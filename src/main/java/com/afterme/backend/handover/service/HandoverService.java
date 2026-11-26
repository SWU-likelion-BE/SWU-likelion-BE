package com.afterme.backend.handover.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.domain.HandoverProject;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handover.domain.ProjectParticipant;
import com.afterme.backend.handover.dto.*;
import com.afterme.backend.handover.repository.HandoverProjectRepository;
import com.afterme.backend.handover.repository.ProjectParticipantRepository;
import com.afterme.backend.organization.domain.Organization;
import com.afterme.backend.organization.domain.OrganizationMember;
import com.afterme.backend.handover.repository.ProjectParticipantRepository.ProjectRole;
import com.afterme.backend.organization.repository.OrganizationMemberRepository;
import com.afterme.backend.organization.repository.OrganizationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
                    .distinct()
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

        //권한 검증
        if(!organizationMember.isActiveAdmin()){
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        //마감일 검증
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        LocalDate deadline = request.deadline();

        if (deadline.isBefore(today) || deadline.isAfter(today.plusDays(31))){
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        //전임자 검증
        if(predecessors.size() != predecessorIds.size() || predecessors.stream()
                .anyMatch(predecessor -> !predecessor.isActive())){
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

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

        List<HandoverProjectResponse.ParticipantResponse> participantResponses =
                predecessors.stream()
                        .map(predecessor ->
                                new HandoverProjectResponse.ParticipantResponse(
                                        predecessor.getId().toString(),
                                        predecessor.getUser().getName(),
                                        ParticipantRole.PREDECESSOR
                                ))
                        .toList();

        List<ParticipantRole> myRoles = predecessorIds.contains(organizationMember.getId())
                ? List.of(ParticipantRole.PREDECESSOR)
                : List.of();

        long dDay = ChronoUnit.DAYS.between(
                today, handoverProject.getDeadLine()
        );

        boolean overdue = dDay < 0;

        return new HandoverProjectResponse(
                handoverProject.getId().toString(),
                handoverProject.getOrganization().getId().toString(),
                handoverProject.getTitle(),
                handoverProject.getRoleName(),
                handoverProject.getDeadLine(),
                handoverProject.getStatus(),
                participantResponses,
                myRoles,
                dDay,
                overdue,
                handoverProject.getCompleteness(),
                handoverProject.getResourceVersion(),
                handoverProject.getCreatedAt(),
                handoverProject.getCompletedAt()
        );
    }

    @Transactional(readOnly = true)
    public HandoverListResponse findAll(
            Long userId,
            Long organizationId,
            String status,
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        Page<HandoverProject> projects =
                handoverProjectRepository.findAccessibleByUserId(
                        userId, organizationId, status, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "id"))
                );

        // 현재 페이지에 있는 프로젝트의 내 역할을 한 번에 조회
        List<Long> projectIds = projects.getContent().stream().map(HandoverProject::getId).toList();
        Map<Long, List<String>> rolesByProjectId = projectIds.isEmpty()
                ? Map.of()
                : projectParticipantRepository.findRolesByProjectIdsAndUserId(projectIds, userId).stream()
                        .collect(Collectors.groupingBy(ProjectRole::getProjectId,
                                Collectors.mapping(role -> role.getRole().name(), Collectors.toList())));
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        List<HandoverSummaryResponse> items = projects.getContent().stream()
                .map(project -> toSummary(project, rolesByProjectId.getOrDefault(project.getId(), List.of()), today))
                .toList();

        return new HandoverListResponse(
                items,
                projects.getNumber(),
                projects.getSize(),
                projects.getTotalElements(),
                projects.getTotalPages()
        );
    }

    private HandoverSummaryResponse toSummary(HandoverProject project, List<String> myRoles, LocalDate today) {
        int dDay = Math.toIntExact(
                ChronoUnit.DAYS.between(today, project.getDeadLine())
        );
        boolean overdue = dDay < 0;

        return new HandoverSummaryResponse(
                project.getId(),
                project.getOrganization().getId(),
                project.getTitle(),
                project.getRoleName(),
                project.getStatus(),
                myRoles,
                project.getDeadLine(),
                dDay,
                overdue,
                project.getCompleteness()
        );
    }

    @Transactional(readOnly = true)
    public HandoverDetailResponse findDetail(Long userId, Long projectId) {
        // 조회 권한이 없는 프로젝트도 404로 처리
        HandoverProject project = handoverProjectRepository.findAccessibleById(projectId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        List<ProjectParticipant> projectParticipants = projectParticipantRepository.findParticipantsByProjectId(projectId);
        List<HandoverDetailResponse.ParticipantResponse> participants = projectParticipants.stream()
                .map(participant -> new HandoverDetailResponse.ParticipantResponse(
                        participant.getMember().getId().toString(),
                        participant.getMember().getUser().getName(),
                        participant.getRole()
                ))
                .toList();
        List<ParticipantRole> myRoles = projectParticipants.stream()
                .filter(participant -> participant.getMember().getUser().getId().equals(userId))
                .map(ProjectParticipant::getRole)
                .distinct()
                .toList();

        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
        long dDay = ChronoUnit.DAYS.between(today, project.getDeadLine());

        return new HandoverDetailResponse(
                project.getId().toString(),
                project.getOrganization().getId().toString(),
                project.getTitle(),
                project.getRoleName(),
                project.getDeadLine(),
                project.getStatus(),
                participants,
                myRoles,
                dDay,
                dDay < 0,
                project.getCompleteness(),
                project.getResourceVersion(),
                project.getCreatedAt(),
                project.getCompletedAt()
        );
    }

    @Transactional
    public HandoverDeadlineExtensionResponse extendDeadline(Long userId, Long projectId, ExtendHandoverDeadlineRequest request){

        //프로젝트 조회
        HandoverProject project = handoverProjectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        //권한 및 상태 확인
        boolean isActiveAdmin = organizationMemberRepository
                .findByOrganization_IdAndUser_Id(project.getOrganization().getId(), userId)
                .map(OrganizationMember::isActiveAdmin)
                .orElse(false);

        if(!isActiveAdmin) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }

        if ("COMPLETED".equals(project.getStatus())
                || !project.getResourceVersion().equals(request.expectedVersion())) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }

        //날짜 검증
        LocalDate newDeadline = request.deadline();
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));

        if(!newDeadline.isAfter(project.getDeadLine()) || newDeadline.isAfter(today.plusDays(31))){
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        project.extendDeadline(newDeadline);
        long dDay = ChronoUnit.DAYS.between(today, project.getDeadLine());

        return new HandoverDeadlineExtensionResponse(
                project.getId().toString(),
                project.getDeadLine(),
                dDay,
                dDay < 0,
                project.getResourceVersion()
        );
    }
}
