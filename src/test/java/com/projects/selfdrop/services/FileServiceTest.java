package com.projects.selfdrop.services;

import com.projects.selfdrop.dtos.CreateFileRequest;
import com.projects.selfdrop.dtos.DeleteFileRequest;
import com.projects.selfdrop.dtos.FileDto;
import com.projects.selfdrop.dtos.GetFileRequest;
import com.projects.selfdrop.dtos.UpdateFileRequest;
import com.projects.selfdrop.dtos.UploadTextRequest;
import com.projects.selfdrop.entities.File;
import com.projects.selfdrop.exceptions.FileNotFoundInDbException;
import com.projects.selfdrop.mappers.FileMapper;
import com.projects.selfdrop.repositories.FileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    private static final long CREATED_AT = 1_700_000_000L;

    @Mock
    private FileRepository fileRepository;

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, new FileMapper());
    }

    private static File entity(String id) {
        return new File(id, "file", null, "note.txt", "text/plain", 4L, CREATED_AT);
    }

    private static FileDto dto(File file) {
        return new FileDto(
                file.getId(),
                file.getKind(),
                file.getText(),
                file.getName(),
                file.getMime(),
                file.getSize(),
                file.getCreatedAt());
    }

    // -------------------------------------------------------------------- get

    @Test
    void get_returnsMappedDto() {
        File file = entity("id1");
        when(fileRepository.findById("id1")).thenReturn(Optional.of(file));

        assertThat(fileService.get(new GetFileRequest("id1"))).isEqualTo(dto(file));
    }

    @Test
    void get_throwsWhenRowIsMissing() {
        when(fileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.get(new GetFileRequest("missing")))
                .isInstanceOf(FileNotFoundInDbException.class)
                .hasMessageContaining("missing");
    }

    // --------------------------------------------------------------- listings

    @Test
    void getAll_mapsEveryRowInOrder() {
        when(fileRepository.findAll()).thenReturn(List.of(entity("a"), entity("b")));

        assertThat(fileService.getAll())
                .containsExactly(dto(entity("a")), dto(entity("b")));
    }

    @Test
    void findSince_usesTheGivenSequenceAndMaps() {
        when(fileRepository.findSince(42L)).thenReturn(List.of(entity("a")));

        assertThat(fileService.findSince(42L)).containsExactly(dto(entity("a")));
    }

    @Test
    void findLatest_usesTheGivenLimitAndMaps() {
        when(fileRepository.findLatest(5)).thenReturn(List.of(entity("a")));

        assertThat(fileService.findLatest(5)).containsExactly(dto(entity("a")));
    }

    // ----------------------------------------------------------------- create

    @Test
    void create_persistsEntityBuiltFromRequest() {
        CreateFileRequest request =
                new CreateFileRequest("id1", "file", null, "note.txt", "text/plain", 4L);
        long before = Instant.now().getEpochSecond();

        FileDto created = fileService.create(request);

        long after = Instant.now().getEpochSecond();
        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).create(captor.capture());
        File saved = captor.getValue();

        assertThat(saved.getId()).isEqualTo("id1");
        assertThat(saved.getKind()).isEqualTo("file");
        assertThat(saved.getText()).isNull();
        assertThat(saved.getName()).isEqualTo("note.txt");
        assertThat(saved.getMime()).isEqualTo("text/plain");
        assertThat(saved.getSize()).isEqualTo(4L);
        assertThat(saved.getCreatedAt()).isBetween(before, after);
        assertThat(created).isEqualTo(dto(saved));
    }

    @Test
    void create_fromTextUploadKeepsTextAndLeavesFileFieldsNull() {
        CreateFileRequest request = new UploadTextRequest("hello").toCreateFileRequest("id2");

        fileService.create(request);

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).create(captor.capture());
        File saved = captor.getValue();

        assertThat(saved.getKind()).isEqualTo("text");
        assertThat(saved.getText()).isEqualTo("hello");
        assertThat(saved.getName()).isNull();
        assertThat(saved.getMime()).isNull();
        assertThat(saved.getSize()).isEqualTo(5L);
    }

    // ----------------------------------------------------------------- update

    @Test
    void update_buildsUpdatedEntityAndPersistsIt() {
        File existing = entity("id1");
        when(fileRepository.findById("id1")).thenReturn(Optional.of(existing));
        when(fileRepository.update(any(File.class))).thenReturn(true);

        FileDto updated = fileService.update(
                new UpdateFileRequest("id1", "text", "hello", null, null, 5L));

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).update(captor.capture());
        File saved = captor.getValue();

        assertThat(saved).isNotSameAs(existing);
        assertThat(saved.getId()).isEqualTo("id1");
        assertThat(saved.getKind()).isEqualTo("text");
        assertThat(saved.getText()).isEqualTo("hello");
        assertThat(saved.getName()).isNull();
        assertThat(saved.getMime()).isNull();
        assertThat(saved.getSize()).isEqualTo(5L);
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(updated).isEqualTo(dto(saved));

        // The entity loaded for the existence check is not mutated.
        assertThat(existing.getKind()).isEqualTo("file");
        assertThat(existing.getName()).isEqualTo("note.txt");
    }

    @Test
    void update_throwsWhenRowIsMissing() {
        when(fileRepository.findById("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.update(
                new UpdateFileRequest("missing", "text", "hello", null, null, 5L)))
                .isInstanceOf(FileNotFoundInDbException.class)
                .hasMessageContaining("missing");
        verify(fileRepository, never()).update(any());
    }

    @Test
    void update_throwsWhenRowDisappearsBeforeTheWrite() {
        File existing = entity("id1");
        when(fileRepository.findById("id1")).thenReturn(Optional.of(existing));
        when(fileRepository.update(any(File.class))).thenReturn(false);

        assertThatThrownBy(() -> fileService.update(
                new UpdateFileRequest("id1", "text", "hello", null, null, 5L)))
                .isInstanceOf(FileNotFoundInDbException.class)
                .hasMessageContaining("id1");

        ArgumentCaptor<File> captor = ArgumentCaptor.forClass(File.class);
        verify(fileRepository).update(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(CREATED_AT);
    }

    // ----------------------------------------------------------------- delete

    @Test
    void delete_succeedsWhenRowWasDeleted() {
        when(fileRepository.deleteById("id1")).thenReturn(true);

        assertThatCode(() -> fileService.delete(new DeleteFileRequest("id1")))
                .doesNotThrowAnyException();
        verify(fileRepository).deleteById("id1");
        verifyNoMoreInteractions(fileRepository);
    }

    @Test
    void delete_throwsWhenRowIsMissing() {
        when(fileRepository.deleteById("missing")).thenReturn(false);

        assertThatThrownBy(() -> fileService.delete(new DeleteFileRequest("missing")))
                .isInstanceOf(FileNotFoundInDbException.class)
                .hasMessageContaining("missing");
    }
}
