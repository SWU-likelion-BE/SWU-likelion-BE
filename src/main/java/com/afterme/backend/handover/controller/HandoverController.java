package com.afterme.backend.handover.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.handover.dto.CreateHandoverInvitationRequest;
import com.afterme.backend.handover.dto.CreateHandoverRequest;
import com.afterme.backend.handover.dto.HandoverInvitationResponse;
import com.afterme.backend.handover.service.HandoverInvitationService;
import com.afterme.backend.handover.dto.HandoverDetailResponse;
import com.afterme.backend.handover.dto.HandoverListResponse;
import com.afterme.backend.handover.dto.HandoverProjectResponse;
import com.afterme.backend.handover.service.HandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/handovers")
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService handoverService;
    private final HandoverInvitationService invitationService;

    @PostMapping("/{projectId}/invitations")
    public ResponseEntity<ApiResponse<HandoverInvitationResponse>> createInvitation(
            @LoginUserId Long userId,
            @PathVariable("projectId") Long projectId,
            @Valid @RequestBody CreateHandoverInvitationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(invitationService.create(userId, projectId, request)));
    }

    //인수인계 생성
    @PostMapping
    public ResponseEntity<ApiResponse<HandoverProjectResponse>> handover (
            @LoginUserId Long userId,
            @Valid @RequestBody CreateHandoverRequest request
    ){
        HandoverProjectResponse project = handoverService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(project));
    }


    //인수인계 목록 조회
    @GetMapping
    public ResponseEntity<ApiResponse<HandoverListResponse>> getHandover (
            @LoginUserId Long userId,
            @RequestParam(required = false) Long organizationId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ){
        HandoverListResponse response = handoverService.findAll(userId, organizationId, status, page, size);
        return ResponseEntity.ok(ApiResponse.of(response));
    }

    //인수인계 상세 조회
    @GetMapping("/{projectId}")
    public ResponseEntity<ApiResponse<HandoverDetailResponse>> getHandoverDetail(
            @LoginUserId Long userId,
            @PathVariable("projectId") Long projectId
    ){
        HandoverDetailResponse response = handoverService.findDetail(userId, projectId);
        return ResponseEntity.ok(ApiResponse.of(response));
    }
}
