package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.DeleteFileRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.dtos.requests.GetFileRequest;
import com.projects.selfdrop.dtos.requests.UpdateFileRequest;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/file")
class FileController {
    private static final Logger log = LoggerFactory.getLogger(FileController.class);
    private final IFileService fileService;
    private final IFileStorage fileStorage;
    public FileController(IFileService fileService, IFileStorage fileStorage) {
        this.fileService = fileService;
        this.fileStorage = fileStorage;
    }

    @GetMapping
    public ResponseEntity<List<FileDto>> getAll(
            @RequestParam(required = false) Long after,
            @RequestParam(required = false) Integer limit) {
        List<FileDto> files;
        if (after != null) {
            files = fileService.findSince(after);
        } else if (limit != null) {
            files = fileService.findLatest(limit);
        } else {
            files = fileService.getAll();
        }
        return ResponseEntity.ok(files);
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDto> get(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(fileService.get(new GetFileRequest(id)));
    }

    @PutMapping
    public ResponseEntity<FileDto> update(@Valid @RequestBody UpdateFileRequest request) {
        return ResponseEntity.ok(fileService.update(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank String id) {
        fileService.delete(new DeleteFileRequest(id));

        try {
            fileStorage.delete(id);
        } catch (IOException e) {
            // The row is gone, so the item is already invisible; the scheduled orphan
            // sweep reclaims the bytes later. Failing the request would only confuse
            // the caller about a delete action that did happen.
            log.warn("Deleted row {} but could not delete its file; leaving it to the orphan sweep", id, e);
        }

        return ResponseEntity.noContent().build();
    }
}
