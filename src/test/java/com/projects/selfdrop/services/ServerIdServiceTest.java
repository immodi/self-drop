package com.projects.selfdrop.services;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

public class ServerIdServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void createsIdOnFirstRunAndReusesIt() throws Exception {
        Path path = tempDir.resolve("server-id");

        String first = new ServerIdService(path).value();
        String second = new ServerIdService(path).value();

        assertFalse(first.isBlank());
        assertEquals(first, second);
        assertEquals(first, Files.readString(path));
    }

    @Test
    void readsExistingId() throws Exception {
        Path path = tempDir.resolve("server-id");
        Files.writeString(path, "fixed-id");

        assertEquals("fixed-id", new ServerIdService(path).value());
    }

    @Test
    void regeneratesWhenFileIsEmpty() throws Exception {
        Path path = tempDir.resolve("server-id");
        Files.createFile(path);

        String id = new ServerIdService(path).value();

        assertFalse(id.isBlank());
        assertEquals(id, Files.readString(path));
    }

    @Test
    void createsMissingParentDirectories() throws Exception {
        Path path = tempDir.resolve("nested/dir/server-id");

        String id = new ServerIdService(path).value();

        assertEquals(id, Files.readString(path));
    }
}
