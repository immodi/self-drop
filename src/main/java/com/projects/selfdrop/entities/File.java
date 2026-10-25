package com.projects.selfdrop.entities;

import com.projects.selfdrop.dtos.FileDto;

public class File {

    private Long seq;
    private String id;
    private String kind;
    private String text;
    private String name;
    private String mime;
    private Long size;
    private String device;
    private Long createdAt;

    protected File() {}
    public File(String id, String kind, String text, String name, String mime, Long size, String device, long createdAt) {
        this.id = id;
        this.kind = kind;
        this.text = text;
        this.name = name;
        this.mime = mime;
        this.size = size;
        this.device = device;
        this.createdAt = createdAt;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getMime() {
        return mime;
    }

    public void setMime(String mime) {
        this.mime = mime;
    }

    public Long getSize() {
        return size;
    }

    public void setSize(Long size) {
        this.size = size;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public Long getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Long createdAt) {
        this.createdAt = createdAt;
    }
}
