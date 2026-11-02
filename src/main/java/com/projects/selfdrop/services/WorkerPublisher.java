package com.projects.selfdrop.services;

import com.projects.selfdrop.utils.LanAddressResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Map;

/**
 * Publishes the advertised LAN address and server id to the rendezvous Worker.
 *
 * <p>The Worker record is what lets clients find this server again after its
 * IP changes. Publication is opt-in: it only runs when both {@code worker.url}
 * and {@code worker.token} are configured. The token stays in the backend
 * environment and is never exposed to the browser.</p>
 *
 * <p>The last successfully published values are kept in a small state file, so
 * a restart does not rewrite an unchanged record. The Worker also skips
 * unchanged writes, which keeps KV usage well inside the free tier.</p>
 */
@Service
public class WorkerPublisher {

    private static final Logger log = LoggerFactory.getLogger(WorkerPublisher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(5);
    private static final String DOTTED_QUAD = "\\d{1,3}(?:\\.\\d{1,3}){3}";

    private final AddressService addressService;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String token;
    private final Path statePath;
    private final HttpClient httpClient;

    private volatile Record published;
    private volatile String lastSkipped;

    public WorkerPublisher(
            AddressService addressService,
            ObjectMapper objectMapper,
            @Value("${worker.url:}") String workerUrl,
            @Value("${worker.token:}") String workerToken,
            @Value("${worker.state-path:./data/worker-state.json}") Path statePath) {
        this.addressService = addressService;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint(workerUrl);
        this.token = workerToken == null ? "" : workerToken.strip();
        this.statePath = statePath;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(TIMEOUT)
                .build();
        this.published = readState();

        if (isEnabled()) {
            log.info("Worker publisher enabled: {}", endpoint);
        } else if (!workerUrl.isBlank() && token.isEmpty()) {
            log.warn("Worker publishing disabled: worker.url is set but worker.token is empty");
        }
    }

    /**
     * Re-checks the advertised address and publishes it when it differs from
     * the last successful write. A no-op when the Worker is not configured or
     * when no usable LAN address is available.
     *
     * @return true when the Worker accepted a write
     */
    public synchronized boolean publishIfChanged() {
        if (!isEnabled()) {
            return false;
        }

        AddressService.Address address = addressService.current();
        if (!isPrivateHttpUrl(address.url())) {
            skip(address.url(), "address is not a private http://<IPv4>:<port> URL");
            return false;
        }

        Record desired = new Record(address.url(), address.serverId());
        if (desired.equals(published)) {
            return false;
        }

        if (send(desired)) {
            published = desired;
            writeState(desired);
            lastSkipped = null;
            log.info("Published {} to the rendezvous Worker", desired.url());
            return true;
        }
        return false;
    }

    @Scheduled(
            initialDelayString = "${worker.publish-initial-delay-millis:3000}",
            fixedDelayString = "${worker.publish-interval-millis:30000}")
    public void scheduledPublish() {
        try {
            publishIfChanged();
        } catch (RuntimeException e) {
            log.warn("Worker publication failed: {}", e.getMessage());
        }
    }

    boolean isEnabled() {
        return !endpoint.isEmpty() && !token.isEmpty();
    }

    private boolean send(Record record) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "url", record.url(),
                    "id", record.id()));
            HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                log.warn("Worker rejected the record ({}): {}", response.statusCode(), response.body());
                return false;
            }
            return true;
        } catch (IOException e) {
            log.warn("Could not reach the rendezvous Worker at {}: {}", endpoint, e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Worker publication was interrupted");
            return false;
        } catch (RuntimeException e) {
            log.warn("Worker publication failed: {}", e.getMessage());
            return false;
        }
    }

    private void skip(String url, String reason) {
        if (!url.equals(lastSkipped)) {
            log.info("Not publishing {}: {}", url, reason);
            lastSkipped = url;
        }
    }

    private Record readState() {
        if (statePath == null || !Files.isRegularFile(statePath)) {
            return null;
        }
        try {
            return objectMapper.readValue(statePath.toFile(), Record.class);
        } catch (RuntimeException e) {
            log.warn("Could not read the Worker state file {}: {}", statePath, e.getMessage());
            return null;
        }
    }

    private void writeState(Record record) {
        if (statePath == null) {
            return;
        }
        Path parent = statePath.toAbsolutePath().getParent();
        Path temp = null;
        try {
            Files.createDirectories(parent);
            temp = Files.createTempFile(parent, ".worker-state-", ".tmp");
            objectMapper.writeValue(temp.toFile(), record);
            Files.move(temp, statePath,
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException | RuntimeException e) {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // best effort
                }
            }
            log.warn("Could not save the Worker state file {}: {}", statePath, e.getMessage());
        }
    }

    static String endpoint(String workerUrl) {
        if (workerUrl == null || workerUrl.isBlank()) {
            return "";
        }
        String trimmed = workerUrl.strip();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.endsWith("/r") ? trimmed : trimmed + "/r";
    }

    /**
     * Mirrors the Worker's rule: plain HTTP, an explicit port, and a private
     * IPv4 literal, with no path. Anything else is not worth sending, and the
     * Worker would reject it anyway.
     */
    public static boolean isPrivateHttpUrl(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            if (!"http".equalsIgnoreCase(uri.getScheme())) {
                return false;
            }
            if (uri.getUserInfo() != null || uri.getRawQuery() != null || uri.getRawFragment() != null) {
                return false;
            }
            String path = uri.getPath();
            if (path != null && !path.isEmpty() && !path.equals("/")) {
                return false;
            }
            if (uri.getPort() < 1 || uri.getPort() > 65535) {
                return false;
            }
            String host = uri.getHost();
            return host != null
                    && host.matches(DOTTED_QUAD)
                    && LanAddressResolver.isPrivateIpv4(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    record Record(String url, String id) {
    }
}
