package com.afterme.backend.handover.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record ExtendHandoverDeadlineRequest(
        @NotNull LocalDate deadline,
        @NotBlank String reason,
        @NotNull Long expectedVersion
) {
}
