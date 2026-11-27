package com.afterme.backend.handovertask.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handovertask.domain.HandoverTask;
import com.afterme.backend.handovertask.dto.CreateHandoverTaskRequest;
import com.afterme.backend.handovertask.dto.HandoverTaskDetailResponse;
import com.afterme.backend.handovertask.dto.HandoverTaskResponse;
import com.afterme.backend.handovertask.dto.UpdateHandoverTaskRequest;
import com.afterme.backend.handovertask.repository.HandoverTaskRepository;
import com.afterme.backend.learning.service.LearningAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static com.afterme.backend.handover.domain.ParticipantRole.PREDECESSOR;

@Service
@RequiredArgsConstructor
public class HandoverTaskService {

    private final HandoverTaskRepository handoverTaskRepository;
    private final Clock clock;
    private final LearningAccess access;

    @Transactional
    public HandoverTaskResponse create(
            Long userId,
            Long projectId,
            CreateHandoverTaskRequest request
    ){
        access.role(userId, projectId, ParticipantRole.PREDECESSOR);
        access.mutable(access.lock(projectId));

        OffsetDateTime now = OffsetDateTime.now(clock);
        HandoverTask task = handoverTaskRepository.save(
                new HandoverTask(projectId, request.title().strip(), now)
        );

        return toResponse(task);
    }

    @Transactional(readOnly = true)
    public List<HandoverTaskResponse> list(Long userId, Long projectId) {
        access.project(userId, projectId);
        return handoverTaskRepository.findAllByProjectIdOrderByIdAsc(projectId).stream()
                .map(this::toResponse)
                .toList();
    }

    private HandoverTaskResponse toResponse(HandoverTask task) {
        return new HandoverTaskResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    @Transactional(readOnly = true)
    public HandoverTaskDetailResponse getDetail(Long userId, Long projectId, Long taskId){
        access.project(userId, projectId);

        HandoverTask task = handoverTaskRepository.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        return toDetailResponse(task);
    }

    private HandoverTaskDetailResponse toDetailResponse(HandoverTask task) {
        return new HandoverTaskDetailResponse(
                task.getId(),
                task.getProjectId(),
                task.getTitle(),
                task.getOverview(),
                task.getDetails(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    @Transactional
    public HandoverTaskDetailResponse update(
            Long userId,
            Long projectId,
            Long taskId,
            UpdateHandoverTaskRequest request
    ){
        access.role(userId, projectId, PREDECESSOR);
        access.mutable(access.lock(projectId));

        HandoverTask task = handoverTaskRepository.findByIdAndProjectId(taskId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now(clock);

        if(request.hasTitle()){
            task.rename(request.title().strip(), now);
        }

        if(request.hasOverview()){
            task.updateOverview(request.overview(), now);
        }

        if(request.hasDetails()){
            task.updateDetails(request.details(), now);
        }

        return toDetailResponse(task);
    }
}
