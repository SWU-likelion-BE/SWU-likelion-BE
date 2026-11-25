package com.afterme.backend.checklist.controller;

import com.afterme.backend.checklist.service.ChecklistService;
import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/handovers/{projectId}/documents/{documentId}/versions/{versionId}/checklist-items/{itemId}/completion")
public class ChecklistController {
    private final ChecklistService service;
    public record Request(@NotNull Boolean completed) {}
    @PutMapping
    public ApiResponse<ChecklistService.Result> change(@LoginUserId Long userId, @PathVariable Long projectId,
            @PathVariable Long documentId, @PathVariable Long versionId, @PathVariable Long itemId, @Valid @RequestBody Request request) {
        return ApiResponse.of(service.change(userId, projectId, documentId, versionId, itemId, request.completed()));
    }
}
