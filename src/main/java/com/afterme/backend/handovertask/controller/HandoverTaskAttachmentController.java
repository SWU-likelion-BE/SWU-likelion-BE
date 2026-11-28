package com.afterme.backend.handovertask.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.handovertask.domain.HandoverTaskAttachment;
import com.afterme.backend.handovertask.dto.HandoverTaskAttachmentResponse;
import com.afterme.backend.handovertask.service.HandoverTaskAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/handovers/{projectId}/tasks/{taskId}/attachments")
@RequiredArgsConstructor
public class HandoverTaskAttachmentController {

    private final HandoverTaskAttachmentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<HandoverTaskAttachmentResponse>> upload(
            @LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long taskId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.upload(userId, projectId, taskId, file)));
    }

    @GetMapping
    public ApiResponse<List<HandoverTaskAttachmentResponse>> list(
            @LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long taskId) {
        return ApiResponse.of(service.list(userId, projectId, taskId));
    }

    @DeleteMapping("/{attachmentId}")
    public ResponseEntity<Void> delete(@LoginUserId Long userId, @PathVariable Long projectId,
                                       @PathVariable Long taskId, @PathVariable Long attachmentId) {
        service.delete(userId, projectId, taskId, attachmentId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(
            @LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long taskId,
            @PathVariable Long attachmentId) {
        HandoverTaskAttachment attachment = service.download(userId, projectId, taskId, attachmentId);
        String disposition = ContentDisposition.attachment()
                .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8)
                .build().toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(attachment.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition)
                .body(attachment.getContent());
    }
}
