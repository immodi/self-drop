package com.projects.selfdrop.services;

import com.projects.selfdrop.utils.HostAddressBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Holds the address this server currently advertises to clients.
 *
 * <p>The address is detected once at startup and then re-checked on a schedule,
 * so an IP change is picked up without a restart. Everything that shows or
 * shares the address (the footer and the {@code /pair} page) reads this stored
 * value instead of resolving it again. That keeps a single source of truth and
 * lets a future settings page update the advertised address in one place, with
 * the QR page picking the change up on its own.</p>
 */
@Service
public class AddressService {

    private static final Logger log = LoggerFactory.getLogger(AddressService.class);

    private final HostAddressBuilder hostAddressBuilder;
    private final ServerIdService serverIdService;

    private volatile Address current;

    public AddressService(HostAddressBuilder hostAddressBuilder, ServerIdService serverIdService) {
        this.hostAddressBuilder = hostAddressBuilder;
        this.serverIdService = serverIdService;
        this.current = detect();
    }

    /**
     * The address stored on the last detection. Never {@code null}; it falls
     * back to the configured host (usually {@code localhost}) when no LAN
     * interface can be found.
     */
    public Address current() {
        return current;
    }

    /**
     * The deep link a client scans or pastes to pair with this server.
     */
    
    public String pairUri() {
        Address address = current;
        return "selfdrop://pair?u=" + encode(address.url()) + "&id=" + encode(address.serverId());
    }

    /**
     * True when the stored URL only works on the server itself, so a QR code
     * built from it is useless to another device.
     */
    public boolean isLocalOnly() {
        return isLocalOnly(current.url());
    }

    @Scheduled(
            initialDelayString = "${address.refresh-millis:30000}",
            fixedDelayString = "${address.refresh-millis:30000}")
    public void refresh() {
        Address detected = detect();
        if (!detected.equals(current)) {
            current = detected;
            log.info("Advertised address is now {}", detected.url());
        }
    }

    private Address detect() {
        return new Address(hostAddressBuilder.build(), serverIdService.value());
    }

    static boolean isLocalOnly(String url) {
        if (url == null) {
            return true;
        }
        String lower = url.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://localhost")
                || lower.startsWith("https://localhost")
                || lower.startsWith("http://127.")
                || lower.startsWith("https://127.")
                || lower.startsWith("http://[::1]")
                || lower.startsWith("https://[::1]");
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Address(String url, String serverId) {
    }
}
