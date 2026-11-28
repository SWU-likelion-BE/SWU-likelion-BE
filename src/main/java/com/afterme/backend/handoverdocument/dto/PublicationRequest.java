package com.afterme.backend.handoverdocument.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PublicationRequest(@NotNull @PositiveOrZero Long expectedVersion) {}
