package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.CreateFileRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.utils.HostAddressBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UiController.class)
public class UiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IFileService fileService;

    @MockitoBean
    private HostAddressBuilder hostAddressBuilder;

    private static FileDto textItem(long seq, String text) {
        return new FileDto(seq, "id-" + seq, "text", text, null, null, (long) text.length(), 1000L + seq);
    }

    @Test
    void indexRendersNewestFirstWithLinkifiedText() throws Exception {
        when(fileService.getAll()).thenReturn(List.of(
                textItem(1, "older"),
                textItem(2, "newer https://example.com")));
        when(hostAddressBuilder.lanUrl()).thenReturn(Optional.of("http://192.168.1.104:8080"));

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Selfdrop")))
                .andExpect(content().string(containsString("data-since=\"2\"")))
                .andExpect(content().string(containsString("href=\"https://example.com\"")))
                .andExpect(content().string(containsString("http://192.168.1.104:8080")))
                .andExpect(content().string(containsString("/js/htmx.min.js")))
                .andExpect(content().string(containsString("/css/style.css")));

        String body = mockMvc.perform(get("/"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body.indexOf("data-copy=\"newer")).isLessThan(body.indexOf("data-copy=\"older"));
    }

    @Test
    void timelineRendersItemsFragment() throws Exception {
        when(fileService.getAll()).thenReturn(List.of(textItem(1, "hello")));

        mockMvc.perform(get("/ui/timeline"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"item-id-1\"")))
                .andExpect(content().string(containsString("hello")));
    }

    @Test
    void emptyTimelineShowsEmptyState() throws Exception {
        when(fileService.getAll()).thenReturn(List.of());

        mockMvc.perform(get("/ui/timeline"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nothing here yet")));
    }

    @Test
    void createTextReturnsItemFragment() throws Exception {
        when(fileService.create(any(CreateFileRequest.class))).thenAnswer(invocation -> {
            CreateFileRequest request = invocation.getArgument(0, CreateFileRequest.class);
            return new FileDto(3L, request.id(), request.kind(), request.text(),
                    request.name(), request.mime(), request.size(), 1000L);
        });

        mockMvc.perform(post("/ui/text")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", "hello https://example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-copy=\"hello https://example.com\"")))
                .andExpect(content().string(containsString("href=\"https://example.com\"")));

        ArgumentCaptor<CreateFileRequest> captor = ArgumentCaptor.forClass(CreateFileRequest.class);
        verify(fileService).create(captor.capture());

        CreateFileRequest request = captor.getValue();
        assertThat(request.id()).isNotBlank();
        assertThat(request.kind()).isEqualTo("text");
        assertThat(request.text()).isEqualTo("hello https://example.com");
        assertThat(request.size()).isEqualTo(25L);
    }

    @Test
    void blankTextIsRejected() throws Exception {
        mockMvc.perform(post("/ui/text")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("text", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.text").exists());
    }
}
