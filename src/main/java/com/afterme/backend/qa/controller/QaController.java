package com.afterme.backend.qa.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.qa.domain.QaStatus;
import com.afterme.backend.qa.dto.QaDtos.*;
import com.afterme.backend.qa.service.QaService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/handovers/{projectId}/questions")
@RequiredArgsConstructor
public class QaController {
    private final QaService service;

    @PostMapping
    public ResponseEntity<ApiResponse<Question>> create(@LoginUserId Long userId, @PathVariable Long projectId,
                                                       @Valid @RequestBody CreateQuestion request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.create(userId, projectId, request)));
    }

    @PatchMapping("/{threadId}")
    public ApiResponse<Question> edit(@LoginUserId Long userId, @PathVariable Long projectId,
                                      @PathVariable Long threadId, @Valid @RequestBody EditQuestion request) {
        return ApiResponse.of(service.edit(userId, projectId, threadId, request));
    }

    @GetMapping
    public ApiResponse<QuestionPage> list(@LoginUserId Long userId, @PathVariable Long projectId,
                                          @RequestParam(required = false) QaStatus status,
                                          @RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(service.list(userId, projectId, status, page, size));
    }

    @GetMapping("/{threadId}")
    public ApiResponse<Detail> detail(@LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long threadId) {
        return ApiResponse.of(service.detail(userId, projectId, threadId));
    }

    @PostMapping("/{threadId}/replies")
    public ResponseEntity<ApiResponse<Reply>> reply(@LoginUserId Long userId, @PathVariable Long projectId,
                                                  @PathVariable Long threadId, @Valid @RequestBody CreateReply request) {
        return ResponseEntity.status(201).body(ApiResponse.of(service.reply(userId, projectId, threadId, request)));
    }
}
