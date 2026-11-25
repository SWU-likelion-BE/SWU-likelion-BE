package com.afterme.backend.completion.controller;

import com.afterme.backend.completion.dto.CompletionDtos.*;
import com.afterme.backend.completion.service.CompletionService;
import com.afterme.backend.completion.service.CompletionWorkflowService;
import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/handovers/{projectId}/completion")
@RequiredArgsConstructor
public class CompletionController {
    private final CompletionService service;
    private final CompletionWorkflowService workflow;

    @PatchMapping("/criteria")
    public ApiResponse<Criteria> updateCriteria(@LoginUserId Long userId, @PathVariable Long projectId,
                                               @Valid @RequestBody UpdateCriteria request) {
        return ApiResponse.of(service.updateCriteria(userId, projectId, request));
    }

    @PutMapping("/confirmations/me")
    public ApiResponse<Confirmation> confirm(@LoginUserId Long userId, @PathVariable Long projectId,
                                           @Valid @RequestBody Confirm request) {
        return ApiResponse.of(service.confirm(userId, projectId, request));
    }

    @GetMapping
    public ApiResponse<CompletionWorkflowService.Details> state(@LoginUserId Long userId, @PathVariable Long projectId) {
        return ApiResponse.of(workflow.state(userId, projectId));
    }

    @PostMapping
    public ApiResponse<CompletionWorkflowService.Approval> request(@LoginUserId Long userId, @PathVariable Long projectId) {
        return ApiResponse.of(workflow.request(userId, projectId));
    }

    @PostMapping("/approval")
    public ApiResponse<CompletionWorkflowService.Approval> approve(@LoginUserId Long userId, @PathVariable Long projectId) {
        return ApiResponse.of(workflow.approve(userId, projectId));
    }
}
