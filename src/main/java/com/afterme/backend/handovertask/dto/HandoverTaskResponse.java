package com.afterme.backend.handovertask.dto;

import java.time.OffsetDateTime;

public record HandoverTaskResponse(
        Long id,
        Long projectId,
        String title,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
