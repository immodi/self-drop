package com.projects.selfdrop.utils;

import jakarta.servlet.http.HttpServletRequest;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Identifies requests that came from the machine running the server.
 *
 * <p>Settings can change where pairing clients are sent, so they are only
 * available locally.</p>
 */
public final class LoopbackRequests {

    private LoopbackRequests() {
    }

    public static boolean isLoopback(HttpServletRequest request) {
        String remoteAddress = request.getRemoteAddr();
        if (remoteAddress == null || remoteAddress.isBlank()) {
            return true;
        }
        try {
            return InetAddress.getByName(remoteAddress).isLoopbackAddress();
        } catch (UnknownHostException e) {
            return true;
        }
    }
}
