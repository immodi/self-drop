package com.projects.selfdrop.utils;

import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class IdGenerator {

    private static final Pattern SHAPE = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static Pattern shape() {
        return SHAPE;
    }

    public static boolean hasIdShape(String candidate) {
        return candidate != null && SHAPE.matcher(candidate).matches();
    }
}
