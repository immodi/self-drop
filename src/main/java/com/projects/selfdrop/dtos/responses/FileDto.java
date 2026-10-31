package com.projects.selfdrop.dtos.responses;


public record FileDto(
        Long seq,
        String id,
        String kind,
        String text,
        String name,
        String mime,
        Long size,
        Long createdAt
) {
}
