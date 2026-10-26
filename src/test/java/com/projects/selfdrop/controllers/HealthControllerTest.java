package com.projects.selfdrop.controllers;

import com.projects.selfdrop.services.ServerIdService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(HealthController.class)
public class HealthControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ServerIdService serverIdService;

    @Test
    void shouldReturnPersistedServerId() throws Exception {
        when(serverIdService.value()).thenReturn("server-123");

        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.id").value("server-123"));

        verify(serverIdService).value();
    }

    @Test
    void shouldReturnSameServerIdOnEveryCall() throws Exception {
        when(serverIdService.value()).thenReturn("server-123");

        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("server-123"));
        mockMvc.perform(get("/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("server-123"));

        verify(serverIdService, times(2)).value();
    }
}
