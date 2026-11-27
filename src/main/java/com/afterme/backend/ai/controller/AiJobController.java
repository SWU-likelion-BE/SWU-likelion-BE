package com.afterme.backend.ai.controller;

import com.afterme.backend.ai.job.AiJobRegistry;
import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/handovers/{projectId}/ai-jobs/{jobId}")
public class AiJobController {

    private final AiJobRegistry registry;

    @GetMapping
    public ApiResponse<Object> status(@LoginUserId Long userId, @PathVariable Long projectId,
                                      @PathVariable String jobId) {
        return ApiResponse.of(registry.find(jobId).status(userId, projectId, jobId));
    }

    @PostMapping("/retries")
    public ResponseEntity<ApiResponse<Object>> retry(@LoginUserId Long userId, @PathVariable Long projectId,
                                                     @PathVariable String jobId) {
        return ResponseEntity.accepted().body(ApiResponse.of(registry.find(jobId).retry(userId, projectId, jobId)));
    }
}