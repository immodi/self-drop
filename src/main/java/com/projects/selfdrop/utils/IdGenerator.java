package com.projects.selfdrop.utils;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class IdGenerator {
    private volatile String id = UUID.randomUUID().toString();

    public String getId() {
        return id;
    }

    public void refresh() {
        this.id = UUID.randomUUID().toString();
    }
}