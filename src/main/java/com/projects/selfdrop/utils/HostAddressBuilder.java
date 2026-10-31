package com.projects.selfdrop.utils;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class HostAddressBuilder {
    private final String address;
    private final int port;

    public HostAddressBuilder(@Value("${server.address:localhost}") String address, @Value("${server.port:8080}") int port) {
        this.address = address;
        this.port = port;
    }

    public String build() {
        return "http://" + address + ":" + port;
    }
}
