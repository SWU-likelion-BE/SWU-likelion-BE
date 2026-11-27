package com.afterme.backend.handovertask.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.handover.domain.HandoverProject;
import com.afterme.backend.handover.domain.ParticipantRole;
import com.afterme.backend.handovertask.domain.HandoverTask;
import com.afterme.backend.handovertask.dto.CreateHandoverTaskRequest;
import com.afterme.backend.handovertask.dto.HandoverTaskResponse;
import com.afterme.backend.handovertask.dto.UpdateHandoverTaskRequest;
import com.afterme.backend.handovertask.repository.HandoverTaskRepository;
import com.afterme.backend.learning.service.LearningAccess;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class HandoverTaskServiceTest {
    private static final long USER_ID = 1L;
    private static final long PROJECT_ID = 2L;
    private static final long TASK_ID = 3L;
    private final Clock clock = Clock.fixed(Instant.parse("2026-11-27T00:00:00Z"), ZoneOffset.UTC);
    private final HandoverTaskRepository repository = mock(HandoverTaskRepository.class);
    private final LearningAccess access = mock(LearningAccess.class);
    private final HandoverTaskService service = new HandoverTaskService(repository, clock, access);

    @Test
    void createsTaskWithTrimmedTitleAndTimestamps() {
        when(repository.save(any(HandoverTask.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(USER_ID, PROJECT_ID, new CreateHandoverTaskRequest("  Report  "));

        assertThat(response.projectId()).isEqualTo(PROJECT_ID);
        assertThat(response.title()).isEqualTo("Report");
        assertThat(response.createdAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(response.updatedAt()).isEqualTo(response.createdAt());
        verify(access).role(USER_ID, PROJECT_ID, ParticipantRole.PREDECESSOR);
    }

    @Test
    void listsOnlyTasksReturnedForTheRequestedProject() {
        when(repository.findAllByProjectIdOrderByIdAsc(PROJECT_ID))
                .thenReturn(List.of(task("First"), task("Second")));

        assertThat(service.list(USER_ID, PROJECT_ID))
                .extracting(HandoverTaskResponse::title)
                .containsExactly("First", "Second");
        verify(access).project(USER_ID, PROJECT_ID);
    }

    @Test
    void returnsNotFoundWhenTaskDoesNotBelongToProject() {
        when(repository.findByIdAndProjectId(TASK_ID, PROJECT_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getDetail(USER_ID, PROJECT_ID, TASK_ID))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.RESOURCE_NOT_FOUND));
        verify(access).project(USER_ID, PROJECT_ID);
    }

    @Test
    void updatesTitleAndTimestamp() {
        HandoverTask task = task("Old");
        when(repository.findByIdAndProjectId(TASK_ID, PROJECT_ID)).thenReturn(Optional.of(task));

        var response = service.update(USER_ID, PROJECT_ID, TASK_ID, new UpdateHandoverTaskRequest("  New  "));

        assertThat(task.getTitle()).isEqualTo("New");
        assertThat(response.updatedAt()).isEqualTo(OffsetDateTime.now(clock));
        assertThat(response.createdAt()).isEqualTo(OffsetDateTime.now(clock).minusDays(1));
    }

    @Test
    void rejectsUpdatesWhenProjectIsImmutable() {
        HandoverProject project = mock(HandoverProject.class);
        when(access.lock(PROJECT_ID)).thenReturn(project);
        doThrow(new BusinessException(ErrorCode.CONFLICT)).when(access).mutable(project);

        assertThatThrownBy(() -> service.update(USER_ID, PROJECT_ID, TASK_ID, new UpdateHandoverTaskRequest("New")))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.CONFLICT));
        verifyNoInteractions(repository);
    }

    private HandoverTask task(String title) {
        return new HandoverTask(PROJECT_ID, title, OffsetDateTime.now(clock).minusDays(1));
    }
}
