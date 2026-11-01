package com.projects.selfdrop.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class HostAddressBuilder {
    private final LanAddressResolver lanAddressResolver;
    private final String address;
    private final int port;

    public HostAddressBuilder(LanAddressResolver lanAddressResolver,
                              @Value("${server.address:0.0.0.0}") String address,
                              @Value("${server.port:8080}") int port) {
        this.lanAddressResolver = lanAddressResolver;
        this.address = address;
        this.port = port;
    }

    /**
     * Returns a URL using the detected LAN address, or an empty optional when no
     * LAN interface could be found.
     */
    public Optional<String> lanUrl() {
        return lanAddressResolver.resolve().map(host -> "http://" + host + ":" + port);
    }

    public String build() {
        return lanUrl().orElseGet(() -> "http://" + configuredHost() + ":" + port);
    }

    private String configuredHost() {
        if (address == null || address.isBlank() || address.equals("0.0.0.0") || address.equals("::")) {
            return "localhost";
        }
        return address;
    }
}
