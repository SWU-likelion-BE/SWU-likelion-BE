package com.afterme.backend.quiz.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.quiz.service.QuizGenerationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class QuizGenerationController {
    private final QuizGenerationService service;
    @PostMapping("/api/handovers/{projectId}/quiz-generation-jobs")
    public ResponseEntity<ApiResponse<QuizGenerationService.Job>> request(@LoginUserId Long userId, @PathVariable Long projectId,
                                                                        @Valid @RequestBody QuizGenerationService.Request request) {
        return ResponseEntity.accepted().body(ApiResponse.of(service.request(userId, projectId, request)));
    }
}
