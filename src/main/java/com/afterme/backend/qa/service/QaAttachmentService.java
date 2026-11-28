package com.afterme.backend.qa.service;

import com.afterme.backend.global.exception.BusinessException;
import com.afterme.backend.global.exception.ErrorCode;
import com.afterme.backend.global.file.AttachmentFilename;
import com.afterme.backend.learning.service.LearningAccess;
import com.afterme.backend.qa.domain.QaAttachment;
import com.afterme.backend.qa.dto.QaDtos.Attachment;
import com.afterme.backend.qa.repository.QaAttachmentRepository;
import com.afterme.backend.qa.repository.QaAttachmentRepository.Metadata;
import com.afterme.backend.qa.repository.QaThreadRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Clock;
import java.util.HexFormat;

import static com.afterme.backend.handover.domain.ParticipantRole.SUCCESSOR;

@Service
@RequiredArgsConstructor
public class QaAttachmentService {
    private static final int MAX_FILE_SIZE = 10 * 1024 * 1024;

    private final QaAttachmentRepository attachments;
    private final QaThreadRepository threads;
    private final LearningAccess access;
    private final Clock clock;

    @Transactional
    public Attachment upload(Long userId, Long projectId, Long threadId, MultipartFile file) throws IOException {
        requireAuthor(userId, projectId, threadId);
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT);
        }
        String name = AttachmentFilename.sanitize(file.getOriginalFilename());
        byte[] bytes = file.getBytes();
        String contentType = contentType(bytes);
        QaAttachment attachment = attachments.save(new QaAttachment(threadId, name, contentType, bytes, clock.instant()));
        return metadata(projectId, threadId, attachment.getId(), name, contentType, attachment.getSizeBytes());
    }

    @Transactional(readOnly = true)
    public QaAttachment download(Long userId, Long projectId, Long threadId, Long attachmentId) {
        access.project(userId, projectId);
        if (threads.findByIdAndProjectId(threadId, projectId).isEmpty()) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return attachments.findByIdAndThreadId(attachmentId, threadId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    @Transactional
    public void delete(Long userId, Long projectId, Long threadId, Long attachmentId) {
        requireAuthor(userId, projectId, threadId);
        if (attachments.deleteFromThread(attachmentId, threadId) == 0) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
    }

    private void requireAuthor(Long userId, Long projectId, Long threadId) {
        access.role(userId, projectId, SUCCESSOR);
        access.mutable(access.lock(projectId));
        var thread = threads.findForUpdate(threadId, projectId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!thread.getAuthorId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }

    private static String contentType(byte[] bytes) {
        String signature = HexFormat.of().formatHex(bytes, 0, Math.min(bytes.length, 8));
        if (signature.startsWith("255044462d")) return "application/pdf";
        if (signature.equals("89504e470d0a1a0a")) return "image/png";
        if (signature.startsWith("ffd8ff")) return "image/jpeg";
        if (signature.startsWith("474946383761") || signature.startsWith("474946383961")) return "image/gif";
        throw new BusinessException(ErrorCode.INVALID_INPUT);
    }

    static Attachment metadata(Long projectId, Long threadId, Metadata file) {
        return metadata(projectId, threadId, file.getId(), file.getOriginalFilename(),
                file.getContentType(), file.getSizeBytes());
    }

    private static Attachment metadata(Long projectId, Long threadId, Long id, String name, String type, long size) {
        String url = "/api/handovers/%d/questions/%d/attachments/%d".formatted(projectId, threadId, id);
        return new Attachment(id, name, type, size, url);
    }
}
