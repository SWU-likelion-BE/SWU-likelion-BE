package com.afterme.backend.handovertask.dto;

import java.time.OffsetDateTime;

public record HandoverTaskAttachmentResponse(
        Long id,
        String originalFilename,
        String contentType,
        long sizeBytes,
        OffsetDateTime createdAt
) {
}
