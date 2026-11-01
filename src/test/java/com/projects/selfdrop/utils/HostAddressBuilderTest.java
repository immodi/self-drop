package com.projects.selfdrop.utils;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HostAddressBuilderTest {

    private static HostAddressBuilder builder(String configuredAddress, Optional<String> detected) {
        LanAddressResolver resolver = mock(LanAddressResolver.class);
        when(resolver.resolve()).thenReturn(detected);
        return new HostAddressBuilder(resolver, configuredAddress, 8080);
    }

    @Test
    void usesTheDetectedLanAddress() {
        HostAddressBuilder builder = builder("0.0.0.0", Optional.of("192.168.1.104"));

        assertThat(builder.lanUrl()).contains("http://192.168.1.104:8080");
        assertThat(builder.build()).isEqualTo("http://192.168.1.104:8080");
    }

    @Test
    void fallsBackToLocalhostWhenNoLanAddressIsDetected() {
        HostAddressBuilder builder = builder("0.0.0.0", Optional.empty());

        assertThat(builder.lanUrl()).isEmpty();
        assertThat(builder.build()).isEqualTo("http://localhost:8080");
    }

    @Test
    void keepsAnExplicitConfiguredHostAsTheFallback() {
        HostAddressBuilder builder = builder("selfdrop.local", Optional.empty());

        assertThat(builder.build()).isEqualTo("http://selfdrop.local:8080");
    }
}
