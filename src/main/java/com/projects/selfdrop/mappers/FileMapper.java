package com.projects.selfdrop.mappers;

import com.projects.selfdrop.dtos.requests.CreateFileRequest;
import com.projects.selfdrop.dtos.responses.FileDto;
import com.projects.selfdrop.dtos.requests.UpdateFileRequest;
import com.projects.selfdrop.entities.File;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileMapper {

    public FileDto toDto(File file) {
        return new FileDto(
                file.getSeq(),
                file.getId(),
                file.getKind(),
                file.getText(),
                file.getName(),
                file.getMime(),
                file.getSize(),
                file.getCreatedAt()
        );
    }

    public List<FileDto> toDtos(List<File> files) {
        return files.stream().map(this::toDto).toList();
    }

    public File toEntity(CreateFileRequest request, String id, Long createdAt) {
        return new File(
                id,
                request.kind(),
                request.text(),
                request.name(),
                request.mime(),
                request.size(),
                createdAt
        );
    }

    public File toEntity(UpdateFileRequest request, String id, Long createdAt) {
        return new File(
                id,
                request.kind(),
                request.text(),
                request.name(),
                request.mime(),
                request.size(),
                createdAt
        );
    }
}
