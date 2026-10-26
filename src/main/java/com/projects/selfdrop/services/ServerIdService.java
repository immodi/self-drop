package com.projects.selfdrop.services;

import com.projects.selfdrop.utils.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;

@Service
public class ServerIdService {
    private static final Logger log = LoggerFactory.getLogger(ServerIdService.class);
    private final String value;
    public ServerIdService(@Value("${server-id-path:./data/server-id}") Path path) throws IOException {
        this.value = loadOrCreate(path);
    }

    private static String loadOrCreate(Path path) throws IOException {
        if (Files.isRegularFile(path)) {
            return readValid(path);
        }

        Files.createDirectories(path.toAbsolutePath().getParent());
        String generated = IdGenerator.generate();

        Path tmp = Files.createTempFile(path.getParent(), ".server-id-", ".tmp");
        try {
            Files.writeString(tmp, generated);
            // move without REPLACE_EXISTING: throws FileAlreadyExistsException if it appeared
            Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE);
            return generated;
        } catch (FileAlreadyExistsException e) {
            Files.deleteIfExists(tmp);
            return readValid(path);         // someone else won — use theirs
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    private static String readValid(Path path) throws IOException {
        String content = Files.readString(path).strip();
        if (content.isEmpty()) {
            log.warn("server-id file was empty, regenerating: {}", path);
            Files.deleteIfExists(path);
            return loadOrCreate(path);
        }
        return content;
    }

    public String value() {
        return value;
    }
}
