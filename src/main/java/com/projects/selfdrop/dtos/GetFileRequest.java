package com.projects.selfdrop.dtos;

import jakarta.validation.constraints.NotBlank;

public record GetFileRequest(
        @NotBlank String id
) {
}
