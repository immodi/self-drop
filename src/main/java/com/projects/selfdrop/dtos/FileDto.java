package com.projects.selfdrop.dtos;


public record FileDto(
        String id,
        String kind,
        String text,
        String name,
        String mime,
        Long size,
        Long createdAt
) {
}
