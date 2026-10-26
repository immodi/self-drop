package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.CreateFileRequest;
import com.projects.selfdrop.dtos.DeleteFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UploadController.class)
public class UploadControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IFileService fileService;

    @MockitoBean
    private IFileStorage fileStorage;

    private void echoCreatedFile() {
        when(fileService.create(any(CreateFileRequest.class)))
                .thenAnswer(invocation -> toDto(invocation.getArgument(0, CreateFileRequest.class)));
    }

    private static FileDto toDto(CreateFileRequest request) {
        return new FileDto(
                request.id(),
                request.kind(),
                request.text(),
                request.name(),
                request.mime(),
                request.size(),
                1000L);
    }

    // ------------------------------------------------------------------------ text

    @Test
    void uploadText_createsTextItem() throws Exception {
        echoCreatedFile();

        MvcResult result = mockMvc.perform(post("/upload/text")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.text").value("hello"))
                .andReturn();

        ArgumentCaptor<CreateFileRequest> captor = ArgumentCaptor.forClass(CreateFileRequest.class);
        verify(fileService).create(captor.capture());

        CreateFileRequest request = captor.getValue();
        assertThat(request.id()).isNotBlank();
        assertThat(request.kind()).isEqualTo("text");
        assertThat(request.text()).isEqualTo("hello");
        assertThat(request.size()).isEqualTo(5L);
        assertThat(result.getResponse().getHeader("Location")).isEqualTo("/file/" + request.id());
    }

    // ------------------------------------------------------------------------ file

    @Test
    void uploadFile_storesBytesAndCreatesRow() throws Exception {
        echoCreatedFile();

        MockMultipartFile file = new MockMultipartFile(
                "files", "hello.txt", "text/plain", "hello".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload/file").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].kind").value("file"))
                .andExpect(jsonPath("$[0].name").value("hello.txt"))
                .andExpect(jsonPath("$[0].mime").value("text/plain"))
                .andExpect(jsonPath("$[0].size").value(5));

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<CreateFileRequest> requestCaptor = ArgumentCaptor.forClass(CreateFileRequest.class);
        verify(fileStorage).store(keyCaptor.capture(), any(InputStream.class));
        verify(fileService).create(requestCaptor.capture());

        CreateFileRequest request = requestCaptor.getValue();
        assertThat(keyCaptor.getValue()).isNotBlank();
        assertThat(request.id()).isEqualTo(keyCaptor.getValue());
        assertThat(request.kind()).isEqualTo("file");
        assertThat(request.name()).isEqualTo("hello.txt");
        assertThat(request.mime()).isEqualTo("text/plain");
        assertThat(request.size()).isEqualTo(5L);
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void uploadFile_handlesMultipleFiles() throws Exception {
        echoCreatedFile();

        MockMultipartFile first = new MockMultipartFile(
                "files", "a.txt", "text/plain", "aaa".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile second = new MockMultipartFile(
                "files", "b.bin", "application/octet-stream", new byte[]{1, 2, 3, 4});

        mockMvc.perform(multipart("/upload/file").file(first).file(second))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("a.txt"))
                .andExpect(jsonPath("$[1].name").value("b.bin"));

        verify(fileStorage, times(2)).store(anyString(), any(InputStream.class));
        verify(fileService, times(2)).create(any(CreateFileRequest.class));
    }

    @Test
    void uploadFile_skipsEmptyFiles() throws Exception {
        MockMultipartFile empty = new MockMultipartFile(
                "files", "empty.txt", "text/plain", new byte[0]);

        mockMvc.perform(multipart("/upload/file").file(empty))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        verifyNoInteractions(fileStorage, fileService);
    }

    @Test
    void uploadFile_returnsBadRequestWhenNoFilesProvided() throws Exception {
        mockMvc.perform(multipart("/upload/file"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Missing required request part: files."));

        verifyNoInteractions(fileStorage, fileService);
    }

    @Test
    void uploadFile_rollsBackRowsAndBytesWhenRowCreationFails() throws Exception {
        AtomicInteger creates = new AtomicInteger();
        when(fileService.create(any(CreateFileRequest.class))).thenAnswer(invocation -> {
            CreateFileRequest request = invocation.getArgument(0, CreateFileRequest.class);
            if (creates.incrementAndGet() == 2) {
                throw new IllegalStateException("db down");
            }
            return toDto(request);
        });

        MockMultipartFile first = new MockMultipartFile(
                "files", "a.txt", "text/plain", "aaa".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile second = new MockMultipartFile(
                "files", "b.txt", "text/plain", "bbb".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload/file").file(first).file(second))
                .andExpect(status().isInternalServerError());

        verify(fileService, times(2)).delete(any(DeleteFileRequest.class));
        verify(fileStorage, times(2)).delete(anyString());
    }

    @Test
    void uploadFile_keepsBytesWhenRowDeleteFails() throws Exception {
        when(fileService.create(any(CreateFileRequest.class)))
                .thenThrow(new IllegalStateException("db down"));
        doThrow(new IllegalStateException("cannot delete row"))
                .when(fileService).delete(any(DeleteFileRequest.class));

        MockMultipartFile file = new MockMultipartFile(
                "files", "a.txt", "text/plain", "aaa".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload/file").file(file))
                .andExpect(status().isInternalServerError());

        verify(fileService).delete(any(DeleteFileRequest.class));
        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void uploadFile_rollsBackStoredBytesWhenStoreFails() throws Exception {
        echoCreatedFile();
        doNothing().doThrow(new IOException("disk full"))
                .when(fileStorage).store(anyString(), any(InputStream.class));

        MockMultipartFile first = new MockMultipartFile(
                "files", "a.txt", "text/plain", "aaa".getBytes(StandardCharsets.UTF_8));
        MockMultipartFile second = new MockMultipartFile(
                "files", "b.txt", "text/plain", "bbb".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/upload/file").file(first).file(second))
                .andExpect(status().isInternalServerError());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(fileStorage, times(2)).store(keyCaptor.capture(), any(InputStream.class));

        String storedKey = keyCaptor.getAllValues().getFirst();
        verify(fileService).create(any(CreateFileRequest.class));
        verify(fileService).delete(new DeleteFileRequest(storedKey));
        verify(fileStorage).delete(storedKey);
    }
}
