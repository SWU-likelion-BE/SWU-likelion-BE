package com.afterme.backend.handoverdocument.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.handoverdocument.dto.DraftResponse;
import com.afterme.backend.handoverdocument.dto.PublicationRequest;
import com.afterme.backend.handoverdocument.dto.PublicationResponse;
import com.afterme.backend.handoverdocument.dto.UpdateDraftRequest;
import com.afterme.backend.handoverdocument.service.HandoverDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/handovers/{projectId}")
@RequiredArgsConstructor
public class HandoverDocumentController {
    private final HandoverDocumentService service;

    @GetMapping("/tasks/{taskId}/documents/versions/{versionId}/draft")
    public ApiResponse<DraftResponse> getDraft(@LoginUserId Long userId, @PathVariable Long projectId,
                                               @PathVariable Long taskId, @PathVariable Long versionId) {
        return ApiResponse.of(service.getDraft(userId, projectId, taskId, versionId));
    }

    @PutMapping("/tasks/{taskId}/documents/versions/{versionId}/draft")
    public ApiResponse<DraftResponse> updateDraft(@LoginUserId Long userId, @PathVariable Long projectId,
                                                  @PathVariable Long taskId, @PathVariable Long versionId,
                                                  @Valid @RequestBody UpdateDraftRequest request) {
        return ApiResponse.of(service.updateDraft(userId, projectId, taskId, versionId, request));
    }

    @PostMapping("/documents/{documentId}/versions/{versionId}/publication")
    public ApiResponse<PublicationResponse> publish(@LoginUserId Long userId, @PathVariable Long projectId,
                                                    @PathVariable Long documentId, @PathVariable Long versionId,
                                                    @Valid @RequestBody PublicationRequest request) {
        return ApiResponse.of(service.publish(userId, projectId, documentId, versionId, request.expectedVersion()));
    }
}
