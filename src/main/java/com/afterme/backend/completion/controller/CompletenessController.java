package com.afterme.backend.completion.controller;

import com.afterme.backend.completion.service.ReadinessService;
import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class CompletenessController {
    private final ReadinessService service;
    @GetMapping("/api/handovers/{projectId}/completeness")
    public ApiResponse<ReadinessService.Evaluation> get(@LoginUserId Long userId, @PathVariable Long projectId) {
        return ApiResponse.of(service.get(userId, projectId));
    }
}
