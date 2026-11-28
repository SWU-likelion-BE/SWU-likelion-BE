package com.afterme.backend.handovertask.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.global.file.AttachmentFilename;
import com.afterme.backend.handovertask.domain.HandoverTaskAttachment;
import com.afterme.backend.handovertask.dto.HandoverTaskAttachmentResponse;
import com.afterme.backend.handovertask.repository.HandoverTaskAttachmentRepository;
import com.afterme.backend.handovertask.repository.HandoverTaskRepository;
import com.afterme.backend.learning.service.LearningAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;

import static com.afterme.backend.handover.domain.ParticipantRole.PREDECESSOR;

@Service
@RequiredArgsConstructor
public class HandoverTaskAttachmentService {

    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final HandoverTaskRepository tasks;
    private final HandoverTaskAttachmentRepository attachments;
    private final LearningAccess access;
    private final Clock clock;

    @Transactional
    public HandoverTaskAttachmentResponse upload(Long userId, Long projectId, Long taskId, MultipartFile file)
            throws IOException {
        access.role(userId, projectId, PREDECESSOR);
        access.mutable(access.lock(projectId));
        requireTask(projectId, taskId);

        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }

        HandoverTaskAttachment attachment = attachments.save(new HandoverTaskAttachment(
                taskId, AttachmentFilename.sanitize(file.getOriginalFilename()), contentType(file.getContentType()),
                file.getBytes(), OffsetDateTime.now(clock)));
        return new HandoverTaskAttachmentResponse(attachment.getId(), attachment.getOriginalFilename(),
                attachment.getContentType(), attachment.getSizeBytes(), attachment.getCreatedAt());
    }

    @Transactional(readOnly = true)
    public List<HandoverTaskAttachmentResponse> list(Long userId, Long projectId, Long taskId) {
        access.project(userId, projectId);
        requireTask(projectId, taskId);
        return attachments.listMetadata(taskId);
    }

    @Transactional(readOnly = true)
    public HandoverTaskAttachment download(Long userId, Long projectId, Long taskId, Long attachmentId) {
        access.project(userId, projectId);
        requireTask(projectId, taskId);
        return attachments.findByIdAndTaskId(attachmentId, taskId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private void requireTask(Long projectId, Long taskId) {
        if (!tasks.existsByIdAndProjectId(taskId, projectId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private static String contentType(String value) {
        if (value != null && !value.isBlank()) {
            try {
                MediaType type = MediaType.parseMediaType(value);
                if (type.isConcrete() && value.length() <= 255) {
                    return type.toString();
                }
            } catch (InvalidMediaTypeException ignored) {
                // Store a generic type when the upload omits a usable MIME type.
            }
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }
}
