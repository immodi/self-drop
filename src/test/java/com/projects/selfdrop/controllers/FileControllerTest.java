package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.DeleteFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
import com.projects.selfdrop.dtos.UpdateFileRequest;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.interfaces.IFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(FileController.class)
public class FileControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IFileService fileService;

    @MockitoBean
    private IFileStorage fileStorage;

    private FileDto fileDto;

    @BeforeEach
    void setUp() {
        fileDto = new FileDto(1L, "1", "file", null, "file.txt", "text/plain", 10L, 1000L);
    }

    @Test
    void shouldGetFile() throws Exception {
        // given
        GetFileRequest request = new GetFileRequest("1");
        when(fileService.get(request)).thenReturn(fileDto);

        // when / then
        mockMvc.perform(get("/file/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.kind").value("file"))
                .andExpect(jsonPath("$.name").value("file.txt"))
                .andExpect(jsonPath("$.mime").value("text/plain"))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.createdAt").value(1000));

        verify(fileService).get(request);
    }

    @Test
    void shouldReturnNotFoundWhenFileDoesNotExist() throws Exception {
        when(fileService.get(new GetFileRequest("999")))
                .thenThrow(new FileNotFoundInDbException("999"));

        mockMvc.perform(get("/file/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not found"))
                .andExpect(jsonPath("$.detail").value("No file with id: 999"));
    }

    @Test
    void shouldGetAllFiles() throws Exception {
        FileDto text = new FileDto(2L, "2", "text", "hello", null, null, 5L, 2000L);
        when(fileService.getAll()).thenReturn(List.of(fileDto, text));

        mockMvc.perform(get("/file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("1"))
                .andExpect(jsonPath("$[1].id").value("2"))
                .andExpect(jsonPath("$[1].kind").value("text"));

        verify(fileService).getAll();
    }

    @Test
    void shouldReturnEmptyListWhenNothingStored() throws Exception {
        when(fileService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/file"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void shouldGetFilesAfterSeq() throws Exception {
        when(fileService.findSince(5L)).thenReturn(List.of(fileDto));

        mockMvc.perform(get("/file").param("after", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("1"));

        verify(fileService).findSince(5L);
        verify(fileService, never()).getAll();
    }

    @Test
    void shouldRejectInvalidAfterParam() throws Exception {
        mockMvc.perform(get("/file").param("after", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'after'."));

        verifyNoInteractions(fileService);
    }

    @Test
    void shouldGetLatestFiles() throws Exception {
        when(fileService.findLatest(2)).thenReturn(List.of(fileDto));

        mockMvc.perform(get("/file").param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value("1"));

        verify(fileService).findLatest(2);
        verify(fileService, never()).getAll();
    }

    @Test
    void shouldPreferAfterOverLimit() throws Exception {
        when(fileService.findSince(5L)).thenReturn(List.of(fileDto));

        mockMvc.perform(get("/file")
                        .param("after", "5")
                        .param("limit", "2"))
                .andExpect(status().isOk());

        verify(fileService).findSince(5L);
        verify(fileService, never()).findLatest(anyInt());
    }

    @Test
    void shouldRejectInvalidLimitParam() throws Exception {
        mockMvc.perform(get("/file").param("limit", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"))
                .andExpect(jsonPath("$.detail").value("Invalid value for parameter 'limit'."));

        verifyNoInteractions(fileService);
    }

    @Test
    void shouldUpdateFile() throws Exception {
        UpdateFileRequest request = new UpdateFileRequest("1", "text", "updated", null, null, 7L);
        FileDto updated = new FileDto(1L, "1", "text", "updated", null, null, 7L, 1000L);
        when(fileService.update(request)).thenReturn(updated);

        mockMvc.perform(put("/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("1"))
                .andExpect(jsonPath("$.kind").value("text"))
                .andExpect(jsonPath("$.text").value("updated"))
                .andExpect(jsonPath("$.size").value(7));

        verify(fileService).update(request);
    }

    @Test
    void shouldRejectInvalidUpdate() throws Exception {
        UpdateFileRequest invalid = new UpdateFileRequest("", "", null, null, null, -1L);

        mockMvc.perform(put("/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.id").exists())
                .andExpect(jsonPath("$.errors.kind").exists())
                .andExpect(jsonPath("$.errors.size").exists());

        verifyNoInteractions(fileService);
    }

    @Test
    void shouldRejectMalformedJson() throws Exception {
        mockMvc.perform(put("/file")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad request"));
    }

    @Test
    void shouldDeleteFileAndItsBytes() throws Exception {
        mockMvc.perform(delete("/file/1"))
                .andExpect(status().isNoContent());

        verify(fileService).delete(new DeleteFileRequest("1"));
        verify(fileStorage).delete("1");
    }

    @Test
    void shouldReturnNoContentEvenWhenBytesCannotBeDeleted() throws Exception {
        doThrow(new IOException("disk on fire")).when(fileStorage).delete("1");

        mockMvc.perform(delete("/file/1"))
                .andExpect(status().isNoContent());

        verify(fileService).delete(new DeleteFileRequest("1"));
        verify(fileStorage).delete("1");
    }

    @Test
    void shouldNotTouchStorageWhenDeletingMissingFile() throws Exception {
        doThrow(new FileNotFoundInDbException("999"))
                .when(fileService).delete(new DeleteFileRequest("999"));

        mockMvc.perform(delete("/file/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("File not found"));

        verify(fileStorage, never()).delete("999");
    }

}
