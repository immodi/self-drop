package com.projects.selfdrop.controllers;

import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.messaging.FileEvent;
import com.projects.selfdrop.interfaces.IBroadcaster;
import com.projects.selfdrop.interfaces.IFileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@RestController
@RequestMapping("/messages")
public class SseController {
    private static final Logger log = LoggerFactory.getLogger(SseController.class);
    private static final long HEARTBEAT_SECONDS = 10;

    private final ExecutorService executorService;
    private final ScheduledExecutorService heartbeatScheduler;
    private final IFileService fileService;
    private final IBroadcaster<FileEvent> broadcaster;

    public SseController(IFileService fileService,
                         IBroadcaster<FileEvent> broadcaster,
                         @Qualifier("sseExecutor") ExecutorService executorService,
                         @Qualifier("sseHeartbeat") ScheduledExecutorService heartbeatScheduler) {
        this.fileService = fileService;
        this.broadcaster = broadcaster;
        this.executorService = executorService;
        this.heartbeatScheduler = heartbeatScheduler;
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter handleSse(
            @RequestParam(value = "since", required = false) Long sinceParam,
            @RequestHeader(value = "Last-Event-ID", required = false) String lastEventIdHeader
    ) {
        long since = resolveSince(sinceParam, lastEventIdHeader);

        SseEmitter emitter = new SseEmitter(0L);
        String connectionId = UUID.randomUUID().toString();
        AtomicBoolean closed = new AtomicBoolean(false);
        AtomicReference<ScheduledFuture<?>> heartbeatRef = new AtomicReference<>();

        Runnable cleanup = () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            ScheduledFuture<?> heartbeat = heartbeatRef.get();
            if (heartbeat != null) {
                heartbeat.cancel(false);
            }
            broadcaster.remove(connectionId);
        };

        emitter.onCompletion(cleanup);
        emitter.onTimeout(() -> {
            log.warn("SSE timed out");
            cleanup.run();
        });
        emitter.onError(e -> {
            log.warn("SSE error: {}", e.getMessage());
            cleanup.run();
        });

        broadcaster.register(connectionId, emitter);
        if (closed.get()) {
            broadcaster.remove(connectionId);
            return emitter;
        }

        ScheduledFuture<?> heartbeat = heartbeatScheduler.scheduleAtFixedRate(() -> {
            if (closed.get()) {
                return;
            }
            if (!broadcaster.heartbeat(connectionId)) {
                log.warn("SSE heartbeat failed; closing connection {}", connectionId);
                cleanup.run();
            }
        }, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
        heartbeatRef.set(heartbeat);
        if (closed.get()) {
            heartbeat.cancel(false);
        }

        executorService.execute(() -> {
            try {
                List<FileDto> stored = fileService.findSince(since);
                long watermark = stored.stream()
                        .map(FileDto::seq)
                        .filter(Objects::nonNull)
                        .max(Long::compare)
                        .orElse(since);
                List<FileEvent> backlog = stored.stream()
                        .map(FileEvent::added)
                        .toList();
                broadcaster.replay(connectionId, backlog, watermark);
            } catch (Exception e) {
                log.error("SSE catch-up failed for connection {}", connectionId, e);
                cleanup.run();
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {
                    // The client may already be gone.
                }
            }
        });

        return emitter;
    }

    private long resolveSince(Long sinceParam, String lastEventIdHeader) {
        if (lastEventIdHeader != null && !lastEventIdHeader.isBlank()) {
            try {
                return Math.max(0, Long.parseLong(lastEventIdHeader));
            } catch (NumberFormatException ignored) {
                // Fall back to the query parameter.
            }
        }
        return sinceParam != null ? Math.max(0, sinceParam) : 0L;
    }
}
