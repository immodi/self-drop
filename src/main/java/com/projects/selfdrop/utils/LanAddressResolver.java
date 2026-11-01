package com.projects.selfdrop.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Finds the machine's current LAN IPv4 address.
 *
 * <p>This works both on the host and in a container using host networking,
 * because both expose the host's network interfaces. A bridge-networked
 * container only sees its own interface, so it cannot discover the host's LAN
 * address.</p>
 */
@Component
public class LanAddressResolver {

    private static final Logger log = LoggerFactory.getLogger(LanAddressResolver.class);
    private static final Duration CACHE_TTL = Duration.ofSeconds(30);
    private static final String[] VIRTUAL_PREFIXES = {
            "docker", "br-", "veth", "virbr", "vmnet", "vboxnet",
            "tun", "tap", "tailscale", "wg", "zt", "cni", "flannel", "podman"
    };
    private static final String[] PREFERRED_PREFIXES = {
            "wlan", "wlp", "wl", "eth", "en", "eno", "enp", "ens"
    };

    private volatile CachedAddress cached = new CachedAddress(Optional.empty(), Instant.EPOCH);

    /**
     * Returns the current LAN address, or an empty optional when no usable
     * interface is available.
     */
    public Optional<String> resolve() {
        Instant now = Instant.now();
        CachedAddress current = cached;
        if (now.isBefore(current.expiresAt())) {
            return current.address();
        }

        Optional<String> detected = detect();
        cached = new CachedAddress(detected, now.plus(CACHE_TTL));
        if (detected.isPresent() && !detected.equals(current.address())) {
            log.info("Detected LAN address: {}", detected.get());
        }
        return detected;
    }

    private Optional<String> detect() {
        List<Candidate> candidates = candidates();

        // Prefer the interface the OS uses for outbound traffic, but only when
        // it is also a usable LAN interface. This avoids picking a VPN address.
        Optional<String> outbound = outboundAddress();
        if (outbound.isPresent() && candidates.stream()
                .anyMatch(candidate -> candidate.address().equals(outbound.get()))) {
            return outbound;
        }

        return candidates.stream()
                .max(Comparator.comparingInt(Candidate::score)
                        .thenComparing(Candidate::interfaceName)
                        .thenComparing(Candidate::address))
                .map(Candidate::address);
    }

    private List<Candidate> candidates() {
        List<Candidate> result = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return result;
            }

            for (NetworkInterface networkInterface : Collections.list(interfaces)) {
                if (!isCandidateInterface(networkInterface)) {
                    continue;
                }
                for (InterfaceAddress interfaceAddress : networkInterface.getInterfaceAddresses()) {
                    InetAddress address = interfaceAddress.getAddress();
                    if (!(address instanceof Inet4Address)) {
                        continue;
                    }
                    String hostAddress = address.getHostAddress();
                    if (!isUsableIpv4(hostAddress)) {
                        continue;
                    }
                    result.add(new Candidate(
                            networkInterface.getName(),
                            hostAddress,
                            score(networkInterface.getName(), hostAddress)));
                }
            }
        } catch (SocketException e) {
            log.debug("Could not enumerate network interfaces: {}", e.getMessage());
        }
        return result;
    }

    private static boolean isCandidateInterface(NetworkInterface networkInterface) throws SocketException {
        return networkInterface.isUp()
                && !networkInterface.isLoopback()
                && !networkInterface.isVirtual()
                && !networkInterface.isPointToPoint()
                && !isVirtualInterface(networkInterface.getName());
    }

    private Optional<String> outboundAddress() {
        try (DatagramSocket socket = new DatagramSocket()) {
            // Connecting a UDP socket selects a route but does not send a packet.
            socket.connect(InetAddress.getByName("8.8.8.8"), 53);
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address && isUsableIpv4(local.getHostAddress())) {
                return Optional.of(local.getHostAddress());
            }
        } catch (Exception e) {
            log.debug("Could not determine the outbound address: {}", e.getMessage());
        }
        return Optional.empty();
    }

    static boolean isUsableIpv4(String address) {
        return address != null
                && !address.isBlank()
                && !address.startsWith("0.")
                && !address.startsWith("127.")
                && !address.startsWith("169.254.")
                && !address.equals("255.255.255.255");
    }

    static boolean isPrivateIpv4(String address) {
        if (address == null) {
            return false;
        }
        if (address.startsWith("10.") || address.startsWith("192.168.")) {
            return true;
        }
        if (!address.startsWith("172.")) {
            return false;
        }

        String[] parts = address.split("\\.");
        if (parts.length < 2) {
            return false;
        }
        try {
            int second = Integer.parseInt(parts[1]);
            return second >= 16 && second <= 31;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static boolean isVirtualInterface(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : VIRTUAL_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static boolean isPreferredInterface(String name) {
        if (name == null) {
            return false;
        }
        String lower = name.toLowerCase(Locale.ROOT);
        for (String prefix : PREFERRED_PREFIXES) {
            if (lower.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    static int score(String interfaceName, String address) {
        int score = isPrivateIpv4(address) ? 100 : 0;
        if (isPreferredInterface(interfaceName)) {
            score += 20;
        }
        if (address != null && address.startsWith("192.168.")) {
            score += 5;
        }
        return score;
    }

    private record Candidate(String interfaceName, String address, int score) {
    }

    private record CachedAddress(Optional<String> address, Instant expiresAt) {
    }
}
