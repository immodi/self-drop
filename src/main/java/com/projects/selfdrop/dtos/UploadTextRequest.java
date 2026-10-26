package com.projects.selfdrop.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Objects;

public record UploadTextRequest (
    String text,
    Long size
){
    public UploadTextRequest(String text) {
        this(Objects.requireNonNull(text, "text"), (long) text.length());
    }
    public String kind() { return "text"; }
    public CreateFileRequest toCreateFileRequest(String id) {
        return new CreateFileRequest(id, kind(), text(), null, null, size());
    }
}
