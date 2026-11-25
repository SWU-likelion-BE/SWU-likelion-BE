package com.afterme.backend.handover.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record HandoverInvitationCodeRequest(@NotBlank @Size(max = 255) String code) {
}
