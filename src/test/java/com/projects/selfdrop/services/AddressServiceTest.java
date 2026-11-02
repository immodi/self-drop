package com.projects.selfdrop.services;

import com.projects.selfdrop.utils.HostAddressBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AddressServiceTest {

    private static ServerIdService serverId(String value) {
        ServerIdService serverIdService = mock(ServerIdService.class);
        when(serverIdService.value()).thenReturn(value);
        return serverIdService;
    }

    @Test
    void storesTheDetectedAddressAndServerId() {
        HostAddressBuilder builder = mock(HostAddressBuilder.class);
        when(builder.build()).thenReturn("http://192.168.1.50:8080");

        AddressService service = new AddressService(builder, serverId("abc123"));

        assertThat(service.current())
                .isEqualTo(new AddressService.Address("http://192.168.1.50:8080", "abc123"));
        assertThat(service.pairUri())
                .isEqualTo("selfdrop://pair?u=http%3A%2F%2F192.168.1.50%3A8080&id=abc123");
        assertThat(service.isLocalOnly()).isFalse();
    }

    @Test
    void refreshPicksUpANewDetectedAddress() {
        HostAddressBuilder builder = mock(HostAddressBuilder.class);
        when(builder.build())
                .thenReturn("http://192.168.1.50:8080")
                .thenReturn("http://192.168.1.51:8080");

        AddressService service = new AddressService(builder, serverId("abc123"));
        service.refresh();

        assertThat(service.current().url()).isEqualTo("http://192.168.1.51:8080");
        assertThat(service.pairUri()).contains("192.168.1.51");
    }

    @Test
    void flagsLoopbackAddressesAsLocalOnly() {
        HostAddressBuilder builder = mock(HostAddressBuilder.class);
        when(builder.build()).thenReturn("http://localhost:8080");

        AddressService service = new AddressService(builder, serverId("abc123"));

        assertThat(service.isLocalOnly()).isTrue();
    }

    @Test
    void overrideReplacesTheDetectedAddressAndSurvivesARefresh() {
        HostAddressBuilder builder = mock(HostAddressBuilder.class);
        when(builder.build())
                .thenReturn("http://192.168.1.50:8080")
                .thenReturn("http://192.168.1.99:8080");

        AddressService service = new AddressService(builder, serverId("abc123"));
        service.override(new AddressService.Address("http://192.168.1.60:8080", "abc123"));
        service.refresh();

        assertThat(service.current().url()).isEqualTo("http://192.168.1.60:8080");
    }

    @Test
    void overrideRejectsLoopbackAndPublicAddresses() {
        HostAddressBuilder builder = mock(HostAddressBuilder.class);
        when(builder.build()).thenReturn("http://192.168.1.50:8080");

        AddressService service = new AddressService(builder, serverId("abc123"));

        for (String url : new String[]{
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://8.8.8.8:8080",
                "https://192.168.1.60:8080"}) {
            assertThat(service.override(new AddressService.Address(url, "abc123")).url())
                    .as(url)
                    .isEqualTo("http://192.168.1.50:8080");
        }
    }

    @Test
    void detectsLoopbackVariants() {
        assertThat(AddressService.isLocalOnly("http://localhost:8080")).isTrue();
        assertThat(AddressService.isLocalOnly("http://127.0.0.1:8080")).isTrue();
        assertThat(AddressService.isLocalOnly("http://[::1]:8080")).isTrue();
        assertThat(AddressService.isLocalOnly("http://192.168.1.50:8080")).isFalse();
        assertThat(AddressService.isLocalOnly("http://selfdrop.local:8080")).isFalse();
        assertThat(AddressService.isLocalOnly(null)).isTrue();
    }
}
