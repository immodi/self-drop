package com.projects.selfdrop.dtos.requests;

import jakarta.validation.constraints.NotBlank;

public record DeleteFileRequest(
        @NotBlank String id
) {
}
