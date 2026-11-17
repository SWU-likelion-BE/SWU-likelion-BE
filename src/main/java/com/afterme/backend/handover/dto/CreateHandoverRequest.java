package com.afterme.backend.handover.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record CreateHandoverRequest (
        @NotBlank String organizationId,
        @NotBlank String title,
        @NotBlank String roleName,
        @NotNull LocalDate deadline,
        @NotEmpty List<@NotBlank String> predecessorMemberIds
){

}
