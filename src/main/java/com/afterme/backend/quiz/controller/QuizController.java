package com.afterme.backend.quiz.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.quiz.dto.QuizDtos.*;
import com.afterme.backend.quiz.service.QuizService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/handovers/{projectId}/quizzes")
@RequiredArgsConstructor
public class QuizController {
    private final QuizService service;

    @GetMapping
    public ApiResponse<QuizPage> list(@LoginUserId Long userId, @PathVariable Long projectId,
                                    @RequestParam(defaultValue = "0") int page,
                                    @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(service.list(userId, projectId, page, size));
    }

    @PostMapping("/{quizSetId}/attempts")
    public ResponseEntity<ApiResponse<AttemptStarted>> start(@LoginUserId Long userId, @PathVariable Long projectId,
                                                            @PathVariable Long quizSetId) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.start(userId, projectId, quizSetId)));
    }

    @GetMapping("/{quizSetId}/attempts/{attemptId}")
    public ApiResponse<AttemptQuestions> questions(@LoginUserId Long userId, @PathVariable Long projectId,
                                                  @PathVariable Long quizSetId, @PathVariable Long attemptId) {
        return ApiResponse.of(service.questions(userId, projectId, quizSetId, attemptId));
    }

    @PostMapping("/{quizSetId}/attempts/{attemptId}/submission")
    public ApiResponse<Result> submit(@LoginUserId Long userId, @PathVariable Long projectId,
                                      @PathVariable Long quizSetId, @PathVariable Long attemptId,
                                      @Valid @RequestBody Submission request) {
        return ApiResponse.of(service.submit(userId, projectId, quizSetId, attemptId, request));
    }

    @GetMapping("/{quizSetId}/attempts/{attemptId}/result")
    public ApiResponse<Result> result(@LoginUserId Long userId, @PathVariable Long projectId,
                                      @PathVariable Long quizSetId, @PathVariable Long attemptId) {
        return ApiResponse.of(service.result(userId, projectId, quizSetId, attemptId));
    }
}
