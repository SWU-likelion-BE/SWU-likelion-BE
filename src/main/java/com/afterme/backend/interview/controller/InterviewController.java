package com.afterme.backend.interview.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.interview.dto.InterviewDtos.*;
import com.afterme.backend.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/handovers/{projectId}")
public class InterviewController {

    private final InterviewService service;

    @PostMapping("/tasks/{taskId}/interviews")
    public ResponseEntity<ApiResponse<SessionDetail>> start(@LoginUserId Long userId, @PathVariable Long projectId,
                                                            @PathVariable Long taskId) {
        var started = service.start(userId, projectId, taskId);
        HttpStatus status = started.created() ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(ApiResponse.of(started.session()));
    }

    @GetMapping("/interviews/{sessionId}")
    public ApiResponse<SessionDetail> get(@LoginUserId Long userId, @PathVariable Long projectId,
                                          @PathVariable Long sessionId) {
        return ApiResponse.of(service.get(userId, projectId, sessionId));
    }

    @PutMapping("/interviews/{sessionId}/questions/{questionId}/answer")
    public ApiResponse<SavedAnswer> saveAnswer(@LoginUserId Long userId, @PathVariable Long projectId,
                                               @PathVariable Long sessionId, @PathVariable Long questionId,
                                               @Valid @RequestBody SaveAnswer request) {
        return ApiResponse.of(service.saveAnswer(userId, projectId, sessionId, questionId, request));
    }

    @GetMapping("/interviews/{sessionId}/answers/{answerId}/revisions")
    public ApiResponse<List<RevisionView>> revisions(@LoginUserId Long userId, @PathVariable Long projectId,
                                                     @PathVariable Long sessionId, @PathVariable Long answerId) {
        return ApiResponse.of(service.revisions(userId, projectId, sessionId, answerId));
    }

    @PostMapping("/interviews/{sessionId}/confirmations")
    public ResponseEntity<ApiResponse<Confirmation>> confirm(@LoginUserId Long userId, @PathVariable Long projectId,
                                                             @PathVariable Long sessionId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(service.confirm(userId, projectId, sessionId)));
    }
}