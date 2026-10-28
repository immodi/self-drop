package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.*;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import com.projects.selfdrop.utils.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/upload")
class UploadController {

    private static final Logger log = LoggerFactory.getLogger(UploadController.class);

    private final IFileService fileService;
    private final IFileStorage fileStorageService;

    public UploadController(IFileService fileService, IFileStorage fileStorageService) {
        this.fileService = fileService;
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/text")
    public ResponseEntity<TextDto> post(@RequestBody String text) {
        String id = IdGenerator.generate();
        CreateFileRequest createRequest = new UploadTextRequest(text).toCreateFileRequest(id);
        FileDto created = fileService.create(createRequest);
        return ResponseEntity
                .created(URI.create("/file/" + created.id()))
                .body(new TextDto(created.text()));
    }

    @PostMapping(value = "/file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<List<FileDto>> upload(@RequestParam("files") List<MultipartFile> files) throws IOException {
        List<FileDto> createdFiles = new ArrayList<>();
        List<String> storedKeys = new ArrayList<>();

        try {
            for (MultipartFile file : files) {
                if (file.isEmpty()) continue;

                String id = IdGenerator.generate();

                // getInputStream() streams from the temp file — never loads into heap.
                // Do NOT use file.getBytes(): that loads the entire file into memory.
                try (InputStream in = file.getInputStream()) {
                    fileStorageService.store(id, in);
                    storedKeys.add(id);
                }

                CreateFileRequest createRequest = new UploadFileRequest(
                        file.getOriginalFilename(),
                        file.getContentType(),
                        file.getSize())
                        .toCreateFileRequest(id);

                FileDto created = fileService.create(createRequest);
                createdFiles.add(created);
            }

            return ResponseEntity.ok(createdFiles);
        } catch (RuntimeException | IOException e) {
            // Rows first, bytes second. Only remove a file's bytes once its row is
            // known to be gone, so a failed row delete can never leave a dangling row.
            for (String key : storedKeys) {
                try {
                    fileService.delete(new DeleteFileRequest(key));
                } catch (FileNotFoundInDbException rowNeverCreated) {
                    // No row to keep consistent, so the bytes are safe to remove below.
                } catch (Exception cleanupFailure) {
                    log.warn("Failed to delete file row {} while rolling back upload; keeping its stored file",
                            key, cleanupFailure);
                    continue;
                }

                try {
                    fileStorageService.delete(key);
                } catch (Exception cleanupFailure) {
                    log.warn("Failed to delete stored file {} while rolling back upload; it will be reclaimed by the orphan sweep",
                            key, cleanupFailure);
                }
            }
            throw e;
        }
    }

}
