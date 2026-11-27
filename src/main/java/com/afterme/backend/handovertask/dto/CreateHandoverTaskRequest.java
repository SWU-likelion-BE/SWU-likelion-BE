package com.afterme.backend.handovertask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateHandoverTaskRequest(
        @NotBlank @Size(max=255) String title
) {

}
