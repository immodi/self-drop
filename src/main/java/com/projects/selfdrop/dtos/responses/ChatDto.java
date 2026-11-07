package com.projects.selfdrop.dtos.responses;

public record ChatDto(
        Long seq,
        String id,
        String name,
        Long createdAt
) {
}
