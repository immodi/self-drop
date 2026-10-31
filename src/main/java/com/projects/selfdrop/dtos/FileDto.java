package com.projects.selfdrop.dtos;


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
