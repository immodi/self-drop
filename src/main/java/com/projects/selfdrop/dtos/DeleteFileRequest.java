package com.projects.selfdrop.dtos;

import jakarta.validation.constraints.NotBlank;

public record DeleteFileRequest(
        @NotBlank String id
) {
}
