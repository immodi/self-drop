package com.projects.selfdrop.messaging;

import com.projects.selfdrop.dtos.responses.FileDto;

/**
 * Notification payload for the live-update stream.
 *
 * <p>{@code chatId} identifies the owning chat for every event, including
 * deletes where {@code item} is {@code null}. {@code seq} is the database
 * sequence of the affected row, which lets clients (and the broadcaster) order
 * and de-duplicate events.
 */
public record FileEvent(String type, String id, String chatId, Long seq, FileDto item) {

    public static final String ITEM_ADDED = "item-added";
    public static final String ITEM_UPDATED = "item-updated";
    public static final String ITEM_DELETED = "item-deleted";

    public static FileEvent added(FileDto item) {
        return new FileEvent(ITEM_ADDED, item.id(), item.chatId(), item.seq(), item);
    }

    public static FileEvent updated(FileDto item) {
        return new FileEvent(ITEM_UPDATED, item.id(), item.chatId(), item.seq(), item);
    }

    public static FileEvent deleted(FileDto item) {
        return new FileEvent(ITEM_DELETED, item.id(), item.chatId(), item.seq(), null);
    }
}
