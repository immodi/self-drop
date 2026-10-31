package com.projects.selfdrop.services;

import com.projects.selfdrop.dtos.requests.*;
import com.projects.selfdrop.dtos.responses.*;
import com.projects.selfdrop.entities.File;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.interfaces.IEventPublisher;
import com.projects.selfdrop.interfaces.IFileRepository;
import com.projects.selfdrop.interfaces.IFileService;
import com.projects.selfdrop.mappers.FileMapper;
import com.projects.selfdrop.messaging.FileEvent;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Primary
@Service
public class FileService implements IFileService {
    private final IFileRepository fileRepository;
    private final FileMapper fileMapper;
    private final IEventPublisher<FileEvent> eventPublisher;


    public FileService(
            IFileRepository fileRepository,
            FileMapper fileMapper,
            IEventPublisher<FileEvent> eventPublisher) {
        this.fileRepository = fileRepository;
        this.fileMapper = fileMapper;
        this.eventPublisher = eventPublisher;
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
        FileDto dto = fileMapper.toDto(file);
        eventPublisher.publish(FileEvent.added(dto));
        return dto;
    }

    public FileDto update(UpdateFileRequest request) {
        File existing = fileRepository.findById(request.id())
                .orElseThrow(() -> new FileNotFoundInDbException(request.id()));
        File updated = fileMapper.toEntity(request, existing.getId(), existing.getCreatedAt());
        updated.setSeq(existing.getSeq());
        if (!fileRepository.update(updated)) {
            throw new FileNotFoundInDbException(request.id());
        }

        FileDto dto = fileMapper.toDto(updated);
        eventPublisher.publish(FileEvent.updated(dto));
        return dto;
    }

    public void delete(DeleteFileRequest request) {
        File existing = fileRepository.findById(request.id())
                .orElseThrow(() -> new FileNotFoundInDbException(request.id()));
        if (!fileRepository.deleteById(request.id())) {
            throw new FileNotFoundInDbException(request.id());
        }
        eventPublisher.publish(FileEvent.deleted(fileMapper.toDto(existing)));
    }
}
