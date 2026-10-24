package com.projects.selfdrop.dtos;

import jakarta.validation.constraints.NotNull;

public record IdDto(
        @NotNull String id
) {
}
