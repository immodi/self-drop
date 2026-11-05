package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.GetFileRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import com.projects.selfdrop.ui.UiFormat;
import com.projects.selfdrop.utils.ByteRanges;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Streams images and videos inline for the timeline preview. Only raster images
 * and videos are previewable; everything else stays on the attachment-only
 * download endpoint.
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
    public ResponseEntity<StreamingResponseBody> preview(
            @PathVariable String id,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String rangeHeader) throws IOException {
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

        long size = fileStorage.size(meta.id());
        ByteRanges.Selection range = ByteRanges.select(rangeHeader, size);

        if (range.unsatisfiable()) {
            return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE)
                    .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                    .header(HttpHeaders.CONTENT_RANGE, "bytes */" + size)
                    .build();
        }

        StreamingResponseBody body = out -> fileStorage.stream(meta.id(), range.start(), range.length(), out);

        String filename = meta.name() != null ? meta.name() : meta.id();
        ResponseEntity.BodyBuilder response = ResponseEntity.status(
                        range.partial() ? HttpStatus.PARTIAL_CONTENT : HttpStatus.OK)
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(filename, StandardCharsets.UTF_8)
                                .build()
                                .toString())
                .header(HttpHeaders.ACCEPT_RANGES, "bytes")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox")
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentLength(range.length());

        if (range.partial()) {
            response.header(HttpHeaders.CONTENT_RANGE,
                    "bytes " + range.start() + "-" + (range.start() + range.length() - 1) + "/" + size);
        }

        return response.body(body);
    }
}
