package com.projects.selfdrop.dtos.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateFileRequest(
        String id,
        @NotBlank
        @Pattern(regexp = "text|file", message = "kind must be 'text' or 'file'")
        String kind,
        String text,
        String name,
        String mime,
        @PositiveOrZero Long size
) {
}
