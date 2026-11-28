package com.afterme.backend.handoverdocument.dto;

import java.time.Instant;
import java.util.List;

public record DraftResponse(
        Long documentId,
        Long versionId,
        Long taskId,
        Long confirmationId,
        String title,
        long revision,
        Instant updatedAt,
        List<Section> sections
) {
    public record Section(Long sectionId, String type, String title, String content, int position) {
    }
}
