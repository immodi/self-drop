package com.projects.selfdrop.messaging;

import com.projects.selfdrop.interfaces.IBroadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.locks.ReentrantLock;

@Component
public class FileEventBroadcaster implements IBroadcaster<FileEvent> {
    private static final Logger log = LoggerFactory.getLogger(FileEventBroadcaster.class);

    /**
     * One entry per HTTP connection, not per session: multiple tabs and devices
     * therefore each keep their own emitter.
     */
    private final Map<String, Client> clients = new ConcurrentHashMap<>();

    /**
     * A single thread keeps events in publication order. It also means an upload
     * never blocks on a slow SSE consumer.
     */
    private final ExecutorService broadcastExecutor;

    public FileEventBroadcaster(@Qualifier("sseBroadcast") ExecutorService broadcastExecutor) {
        this.broadcastExecutor = broadcastExecutor;
    }

    @EventListener
    public void onFileEvent(FileEvent event) {
        try {
            broadcastExecutor.execute(() -> broadcast(event));
        } catch (RejectedExecutionException e) {
            log.warn("Notification executor is shut down; dropping {} event for {}", event.type(), event.id());
        }
    }

    @Override
    public void register(String connectionId, SseEmitter emitter) {
        clients.put(connectionId, new Client(connectionId, emitter));
    }

    @Override
    public void replay(String connectionId, List<FileEvent> backlog, long watermark) {
        Client client = clients.get(connectionId);
        if (client == null) {
            return;
        }

        Throwable failure = null;
        client.lock.lock();
        try {
            if (client.closed) {
                return;
            }

            for (FileEvent event : backlog) {
                if (client.closed) {
                    break;
                }
                try {
                    if (!alreadySent(client, event)) {
                        send(client, event);
                    }
                } catch (Exception e) {
                    client.closed = true;
                    failure = e;
                    break;
                }
            }

            // Anything that arrived while the backlog was being written is
            // flushed now, in arrival order, after the backlog.
            client.ready = true;
            List<FileEvent> pending = new ArrayList<>(client.pending);
            client.pending.clear();

            if (failure == null) {
                for (FileEvent event : pending) {
                    if (client.closed) {
                        break;
                    }
                    try {
                        // An add at or below the watermark is already in the backlog.
                        if (isAlreadyInBacklog(event, watermark) || alreadySent(client, event)) {
                            continue;
                        }
                        send(client, event);
                    } catch (Exception e) {
                        client.closed = true;
                        failure = e;
                        break;
                    }
                }
            }
        } finally {
            client.lock.unlock();
        }

        if (failure != null) {
            close(client, failure);
        }
    }

    @Override
    public void broadcast(FileEvent event) {
        for (Client client : clients.values()) {
            Throwable failure = null;
            client.lock.lock();
            try {
                if (client.closed) {
                    continue;
                }
                if (!client.ready) {
                    client.pending.add(event);
                    continue;
                }
                if (alreadySent(client, event)) {
                    continue;
                }
                send(client, event);
            } catch (Exception e) {
                client.closed = true;
                failure = e;
            } finally {
                client.lock.unlock();
            }

            if (failure != null) {
                close(client, failure);
            }
        }
    }

    @Override
    public boolean heartbeat(String connectionId) {
        Client client = clients.get(connectionId);
        if (client == null) {
            return false;
        }

        Throwable failure = null;
        client.lock.lock();
        try {
            if (client.closed) {
                return false;
            }
            client.emitter.send(SseEmitter.event().name("heartbeat").data(""));
        } catch (Exception e) {
            client.closed = true;
            failure = e;
        } finally {
            client.lock.unlock();
        }

        if (failure != null) {
            close(client, failure);
            return false;
        }
        return true;
    }

    @Override
    public void remove(String connectionId) {
        Client client = clients.remove(connectionId);
        if (client == null) {
            return;
        }
        client.lock.lock();
        try {
            client.closed = true;
            client.pending.clear();
        } finally {
            client.lock.unlock();
        }
    }

    @Override
    public List<SseEmitter> getEmitters() {
        return clients.values().stream().map(client -> client.emitter).toList();
    }

    private static boolean isAlreadyInBacklog(FileEvent event, long watermark) {
        return FileEvent.ITEM_ADDED.equals(event.type())
                && event.seq() != null
                && event.seq() <= watermark;
    }

    private static boolean alreadySent(Client client, FileEvent event) {
        return FileEvent.ITEM_ADDED.equals(event.type())
                && event.seq() != null
                && event.seq() <= client.lastSeq;
    }

    private static void send(Client client, FileEvent event) throws Exception {
        SseEmitter.SseEventBuilder builder = SseEmitter.event()
                .name(event.type())
                .data(event);
        if (event.seq() != null) {
            builder.id(String.valueOf(event.seq()));
        }
        client.emitter.send(builder);
        if (event.seq() != null) {
            client.lastSeq = Math.max(client.lastSeq, event.seq());
        }
    }

    private void close(Client client, Throwable error) {
        clients.remove(client.id, client);
        client.lock.lock();
        try {
            client.closed = true;
            client.pending.clear();
        } finally {
            client.lock.unlock();
        }
        try {
            client.emitter.completeWithError(error);
        } catch (Exception e) {
            log.debug("SSE emitter for connection {} was already completed", client.id);
        }
    }

    private static final class Client {
        private final String id;
        private final SseEmitter emitter;
        private final ReentrantLock lock = new ReentrantLock();
        private final List<FileEvent> pending = new ArrayList<>();
        private boolean ready;
        private boolean closed;
        private long lastSeq;

        private Client(String id, SseEmitter emitter) {
            this.id = id;
            this.emitter = emitter;
        }
    }
}
