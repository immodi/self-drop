package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.dtos.requests.GetFileRequest;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(DownloadController.class)
public class DownloadControllerTest {
    private static final byte[] DATA = "hello".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IFileService fileService;

    @MockitoBean
    private IFileStorage fileStorage;

    private FileDto fileDto;

    @BeforeEach
    void setUp() throws IOException {
        fileDto = new FileDto(1L, "id1", "file", null, "hello.txt", "text/plain", (long) DATA.length, 1000L);
        when(fileStorage.size("id1")).thenReturn((long) DATA.length);
        stubStream("id1", DATA);
    }

    @Test
    void downloadStreamsFileAsAttachment() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("hello.txt")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(DATA.length)))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DATA));

        verify(fileStorage).stream(eq("id1"), eq(0L), eq((long) DATA.length), any(OutputStream.class));
    }

    @Test
    void downloadReturnsPartialContentForByteRange() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=1-3"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 1-3/5"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isPartialContent())
                .andExpect(content().bytes("ell".getBytes(StandardCharsets.UTF_8)));

        verify(fileStorage).stream(eq("id1"), eq(1L), eq(3L), any(OutputStream.class));
    }

    @Test
    void downloadSupportsOpenEndedRange() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=2-"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 2-4/5"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "3"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().bytes("llo".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadSupportsSuffixRange() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=-2"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 3-4/5"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().bytes("lo".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadClampsRangeEndToFileSize() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=3-999"))
                .andExpect(status().isPartialContent())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes 3-4/5"))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, "2"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().bytes("lo".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void downloadReturnsRequestedRangeNotSatisfiablePastEnd() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=5-"))
                .andExpect(status().isRequestedRangeNotSatisfiable())
                .andExpect(header().string(HttpHeaders.CONTENT_RANGE, "bytes */5"))
                .andExpect(header().string(HttpHeaders.ACCEPT_RANGES, "bytes"))
                .andExpect(request().asyncNotStarted());

        verify(fileStorage, never()).stream(anyString(), anyLong(), anyLong(), any(OutputStream.class));
    }

    @Test
    void downloadIgnoresMultipleRangesAndSendsWholeFile() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=0-1,3-4"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().bytes(DATA));
    }

    @Test
    void downloadIgnoresMalformedRangeAndSendsWholeFile() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1")
                        .header(HttpHeaders.RANGE, "bytes=nonsense"))
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(HttpHeaders.CONTENT_RANGE))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(content().bytes(DATA));
    }

    @Test
    void downloadReturnsNotFoundWhenRowDoesNotExist() throws Exception {
        when(fileService.get(new GetFileRequest("missing")))
                .thenThrow(new FileNotFoundInDbException("missing"));

        mockMvc.perform(get("/download/{id}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.detail").value("No file with id: missing"));

        verify(fileStorage, never()).stream(anyString(), anyLong(), anyLong(), any(OutputStream.class));
    }

    @Test
    void downloadReturnsNotFoundWhenBytesAreMissing() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(false);

        mockMvc.perform(get("/download/{id}", "id1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.detail").value("No file with id: id1"));

        verify(fileStorage, never()).stream(anyString(), anyLong(), anyLong(), any(OutputStream.class));
    }

    @Test
    void downloadFallsBackWhenMimeAndNameAreMissing() throws Exception {
        byte[] data = {1, 2, 3};
        FileDto sparse = new FileDto(2L, "id2", "file", null, null, null, (long) data.length, 1000L);
        when(fileService.get(new GetFileRequest("id2"))).thenReturn(sparse);
        when(fileStorage.exists("id2")).thenReturn(true);
        when(fileStorage.size("id2")).thenReturn((long) data.length);
        stubStream("id2", data);

        MvcResult result = mockMvc.perform(get("/download/{id}", "id2"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_OCTET_STREAM))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("id2")))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(data));
    }

    private void stubStream(String id, byte[] data) throws IOException {
        doAnswer(invocation -> {
            long offset = invocation.getArgument(1);
            long length = invocation.getArgument(2);
            OutputStream out = invocation.getArgument(3);
            out.write(data, Math.toIntExact(offset), Math.toIntExact(length));
            return null;
        }).when(fileStorage).stream(eq(id), anyLong(), anyLong(), any(OutputStream.class));
    }
}
