package com.afterme.backend.handoverdocument.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record DraftSectionInput(
        @NotBlank @Size(max = 255) String type,
        @NotBlank @Size(max = 255) String title,
        @NotNull String content
) {
}
