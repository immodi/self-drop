package com.projects.selfdrop.dtos;

public record UploadFileRequest(
        String name,
        String mime,
        Long size
) {
    public String kind() { return "file"; }
    public CreateFileRequest toCreateFileRequest(String id) {
        return new CreateFileRequest(id, kind(), null,  name(), mime(), size());
    }
}
