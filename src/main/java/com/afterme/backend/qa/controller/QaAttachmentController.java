package com.afterme.backend.qa.controller;

import com.afterme.backend.global.response.ApiResponse;
import com.afterme.backend.global.security.LoginUserId;
import com.afterme.backend.qa.dto.QaDtos.Attachment;
import com.afterme.backend.qa.service.QaAttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/handovers/{projectId}/questions/{threadId}/attachments")
@RequiredArgsConstructor
public class QaAttachmentController {
    private final QaAttachmentService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Attachment>> upload(
            @LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long threadId,
            @RequestPart("file") MultipartFile file) throws IOException {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.of(service.upload(userId, projectId, threadId, file)));
    }

    @GetMapping("/{attachmentId}")
    public ResponseEntity<byte[]> download(
            @LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long threadId,
            @PathVariable Long attachmentId) {
        var attachment = service.download(userId, projectId, threadId, attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .contentLength(attachment.getSizeBytes())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.getOriginalFilename(), StandardCharsets.UTF_8).build().toString())
                .body(attachment.getContent());
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@LoginUserId Long userId, @PathVariable Long projectId, @PathVariable Long threadId,
                       @PathVariable Long attachmentId) {
        service.delete(userId, projectId, threadId, attachmentId);
    }
}
