package com.projects.selfdrop.exceptions;

public class ChatNotFoundInDbException extends RuntimeException {
    public ChatNotFoundInDbException(String id) {
        super("No chat with id: " + id);
    }
}
