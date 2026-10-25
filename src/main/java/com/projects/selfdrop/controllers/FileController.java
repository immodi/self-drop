package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.CreateFileRequest;
import com.projects.selfdrop.dtos.DeleteFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
import com.projects.selfdrop.dtos.UpdateFileRequest;
import com.projects.selfdrop.services.FileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/file")
class FileController {
    private final FileService fileService;
    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @GetMapping
    public ResponseEntity<List<FileDto>> getAll() {
        return ResponseEntity.ok(fileService.getAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<FileDto> get(@PathVariable @NotBlank String id) {
        return ResponseEntity.ok(fileService.get(new GetFileRequest(id)));
    }

    @PostMapping
    public ResponseEntity<FileDto> post(@Valid @RequestBody CreateFileRequest request) {
        FileDto created = fileService.create(request);
        return ResponseEntity
                .created(URI.create("/file/" + created.id()))
                .body(created);
    }

    @PutMapping
    public ResponseEntity<FileDto> update(@Valid @RequestBody UpdateFileRequest request) {
        return ResponseEntity.ok(fileService.update(request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable @NotBlank String id) {
        fileService.delete(new DeleteFileRequest(id));
        return ResponseEntity.noContent().build();
    }
}
