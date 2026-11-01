package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.GetFileRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import com.projects.selfdrop.ui.UiFormat;
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

/**
 * Streams image bytes inline for the timeline preview. Only raster images are
 * previewable; everything else stays on the attachment-only download endpoint.
 */
@RestController
@RequestMapping("/preview")
class PreviewController {

    private final IFileStorage fileStorage;
    private final IFileService fileService;

    public PreviewController(IFileStorage fileStorage, IFileService fileService) {
        this.fileStorage = fileStorage;
        this.fileService = fileService;
    }

    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> preview(@PathVariable String id) {
        FileDto meta = fileService.get(new GetFileRequest(id));

        if (!UiFormat.isPreviewable(meta) || !fileStorage.exists(meta.id())) {
            throw new FileNotFoundInDbException(meta.id());
        }

        MediaType contentType;
        try {
            contentType = MediaType.parseMediaType(meta.mime());
        } catch (InvalidMediaTypeException e) {
            throw new FileNotFoundInDbException(meta.id());
        }

        StreamingResponseBody body = out -> {
            try (InputStream in = fileStorage.open(meta.id())) {
                in.transferTo(out);
            }
        };

        String filename = meta.name() != null ? meta.name() : meta.id();
        ResponseEntity.BodyBuilder response = ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600");

        if (meta.size() != null) {
            response.contentLength(meta.size());
        }

        return response.body(body);
    }
}
