package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
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
    void setUp() {
        fileDto = new FileDto("id1", "file", null, "hello.txt", "text/plain", (long) DATA.length, 1000L);
    }

    @Test
    void downloadStreamsFileAsAttachment() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(true);
        when(fileStorage.open("id1")).thenReturn(new ByteArrayInputStream(DATA));

        MvcResult result = mockMvc.perform(get("/download/{id}", "id1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_PLAIN))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("hello.txt")))
                .andExpect(header().string(HttpHeaders.CONTENT_LENGTH, String.valueOf(DATA.length)))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DATA));

        verify(fileStorage).open("id1");
    }

    @Test
    void downloadReturnsNotFoundWhenRowDoesNotExist() throws Exception {
        when(fileService.get(new GetFileRequest("missing")))
                .thenThrow(new FileNotFoundInDbException("missing"));

        mockMvc.perform(get("/download/{id}", "missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.detail").value("No file with id: missing"));

        verify(fileStorage, never()).open(anyString());
    }

    @Test
    void downloadReturnsNotFoundWhenBytesAreMissing() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(fileDto);
        when(fileStorage.exists("id1")).thenReturn(false);

        mockMvc.perform(get("/download/{id}", "id1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.detail").value("No file with id: id1"));

        verify(fileStorage, never()).open(anyString());
    }

    @Test
    void downloadFallsBackWhenMimeAndNameAreMissing() throws Exception {
        byte[] data = {1, 2, 3};
        FileDto sparse = new FileDto("id2", "file", null, null, null, (long) data.length, 1000L);
        when(fileService.get(new GetFileRequest("id2"))).thenReturn(sparse);
        when(fileStorage.exists("id2")).thenReturn(true);
        when(fileStorage.open("id2")).thenReturn(new ByteArrayInputStream(data));

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
}
