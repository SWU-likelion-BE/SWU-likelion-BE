package com.afterme.backend.handover.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record CreateHandoverRequest (
        @NotBlank String organizationId,
        @NotBlank String title,
        @NotBlank String roleName,
        @NotNull LocalDate deadline,
        @NotEmpty List<@NotBlank String> predecessorMemberIds,
        @Size(max = 255) String organizationName,
        @Size(max = 255) String affiliationName
){
    public CreateHandoverRequest(String organizationId, String title, String roleName, LocalDate deadline,
                                 List<String> predecessorMemberIds) {
        this(organizationId, title, roleName, deadline, predecessorMemberIds, null, null);
    }

}
