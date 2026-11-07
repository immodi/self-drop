package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.CreateChatRequest;
import com.projects.selfdrop.dtos.requests.CreateFileRequest;
import com.projects.selfdrop.dtos.requests.DeleteChatRequest;
import com.projects.selfdrop.dtos.requests.UpdateChatRequest;
import com.projects.selfdrop.dtos.responses.ChatDto;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.interfaces.IChatService;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.services.AddressService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(UiController.class)
public class UiControllerTest {
    private static final String CHAT_ID = "chat-1";
    private static final ChatDto CHAT = new ChatDto(1L, CHAT_ID, "General", 900L);

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IChatService chatService;

    @MockitoBean
    private IFileService fileService;

    @MockitoBean
    private AddressService addressService;

    private static FileDto textItem(long seq, String text) {
        return new FileDto(seq, "id-" + seq, CHAT_ID, "text", text, null, null, (long) text.length(), 1000L + seq);
    }

    @Test
    void indexRendersNewestFirstWithLinkifiedText() throws Exception {
        when(chatService.getAll()).thenReturn(List.of(CHAT));
        when(fileService.getAllByChat(CHAT_ID)).thenReturn(List.of(
                textItem(1, "older"),
                textItem(2, "newer https://example.com")));
        when(addressService.isLocalOnly()).thenReturn(false);
        when(addressService.current())
                .thenReturn(new AddressService.Address("http://192.168.1.104:8080", "server-id"));
        when(fileService.maxSeq()).thenReturn(2L);

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Selfdrop")))
                .andExpect(content().string(containsString("data-since=\"2\"")))
                .andExpect(content().string(containsString("href=\"https://example.com\"")))
                .andExpect(content().string(containsString("http://192.168.1.104:8080")))
                .andExpect(content().string(containsString("General")))
                .andExpect(content().string(containsString("/js/htmx.min.js")))
                .andExpect(content().string(containsString("/css/style.css")));

        String body = mockMvc.perform(get("/"))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(body.indexOf("data-copy=\"newer")).isLessThan(body.indexOf("data-copy=\"older"));
    }

    @Test
    void indexSelectsTheRequestedChat() throws Exception {
        when(addressService.isLocalOnly()).thenReturn(true);
        ChatDto other = new ChatDto(2L, "chat-2", "Work", 901L);
        when(chatService.getAll()).thenReturn(List.of(CHAT, other));
        when(fileService.getAllByChat("chat-2")).thenReturn(List.of());

        mockMvc.perform(get("/").param("chat", "chat-2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("chat-2")))
                .andExpect(content().string(containsString("hx-get=\"/ui/timeline?chatId=chat-2\"")));

        verify(fileService).getAllByChat("chat-2");
    }

    @Test
    void indexCreatesADefaultChatWhenNoneExist() throws Exception {
        when(addressService.isLocalOnly()).thenReturn(true);
        when(chatService.getAll()).thenReturn(List.of(), List.of(CHAT));
        when(chatService.ensureDefault()).thenReturn(CHAT);
        when(fileService.getAllByChat(CHAT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("General")));

        verify(chatService).ensureDefault();
    }

    @Test
    void timelineRendersItemsFragmentForTheChat() throws Exception {
        when(fileService.getAllByChat(CHAT_ID)).thenReturn(List.of(textItem(1, "hello")));

        mockMvc.perform(get("/ui/timeline").param("chatId", CHAT_ID))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("id=\"item-id-1\"")))
                .andExpect(content().string(containsString("hello")));

        verify(fileService).getAllByChat(CHAT_ID);
    }

    @Test
    void videoRendersNativePlayer() throws Exception {
        when(fileService.getAllByChat(CHAT_ID)).thenReturn(List.of(
                new FileDto(1L, "vid", CHAT_ID, "file", null, "clip.mp4", "video/mp4", 2048L, 1000L)));

        mockMvc.perform(get("/ui/timeline").param("chatId", CHAT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<video")))
                .andExpect(content().string(containsString("controls")))
                .andExpect(content().string(containsString("/preview/vid")))
                .andExpect(content().string(not(containsString("<img"))));
    }

    @Test
    void emptyTimelineShowsEmptyState() throws Exception {
        when(fileService.getAllByChat(CHAT_ID)).thenReturn(List.of());

        mockMvc.perform(get("/ui/timeline").param("chatId", CHAT_ID))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Nothing here yet")));
    }

    @Test
    void createTextReturnsItemFragmentInTheChat() throws Exception {
        when(fileService.create(any(CreateFileRequest.class))).thenAnswer(invocation -> {
            CreateFileRequest request = invocation.getArgument(0, CreateFileRequest.class);
            return new FileDto(3L, request.id(), request.chatId(), request.kind(), request.text(),
                    request.name(), request.mime(), request.size(), 1000L);
        });

        mockMvc.perform(post("/ui/text")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("chatId", CHAT_ID)
                        .param("text", "hello https://example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-copy=\"hello https://example.com\"")))
                .andExpect(content().string(containsString("href=\"https://example.com\"")));

        ArgumentCaptor<CreateFileRequest> captor = ArgumentCaptor.forClass(CreateFileRequest.class);
        verify(fileService).create(captor.capture());

        CreateFileRequest request = captor.getValue();
        assertThat(request.id()).isNotBlank();
        assertThat(request.chatId()).isEqualTo(CHAT_ID);
        assertThat(request.kind()).isEqualTo("text");
        assertThat(request.text()).isEqualTo("hello https://example.com");
        assertThat(request.size()).isEqualTo(25L);
    }

    @Test
    void blankTextIsRejected() throws Exception {
        mockMvc.perform(post("/ui/text")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("chatId", CHAT_ID)
                        .param("text", " "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.text").exists());
    }

    @Test
    void createChatRedirectsToTheNewChat() throws Exception {
        ChatDto created = new ChatDto(2L, "chat-2", "Work", 901L);
        when(chatService.create(new CreateChatRequest("Work"))).thenReturn(created);

        mockMvc.perform(post("/ui/chat")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "Work"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?chat=chat-2"));
    }

    @Test
    void createChatWithBlankNameReturnsToTheCurrentChat() throws Exception {
        mockMvc.perform(post("/ui/chat")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));
    }

    @Test
    void renameChatTrimsTheNameAndReturnsToTheChat() throws Exception {
        mockMvc.perform(post("/ui/chat/{id}/rename", CHAT_ID)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "  Renamed  "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?chat=chat-1"));

        verify(chatService).update(new UpdateChatRequest(CHAT_ID, "Renamed"));
    }

    @Test
    void renameChatWithBlankNameKeepsTheChat() throws Exception {
        mockMvc.perform(post("/ui/chat/{id}/rename", CHAT_ID)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "   "))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?chat=chat-1"));

        verify(chatService, never()).update(any());
    }

    @Test
    void renameChatWithTooLongNameKeepsTheChat() throws Exception {
        mockMvc.perform(post("/ui/chat/{id}/rename", CHAT_ID)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("name", "x".repeat(121)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/?chat=chat-1"));

        verify(chatService, never()).update(any());
    }

    @Test
    void deleteChatRedirectsHome() throws Exception {
        mockMvc.perform(post("/ui/chat/{id}/delete", "chat-1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"));

        verify(chatService).delete(new DeleteChatRequest("chat-1"));
    }
}
