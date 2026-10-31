package com.projects.selfdrop.dtos.responses;

import jakarta.validation.constraints.NotNull;

public record ServerIdDto(
        @NotNull String id
) {
}
