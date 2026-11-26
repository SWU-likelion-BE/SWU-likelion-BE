package com.afterme.backend.handover.dto;

import java.time.LocalDate;

public record HandoverDeadlineExtensionResponse(
        String id,
        LocalDate deadline,
        long dDay,
        boolean overdue,
        Long resourceVersion
) {
}
