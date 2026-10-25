package com.projects.selfdrop.exceptions;

public class FileNotFoundInDbException extends RuntimeException {
    public FileNotFoundInDbException(String id) {
        super("No file with id: " + id);
    }
}