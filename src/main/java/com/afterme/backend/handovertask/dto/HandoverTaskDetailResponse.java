package com.afterme.backend.handovertask.dto;

import java.time.OffsetDateTime;

public record HandoverTaskDetailResponse(
        Long id,
        Long projectId,
        String title,
        String overview,
        String details,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
