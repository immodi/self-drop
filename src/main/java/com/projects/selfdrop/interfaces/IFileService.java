package com.projects.selfdrop.interfaces;

import com.projects.selfdrop.dtos.requests.*;
import com.projects.selfdrop.dtos.responses.*;

import java.util.List;

public interface IFileService {
    FileDto get(GetFileRequest request);
    List<FileDto> getAll();
    List<FileDto> getAllByChat(String chatId);
    List<FileDto> findSince(long seq);
    List<FileDto> findSinceByChat(String chatId, long seq);
    List<FileDto> findLatest(int n);
    List<FileDto> findLatestByChat(String chatId, int n);
    long maxSeq();
    FileDto create(CreateFileRequest request);
    FileDto update(UpdateFileRequest request);
    void delete(DeleteFileRequest request);
}
