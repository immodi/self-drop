package com.projects.selfdrop.services;

import com.projects.selfdrop.interfaces.IFileRepository;
import com.projects.selfdrop.utils.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import static com.projects.selfdrop.interfaces.IFileStorage.UPLOAD_TEMP_PREFIX;
import static com.projects.selfdrop.interfaces.IFileStorage.UPLOAD_TEMP_SUFFIX;

@Service
public class OrphanSweepService {
    private static final Logger log = LoggerFactory.getLogger(OrphanSweepService.class);

    private final Path storageRoot;
    private final IFileRepository fileRepository;
    private final Duration grace;

    public OrphanSweepService(@Value("${storage.root:./data/files}") Path storageRoot,
                              @Value("${storage.sweep.grace-seconds:1800}") long graceSeconds,
                              IFileRepository fileRepository) {
        this.storageRoot = Objects.requireNonNull(storageRoot).toAbsolutePath().normalize();
        this.fileRepository = Objects.requireNonNull(fileRepository);
        this.grace = Duration.ofSeconds(graceSeconds);
    }

    @Scheduled(fixedRateString = "${storage.sweep.interval-seconds:1800}")
    public int sweepOrphanFiles() {
        if (!Files.isDirectory(storageRoot)) {
            log.warn("Storage root {} is not a directory; nothing to sweep", storageRoot);
            return 0;
        }

        List<Path> entries;
        try (Stream<Path> paths = Files.list(storageRoot)) {
            entries = paths.toList();
        } catch (IOException e) {
            log.error("Could not list storage root {}; skipping sweep", storageRoot, e);
            return 0;
        }

        Instant cutoff = Instant.now().minus(grace);
        int deleted = 0;

        for (Path entry : entries) {
            String id = entry.getFileName().toString();
            if (!IdGenerator.hasIdShape(id)) {
                continue;
            }

            try {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                if (Files.getLastModifiedTime(entry).toInstant().isAfter(cutoff)) {
                    continue;
                }
            } catch (IOException e) {
                log.warn("Could not inspect {}; skipping it this run", entry, e);
                continue;
            }

            boolean exists;
            try {
                exists = fileRepository.existsById(id);
            } catch (DataAccessException e) {
                log.error("Aborting orphan sweep: cannot query the database (deleted {} file(s) before aborting)",
                        deleted, e);
                return deleted;
            }
            if (exists) {
                continue;
            }

            try {
                Files.delete(entry);
                deleted++;
                log.info("Deleted orphan file {}", entry);
            } catch (NoSuchFileException e) {
                // Already removed elsewhere: nothing left to do.
                log.debug("Orphan file {} already removed", entry);
            } catch (IOException e) {
                log.warn("Could not delete orphan {}; leaving it for the next run", entry, e);
            }
        }

        if (deleted > 0) {
            log.info("Orphan sweep removed {} file(s)", deleted);
        }
        return deleted;
    }


    @Scheduled(fixedRateString = "${storage.sweep.interval-seconds:1800}")
    public int sweepAbandonedUploads() {
        if (!Files.isDirectory(storageRoot)) {
            log.warn("Storage root {} is not a directory; no temp file to sweep", storageRoot);
            return 0;
        }

        List<Path> entries;
        try (Stream<Path> paths = Files.list(storageRoot)) {
            entries = paths.toList();
        } catch (IOException e) {
            log.error("Could not list storage root {}; skipping abandoned-upload sweep", storageRoot, e);
            return 0;
        }

        Instant cutoff = Instant.now().minus(grace);
        int deleted = 0;

        for (Path entry : entries) {
            String name = entry.getFileName().toString();
            if (!name.startsWith(UPLOAD_TEMP_PREFIX) || !name.endsWith(UPLOAD_TEMP_SUFFIX)) {
                continue;
            }

            try {
                if (!Files.isRegularFile(entry)) {
                    continue;
                }
                if (Files.getLastModifiedTime(entry).toInstant().isAfter(cutoff)) {
                    continue;
                }
            } catch (IOException e) {
                log.warn("Could not inspect abandoned upload temp file {}; skipping it this run", entry, e);
                continue;
            }

            try {
                Files.delete(entry);
                deleted++;
                log.info("Deleted abandoned upload temp file {}", entry);
            } catch (NoSuchFileException e) {
                // Already removed elsewhere: nothing left to do.
                log.debug("Abandoned upload temp file {} already removed", entry);
            } catch (IOException e) {
                log.warn("Could not delete abandoned upload temp file {}; leaving it for the next run", entry, e);
            }
        }

        if (deleted > 0) {
            log.info("Abandoned-upload sweep removed {} file(s)", deleted);
        }
        return deleted;
    }
}
