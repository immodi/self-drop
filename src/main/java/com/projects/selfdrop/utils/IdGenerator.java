package com.projects.selfdrop.utils;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdGenerator {
    private final String serverId = UUID.randomUUID().toString();

    public String getServerId() {
        return serverId;
    }

    public static String generate() {
        return UUID.randomUUID().toString();
    }
}