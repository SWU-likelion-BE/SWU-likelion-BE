package com.afterme.backend.handovertask.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.handover.dto.HandoverProjectResponse;
import com.afterme.backend.handovertask.domain.HandoverTask;
import com.afterme.backend.handovertask.dto.CreateHandoverTaskRequest;
import com.afterme.backend.handovertask.dto.HandoverTaskResponse;
import com.afterme.backend.handovertask.dto.UpdateHandoverTaskRequest;
import com.afterme.backend.handovertask.service.HandoverTaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/handovers/{projectId}/tasks")
@RequiredArgsConstructor
public class HandoverTaskController {

    private final HandoverTaskService handoverTaskService;

    //업무 생성
    @PostMapping
    public ResponseEntity<ApiResponse<HandoverTaskResponse>> handoverTask (
            @LoginUserId Long userId,
            @PathVariable Long projectId,
            @Valid @RequestBody CreateHandoverTaskRequest request
    ){

        HandoverTaskResponse task = handoverTaskService.create(userId, projectId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(task));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<HandoverTaskResponse>>> getHandoverTask(
            @LoginUserId Long userId,
            @PathVariable Long projectId
    ){
        return ResponseEntity.ok(
                ApiResponse.of(handoverTaskService.list(userId, projectId))
        );
    }

    @GetMapping("/{taskId}")
    public ResponseEntity<ApiResponse<HandoverTaskResponse>> getHandoverTaskDetail(
            @LoginUserId Long userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId
    ){
        HandoverTaskResponse task = handoverTaskService.getDetail(userId, projectId, taskId);
        return ResponseEntity.ok(ApiResponse.of(task));
    }

    @PatchMapping("/{taskId}")
    public ResponseEntity<ApiResponse<HandoverTaskResponse>> updateHandoverTask(
            @LoginUserId Long userId,
            @PathVariable Long projectId,
            @PathVariable Long taskId,
            @Valid @RequestBody UpdateHandoverTaskRequest request
    ){
        HandoverTaskResponse task = handoverTaskService.update(userId, projectId, taskId, request);
        return ResponseEntity.ok(ApiResponse.of(task));
    }
}
