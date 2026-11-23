package com.afterme.backend.handover.dto;

import java.util.List;

public record HandoverListResponse(
        List<HandoverSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
