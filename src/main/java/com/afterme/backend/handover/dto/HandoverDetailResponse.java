package com.afterme.backend.handover.dto;

import com.afterme.backend.handover.domain.ParticipantRole;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record HandoverDetailResponse(
        String id,
        String organizationId,
        String title,
        String roleName,
        LocalDate deadline,
        String status,
        List<ParticipantResponse> participants,
        List<ParticipantRole> myRoles,
        long dDay,
        boolean overdue,
        int completeness,
        Long resourceVersion,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt,
        String organizationName,
        String affiliationName
) {
    public record ParticipantResponse(
            String memberId,
            String name,
            ParticipantRole role
    ){}
}
