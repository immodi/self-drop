package com.projects.selfdrop.controllers;

import com.projects.selfdrop.services.AddressService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(SettingsUiController.class)
class SettingsUiControllerTest {

    private static final String CURRENT_URL = "http://192.168.1.50:8080";
    private static final String SERVER_ID = "abc123";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @BeforeEach
    void setUp() {
        when(addressService.current())
                .thenReturn(new AddressService.Address(CURRENT_URL, SERVER_ID));
        when(addressService.isLocalOnly()).thenReturn(false);
    }

    @Test
    void rendersTheCurrentAddressAndSettingsForm() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Server settings")))
                .andExpect(content().string(containsString(CURRENT_URL)))
                .andExpect(content().string(containsString(SERVER_ID)))
                .andExpect(content().string(containsString("id=\"settings-form\"")))
                .andExpect(content().string(containsString("action=\"/settings/address\"")))
                .andExpect(content().string(not(containsString("Only available on the server"))));
    }

    @Test
    void warnsWhenNoLanAddressWasDetected() throws Exception {
        when(addressService.isLocalOnly()).thenReturn(true);

        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No LAN address was detected")));
    }

    @Test
    void refusesToRenderSettingsOutsideTheServer() throws Exception {
        mockMvc.perform(withRemoteAddress(get("/settings"), "192.168.1.20"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Only available on the server")))
                .andExpect(content().string(not(containsString("id=\"settings-form\""))));
    }

    private static MockHttpServletRequestBuilder withRemoteAddress(
            MockHttpServletRequestBuilder request, String remoteAddress) {
        return request.with(servletRequest -> {
            servletRequest.setRemoteAddr(remoteAddress);
            return servletRequest;
        });
    }
}
