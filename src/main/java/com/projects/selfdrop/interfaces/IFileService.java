package com.projects.selfdrop.interfaces;

import com.projects.selfdrop.dtos.*;

import java.util.List;

public interface IFileService {
    FileDto get(GetFileRequest request);
    List<FileDto> getAll();
    List<FileDto> findSince(long seq);
    List<FileDto> findLatest(int n);
    FileDto create(CreateFileRequest request);
    FileDto update(UpdateFileRequest request);
    void delete(DeleteFileRequest request);
}
