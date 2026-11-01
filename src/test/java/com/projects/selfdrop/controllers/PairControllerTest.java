package com.projects.selfdrop.controllers;

import com.projects.selfdrop.services.AddressService;
import com.projects.selfdrop.services.QRService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(PairController.class)
class PairControllerTest {

    private static final String PAIR_URI =
            "selfdrop://pair?u=http%3A%2F%2F192.168.1.50%3A8080&id=abc123";
    private static final String SERVER_URL = "http://192.168.1.50:8080";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AddressService addressService;

    @MockitoBean
    private QRService qrService;

    private void stubAddress(boolean localOnly) throws Exception {
        when(addressService.current())
                .thenReturn(new AddressService.Address(SERVER_URL, "abc123"));
        when(addressService.pairUri()).thenReturn(PAIR_URI);
        when(addressService.isLocalOnly()).thenReturn(localOnly);
        when(qrService.generate(anyString()))
                .thenReturn("cGFpci1xcg==", "c2VydmVyLXFy");
    }

    @Test
    void pairPageShowsBothQrCodesBehindASelector() throws Exception {
        stubAddress(false);

        mockMvc.perform(get("/pair"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Pair a device")))
                .andExpect(content().string(containsString("id=\"qr-mode-pair\"")))
                .andExpect(content().string(containsString("id=\"qr-mode-server\"")))
                .andExpect(content().string(containsString("data:image/png;base64,cGFpci1xcg==")))
                .andExpect(content().string(containsString("data:image/png;base64,c2VydmVyLXFy")))
                .andExpect(content().string(containsString("selfdrop://pair")))
                .andExpect(content().string(containsString("u=http%3A%2F%2F192.168.1.50%3A8080")))
                .andExpect(content().string(containsString(SERVER_URL)))
                .andExpect(content().string(containsString("hx-get=\"/pair/codes\"")))
                .andExpect(content().string(containsString("hx-trigger=\"every 10s\"")));

        verify(qrService).generate(PAIR_URI);
        verify(qrService).generate(SERVER_URL);
    }

    @Test
    void codesFragmentCanBeFetchedOnItsOwn() throws Exception {
        stubAddress(false);

        mockMvc.perform(get("/pair/codes"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("data-qr-panel=\"pair\"")))
                .andExpect(content().string(containsString("data-qr-panel=\"server\"")))
                .andExpect(content().string(containsString("data:image/png;base64,cGFpci1xcg==")))
                .andExpect(content().string(containsString("data:image/png;base64,c2VydmVyLXFy")));
    }

    @Test
    void warnsWhenTheAddressOnlyWorksOnTheServer() throws Exception {
        stubAddress(true);

        mockMvc.perform(get("/pair"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No LAN address was detected")));
    }
}
