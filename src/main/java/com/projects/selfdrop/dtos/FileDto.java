package com.projects.selfdrop.dtos;

import com.projects.selfdrop.entities.File;
import com.projects.selfdrop.utils.IdGenerator;

import java.time.Instant;

public record FileDto(
        String id,
        String kind,
        String text,
        String name,
        String mime,
        Long size,
        String device,
        Long createdAt
) {
}
