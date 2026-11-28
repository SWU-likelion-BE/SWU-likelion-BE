package com.afterme.backend.handoverdocument.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;

public record UpdateDraftRequest(
        @NotNull @PositiveOrZero Long revision,
        @NotBlank @Size(max = 255) String title,
        @NotNull List<@NotNull @Valid Section> sections
) {
    public record Section(
            @Positive Long sectionId,
            @NotBlank @Size(max = 255) String type,
            @NotBlank @Size(max = 255) String title,
            @NotNull String content
    ) {
    }
}
