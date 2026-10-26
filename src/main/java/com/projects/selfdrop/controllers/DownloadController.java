package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/download")
class DownloadController {
    private static final MediaType DEFAULT_CONTENT_TYPE = MediaType.APPLICATION_OCTET_STREAM;

    private final IFileStorage fileStorage;
    private final IFileService fileService;

    public DownloadController(IFileStorage fileStorage, IFileService fileService) {
        this.fileStorage = fileStorage;
        this.fileService = fileService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String id) {
        FileDto meta = fileService.get(new GetFileRequest(id));

        if (!fileStorage.exists(meta.id())) {
            throw new FileNotFoundInDbException(meta.id());
        }

        StreamingResponseBody body = out -> {
            try (InputStream in = fileStorage.open(meta.id())) {
                in.transferTo(out);
            }
        };

        String filename = meta.name() != null ? meta.name() : meta.id();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(contentType(meta.mime()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox");

        if (meta.size() != null) {
            response.contentLength(meta.size());
        }

        return response.body(body);
    }

    private static MediaType contentType(String mime) {
        if (mime == null || mime.isBlank()) {
            return DEFAULT_CONTENT_TYPE;
        }
        try {
            return MediaType.parseMediaType(mime);
        } catch (InvalidMediaTypeException e) {
            return DEFAULT_CONTENT_TYPE;
        }
    }
}
