package com.afterme.backend.handover.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHandoverInvitationRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 255) String roleName
) {
}
