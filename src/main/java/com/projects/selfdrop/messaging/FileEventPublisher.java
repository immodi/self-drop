package com.projects.selfdrop.messaging;

import com.projects.selfdrop.interfaces.IEventPublisher;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Publishes item events. There is deliberately no in-memory buffer: every event
 * is handed straight to the single-threaded broadcaster, which keeps the queue
 * bounded and preserves publication order.
 */
@Component
public class FileEventPublisher implements IEventPublisher<FileEvent> {
    private final ApplicationEventPublisher eventPublisher;

    public FileEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void publish(FileEvent item) {
        eventPublisher.publishEvent(item);
    }
}
