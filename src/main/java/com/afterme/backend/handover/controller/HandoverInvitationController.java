package com.afterme.backend.handover.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.handover.dto.HandoverInvitationCodeRequest;
import com.afterme.backend.handover.dto.HandoverInvitationPreviewResponse;
import com.afterme.backend.handover.dto.JoinHandoverResponse;
import com.afterme.backend.handover.service.HandoverInvitationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/handovers-invitations")
@RequiredArgsConstructor
public class HandoverInvitationController {

    private final HandoverInvitationService invitationService;

    @PostMapping("/verify")
    public ResponseEntity<ApiResponse<HandoverInvitationPreviewResponse>> previewInvitation(
            @LoginUserId Long userId,
            @Valid @RequestBody HandoverInvitationCodeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.preview(userId, request.code())));
    }

    @PostMapping("/join")
    public ResponseEntity<ApiResponse<JoinHandoverResponse>> joinInvitation(
            @LoginUserId Long userId,
            @Valid @RequestBody HandoverInvitationCodeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.of(invitationService.join(userId, request.code())));
    }
}
