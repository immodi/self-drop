package com.projects.selfdrop.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateChatRequest(
        @NotBlank
        @Size(max = 120, message = "name must be at most 120 characters")
        String name
) {
}
