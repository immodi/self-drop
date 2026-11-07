package com.projects.selfdrop.dtos.requests;

public record UploadFileRequest(
        String name,
        String mime,
        Long size
) {
    public String kind() { return "file"; }
    public CreateFileRequest toCreateFileRequest(String id, String chatId) {
        return new CreateFileRequest(id, chatId, kind(), null, name(), mime(), size());
    }
}
