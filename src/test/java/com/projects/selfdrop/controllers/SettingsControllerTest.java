package com.projects.selfdrop.controllers;

import com.projects.selfdrop.services.AddressService;
import com.projects.selfdrop.services.WorkerPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(SettingsController.class)
class SettingsControllerTest {

    private static final String CURRENT_URL = "http://192.168.1.50:8080";
    private static final String SERVER_ID = "abc123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private WorkerPublisher workerPublisher;

    @BeforeEach
    void setUp() {
        when(addressService.current())
                .thenReturn(new AddressService.Address(CURRENT_URL, SERVER_ID));
        when(addressService.isLocalOnly()).thenReturn(false);
        when(addressService.override(any(AddressService.Address.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void showsTheCurrentlyAdvertisedAddress() throws Exception {
        mockMvc.perform(get("/settings/address"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.url").value(CURRENT_URL))
                .andExpect(jsonPath("$.serverId").value(SERVER_ID))
                .andExpect(jsonPath("$.localOnly").value(false));

        verify(addressService).current();
    }

    @Test
    void overridesTheAddressAndPublishesIt() throws Exception {
        mockMvc.perform(post("/settings/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"http://192.168.1.51:8080\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.url").value("http://192.168.1.51:8080"))
                .andExpect(jsonPath("$.serverId").value(SERVER_ID));

        ArgumentCaptor<AddressService.Address> captor =
                ArgumentCaptor.forClass(AddressService.Address.class);
        verify(addressService).override(captor.capture());
        assertThat(captor.getValue())
                .isEqualTo(new AddressService.Address("http://192.168.1.51:8080", SERVER_ID));
        verify(workerPublisher).publishIfChanged();
    }

    @Test
    void trimsWhitespaceAroundTheAddress() throws Exception {
        mockMvc.perform(post("/settings/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"  http://10.0.0.2:8080  \"}"))
                .andExpect(status().isOk());

        verify(addressService).override(
                new AddressService.Address("http://10.0.0.2:8080", SERVER_ID));
    }

    @Test
    void rejectsAddressesTheWorkerWouldRefuse() throws Exception {
        for (String url : new String[]{
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://8.8.8.8:8080",
                "https://192.168.1.51:8080",
                "http://192.168.1.51:8080/path"}) {
            mockMvc.perform(post("/settings/address")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"url\":\"" + url + "\"}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.title").value("Invalid address"));
        }

        verify(addressService, never()).override(any(AddressService.Address.class));
        verify(workerPublisher, never()).publishIfChanged();
    }

    @Test
    void rejectsABlankAddress() throws Exception {
        mockMvc.perform(post("/settings/address")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation failed"))
                .andExpect(jsonPath("$.errors.url").exists());

        verify(addressService, never()).override(any(AddressService.Address.class));
    }

    @Test
    void onlyAnswersRequestsFromTheServerItself() throws Exception {
        mockMvc.perform(withRemoteAddress(get("/settings/address"), "192.168.1.20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        mockMvc.perform(withRemoteAddress(post("/settings/address")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"url\":\"http://192.168.1.51:8080\"}"),
                        "192.168.1.20"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Forbidden"));

        verify(addressService, never()).override(any(AddressService.Address.class));
        verify(workerPublisher, never()).publishIfChanged();
    }

    private static MockHttpServletRequestBuilder withRemoteAddress(
            MockHttpServletRequestBuilder request, String remoteAddress) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(remoteAddress);
            return servletRequest;
        });
    }
}
