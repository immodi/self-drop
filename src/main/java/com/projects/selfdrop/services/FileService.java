package com.projects.selfdrop.services;

import com.projects.selfdrop.dtos.CreateFileRequest;
import com.projects.selfdrop.dtos.DeleteFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
import com.projects.selfdrop.dtos.UpdateFileRequest;
import com.projects.selfdrop.entities.File;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IFileRepository;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.mappers.FileMapper;
import com.projects.selfdrop.repositories.FileRepository;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Primary
@Service
public class FileService implements IFileService {
    private final IFileRepository fileRepository;
    private final FileMapper fileMapper;

    public FileService(FileRepository fileRepository, FileMapper fileMapper) {
        this.fileRepository = fileRepository;
        this.fileMapper = fileMapper;
    }

    public FileDto get(GetFileRequest request) {
        return fileRepository.findById(request.id())
                .map(fileMapper::toDto)
                .orElseThrow(() -> new FileNotFoundInDbException(request.id()));
    }

    public List<FileDto> getAll() {
        return fileMapper.toDtos(fileRepository.findAll());
    }

    public List<FileDto> findSince(long seq) {
        return fileMapper.toDtos(fileRepository.findSince(seq));
    }

    public List<FileDto> findLatest(int n) {
        return fileMapper.toDtos(fileRepository.findLatest(n));
    }

    public FileDto create(CreateFileRequest request) {
        File file = fileMapper.toEntity(request, request.id(), Instant.now().getEpochSecond());
        fileRepository.create(file);
        return fileMapper.toDto(file);
    }

    public FileDto update(UpdateFileRequest request) {
        File file = fileRepository.findById(request.id())
                .orElseThrow(() -> new FileNotFoundInDbException(request.id()));
        fileMapper.applyUpdate(file, request);
        fileRepository.update(file);
        return fileMapper.toDto(file);
    }

    public void delete(DeleteFileRequest request) {
        if (!fileRepository.deleteById(request.id())) {
            throw new FileNotFoundInDbException(request.id());
        }
    }
}
