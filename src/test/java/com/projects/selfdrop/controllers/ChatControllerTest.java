package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.requests.CreateChatRequest;
import com.projects.selfdrop.dtos.requests.DeleteChatRequest;
import com.projects.selfdrop.dtos.requests.GetChatRequest;
import com.projects.selfdrop.dtos.requests.UpdateChatRequest;
import com.projects.selfdrop.dtos.responses.ChatDto;
import com.projects.selfdrop.exceptions.ChatNotFoundInDbException;
import com.projects.selfdrop.interfaces.IChatService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(ChatController.class)
public class ChatControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private IChatService chatService;

    private ChatDto chatDto;

    @BeforeEach
    void setUp() {
        chatDto = new ChatDto(1L, "chat-1", "General", 1000L);
    }

    @Test
    void shouldListChats() throws Exception {
        when(chatService.getAll()).thenReturn(List.of(
                chatDto, new ChatDto(2L, "chat-2", "Work", 2000L)));

        mockMvc.perform(get("/chat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value("chat-1"))
                .andExpect(jsonPath("$[0].name").value("General"))
                .andExpect(jsonPath("$[1].name").value("Work"));

        verify(chatService).getAll();
    }

    @Test
    void shouldGetOneChat() throws Exception {
        when(chatService.get(new GetChatRequest("chat-1"))).thenReturn(chatDto);

        mockMvc.perform(get("/chat/chat-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("chat-1"))
                .andExpect(jsonPath("$.name").value("General"))
                .andExpect(jsonPath("$.createdAt").value(1000));

        verify(chatService).get(new GetChatRequest("chat-1"));
    }

    @Test
    void shouldReturnNotFoundForMissingChat() throws Exception {
        when(chatService.get(new GetChatRequest("missing")))
                .thenThrow(new ChatNotFoundInDbException("missing"));

        mockMvc.perform(get("/chat/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Chat not found"))
                .andExpect(jsonPath("$.detail").value("No chat with id: missing"));
    }

    @Test
    void shouldCreateChat() throws Exception {
        CreateChatRequest request = new CreateChatRequest("Work");
        ChatDto created = new ChatDto(2L, "chat-2", "Work", 2000L);
        when(chatService.create(request)).thenReturn(created);

        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/chat/chat-2"))
                .andExpect(jsonPath("$.id").value("chat-2"))
                .andExpect(jsonPath("$.name").value("Work"));

        verify(chatService).create(request);
    }

    @Test
    void shouldRejectBlankChatName() throws Exception {
        mockMvc.perform(post("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CreateChatRequest(" "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists());

        verifyNoInteractions(chatService);
    }

    @Test
    void shouldUpdateChat() throws Exception {
        UpdateChatRequest request = new UpdateChatRequest("chat-1", "Renamed");
        ChatDto updated = new ChatDto(1L, "chat-1", "Renamed", 1000L);
        when(chatService.update(request)).thenReturn(updated);

        mockMvc.perform(put("/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("chat-1"))
                .andExpect(jsonPath("$.name").value("Renamed"));

        verify(chatService).update(request);
    }

    @Test
    void shouldDeleteChat() throws Exception {
        mockMvc.perform(delete("/chat/chat-1"))
                .andExpect(status().isNoContent());

        verify(chatService).delete(new DeleteChatRequest("chat-1"));
    }
}
