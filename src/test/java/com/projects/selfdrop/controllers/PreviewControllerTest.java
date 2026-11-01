package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.GetFileRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PreviewController.class)
public class PreviewControllerTest {

    private static final byte[] DATA = "png".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IFileService fileService;

    @MockitoBean
    private IFileStorage fileStorage;

    private static FileDto item(String id, String mime) {
        return new FileDto(1L, id, "file", null, "photo.png", mime, (long) DATA.length, 1000L);
    }

    @Test
    void imageIsServedInline() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(item("id1", "image/png"));
        when(fileStorage.exists("id1")).thenReturn(true);
        when(fileStorage.open("id1")).thenReturn(new ByteArrayInputStream(DATA));

        MvcResult result = mockMvc.perform(get("/preview/{id}", "id1"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("inline")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Security-Policy", "sandbox"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().bytes(DATA));
    }

    @Test
    void nonImageIsNotPreviewable() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(item("id1", "text/plain"));

        mockMvc.perform(get("/preview/{id}", "id1"))
                .andExpect(status().isNotFound());

        verify(fileStorage, never()).open(anyString());
    }

    @Test
    void svgIsNotPreviewable() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(item("id1", "image/svg+xml"));

        mockMvc.perform(get("/preview/{id}", "id1"))
                .andExpect(status().isNotFound());

        verify(fileStorage, never()).open(anyString());
    }

    @Test
    void missingBytesAreNotFound() throws Exception {
        when(fileService.get(new GetFileRequest("id1"))).thenReturn(item("id1", "image/png"));
        when(fileStorage.exists("id1")).thenReturn(false);

        mockMvc.perform(get("/preview/{id}", "id1"))
                .andExpect(status().isNotFound());

        verify(fileStorage, never()).open(anyString());
    }
}
