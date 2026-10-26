package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.DeleteFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
import com.projects.selfdrop.dtos.UpdateFileRequest;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.services.FileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/file")
class FileController {
    private final IFileService fileService;
    public FileController(IFileService fileService) {
        this.fileService = fileService;
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
        return ResponseEntity.noContent().build();
    }
}
