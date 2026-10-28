package com.projects.selfdrop.interfaces;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface IFileStorage {
    void store(String key, InputStream in) throws IOException;
    void store(String key, byte[] data) throws IOException;
    InputStream open(String key) throws IOException;
    byte[] read(String key) throws IOException;
    void stream(String key, OutputStream out) throws IOException;
    boolean exists(String key);
    long size(String key) throws IOException;
    void delete(String key) throws IOException;
    String UPLOAD_TEMP_PREFIX = ".upload-";
    String UPLOAD_TEMP_SUFFIX = ".tmp";
}
