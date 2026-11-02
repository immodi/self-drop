package com.projects.selfdrop.services;

import tools.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WorkerPublisherTest {

    @TempDir
    Path tempDir;

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final List<String> authorizations = new CopyOnWriteArrayList<>();
    private final List<String> bodies = new CopyOnWriteArrayList<>();

    @BeforeEach
    void startServer() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/r", exchange -> {
            requests.incrementAndGet();
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            bodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"changed\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String workerUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/r";
    }

    private AddressService addressService(String url, String id) {
        AddressService addressService = mock(AddressService.class);
        when(addressService.current()).thenReturn(new AddressService.Address(url, id));
        return addressService;
    }

    private WorkerPublisher publisher(AddressService addressService) {
        return new WorkerPublisher(
                addressService,
                new ObjectMapper(),
                workerUrl(),
                "secret-token",
                tempDir.resolve("worker-state.json"));
    }

    @Test
    void publishesTheAddressWithTheBearerToken() throws Exception {
        WorkerPublisher publisher = publisher(addressService("http://192.168.1.50:8080", "abc123"));

        assertThat(publisher.publishIfChanged()).isTrue();

        assertThat(requests).hasValue(1);
        assertThat(authorizations).containsExactly("Bearer secret-token");
        assertThat(bodies.getFirst()).contains("\"url\":\"http://192.168.1.50:8080\"")
                .contains("\"id\":\"abc123\"");
        assertThat(Files.readString(tempDir.resolve("worker-state.json")))
                .contains("http://192.168.1.50:8080");
    }

    @Test
    void doesNotPublishTheSameAddressTwice() {
        WorkerPublisher publisher = publisher(addressService("http://192.168.1.50:8080", "abc123"));

        assertThat(publisher.publishIfChanged()).isTrue();
        assertThat(publisher.publishIfChanged()).isFalse();

        assertThat(requests).hasValue(1);
    }

    @Test
    void republishesAfterTheAddressChanges() {
        AddressService addressService = mock(AddressService.class);
        when(addressService.current())
                .thenReturn(new AddressService.Address("http://192.168.1.50:8080", "abc123"))
                .thenReturn(new AddressService.Address("http://192.168.1.51:8080", "abc123"));

        WorkerPublisher publisher = publisher(addressService);

        assertThat(publisher.publishIfChanged()).isTrue();
        assertThat(publisher.publishIfChanged()).isTrue();

        assertThat(requests).hasValue(2);
    }

    @Test
    void skipsLocalAndPublicAddresses() {
        for (String url : List.of(
                "http://localhost:8080",
                "http://127.0.0.1:8080",
                "http://8.8.8.8:8080",
                "http://192.168.1.50.example:8080")) {
            WorkerPublisher publisher = publisher(addressService(url, "abc123"));
            assertThat(publisher.publishIfChanged()).as(url).isFalse();
        }

        assertThat(requests).hasValue(0);
    }

    @Test
    void doesNothingWhenTheWorkerIsNotConfigured() {
        WorkerPublisher publisher = new WorkerPublisher(
                addressService("http://192.168.1.50:8080", "abc123"),
                new ObjectMapper(),
                "",
                "",
                tempDir.resolve("worker-state.json"));

        assertThat(publisher.isEnabled()).isFalse();
        assertThat(publisher.publishIfChanged()).isFalse();
        assertThat(requests).hasValue(0);
    }

    @Test
    void buildsTheEndpointFromABaseUrlOrAnExplicitPath() {
        assertThat(WorkerPublisher.endpoint("https://example.workers.dev"))
                .isEqualTo("https://example.workers.dev/r");
        assertThat(WorkerPublisher.endpoint("https://example.workers.dev/"))
                .isEqualTo("https://example.workers.dev/r");
        assertThat(WorkerPublisher.endpoint("https://example.workers.dev/r"))
                .isEqualTo("https://example.workers.dev/r");
        assertThat(WorkerPublisher.endpoint("")).isEmpty();
    }

    @Test
    void onlyAcceptsPrivateIpv4HttpUrls() {
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://10.0.0.2:8080")).isTrue();
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://172.16.0.2:8080")).isTrue();
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://192.168.0.10:8080")).isTrue();

        assertThat(WorkerPublisher.isPrivateHttpUrl("https://192.168.0.10:8080")).isFalse();
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://192.168.0.10")).isFalse();
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://127.0.0.1:8080")).isFalse();
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://192.168.0.10:8080/path")).isFalse();
        assertThat(WorkerPublisher.isPrivateHttpUrl("http://192.168.0.10.example:8080")).isFalse();
    }
}
