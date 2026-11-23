package com.afterme.backend.handover.dto;

import java.time.LocalDate;
import java.util.List;

public record HandoverSummaryResponse(
        Long id,
        Long organizationId,
        String title,
        String roleName,
        String status,
        List<String> myRoles,
        LocalDate deadline,
        int dDay,
        boolean overdue,
        int completeness
) {
}
