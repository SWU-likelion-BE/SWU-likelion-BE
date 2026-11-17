package com.afterme.backend.handover.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.handover.domain.HandoverProject;
import com.afterme.backend.handover.dto.CreateHandoverRequest;
import com.afterme.backend.handover.dto.HandoverProjectResponse;
import com.afterme.backend.handover.service.HandoverService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/handovers")
@RequiredArgsConstructor
public class HandoverController {

    private final HandoverService handoverService;

    @PostMapping
    public ResponseEntity<ApiResponse<HandoverProjectResponse>> handover (
            @LoginUserId Long userId,
            @Valid @RequestBody CreateHandoverRequest request
    ){
        HandoverProjectResponse project = handoverService.create(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(project));
    }
}
