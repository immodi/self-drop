package com.projects.selfdrop.interfaces;


import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

public interface IBroadcaster<T> {
    /**
     * Registers a new connection. The id must be unique per HTTP connection,
     * not per user session, so that multiple tabs and devices all receive events.
     */
    void register(String connectionId, SseEmitter emitter);

    /**
     * Sends a backlog of already-stored events to a freshly connected client and
     * then flushes anything that arrived while the backlog was being written.
     *
     * @param watermark highest {@code seq} contained in the backlog
     */
    void replay(String connectionId, List<T> backlog, long watermark);

    void broadcast(T message);

    /**
     * Sends a heartbeat for one connection.
     *
     * @return {@code false} when the connection is gone and should be cleaned up
     */
    boolean heartbeat(String connectionId);

    void remove(String connectionId);

    List<SseEmitter> getEmitters();
}
