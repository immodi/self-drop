package com.projects.selfdrop.mappers;

import com.projects.selfdrop.dtos.CreateFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.UpdateFileRequest;
import com.projects.selfdrop.entities.File;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class FileMapper {

    public FileDto toDto(File file) {
        return new FileDto(
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

    public void applyUpdate(File file, UpdateFileRequest request) {
        file.setKind(request.kind());
        file.setText(request.text());
        file.setName(request.name());
        file.setMime(request.mime());
        file.setSize(request.size());
    }
}
