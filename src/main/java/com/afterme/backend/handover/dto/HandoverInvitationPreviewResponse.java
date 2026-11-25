package com.afterme.backend.handover.dto;

public record HandoverInvitationPreviewResponse(
        String organizationId,
        String projectId,
        String projectTitle,
        String roleName
) {
}
