package com.projects.selfdrop.services;

import com.projects.selfdrop.interfaces.IFileStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.Objects;

@Service
public class FileStorageService implements IFileStorage {

    private final Path storageRoot;

    public FileStorageService(@Value("${storage.root:./data/files}") Path storageRoot) {
        this.storageRoot = Objects.requireNonNull(storageRoot).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.storageRoot);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create storage root: " + this.storageRoot, e);
        }
    }

    @Override
    public void store(String key, InputStream in) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());

        Path tmp = Files.createTempFile(target.getParent(), UPLOAD_TEMP_PREFIX, UPLOAD_TEMP_SUFFIX);
        try {
            try (OutputStream out = Files.newOutputStream(tmp,
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                in.transferTo(out);
            }

            try {
                Files.move(tmp, target,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Best effort: keep the original failure instead of masking it.
            }
        }
    }

    @Override
    public void store(String key, byte[] data) throws IOException {
        store(key, new ByteArrayInputStream(data));
    }

    @Override
    public InputStream open(String key) throws IOException {
        Path p = resolve(key);
        if (!Files.isRegularFile(p)) {
            throw new NoSuchFileException(key);
        }
        return Files.newInputStream(p);
    }

    @Override
    public byte[] read(String key) throws IOException {
        try (InputStream in = open(key)) {
            return in.readAllBytes();
        }
    }

    @Override
    public void stream(String key, OutputStream out) throws IOException {
        try (InputStream in = open(key)) {
            in.transferTo(out);
        }
    }

    @Override
    public boolean exists(String key) {
        return Files.isRegularFile(resolve(key));
    }

    @Override
    public long size(String key) throws IOException {
        return Files.size(resolve(key));
    }

    @Override
    public void delete(String key) throws IOException {
        Files.deleteIfExists(resolve(key));
    }

    private Path resolve(String key) {
        Objects.requireNonNull(key, "key");
        Path p = storageRoot.resolve(key).normalize();
        if (p.equals(storageRoot) || !p.startsWith(storageRoot)) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        return p;
    }
}
