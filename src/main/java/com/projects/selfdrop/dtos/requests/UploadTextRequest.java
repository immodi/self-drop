package com.projects.selfdrop.dtos.requests;

import java.util.Objects;

public record UploadTextRequest (
    String text,
    Long size
){
    public UploadTextRequest(String text) {
        this(Objects.requireNonNull(text, "text"), (long) text.length());
    }
    public String kind() { return "text"; }
    public CreateFileRequest toCreateFileRequest(String id, String chatId) {
        return new CreateFileRequest(id, chatId, kind(), text(), null, null, size());
    }
}
