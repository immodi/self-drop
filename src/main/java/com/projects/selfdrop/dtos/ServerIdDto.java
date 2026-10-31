package com.projects.selfdrop.dtos;

import jakarta.validation.constraints.NotNull;

public record ServerIdDto(
        @NotNull String id
) {
}
