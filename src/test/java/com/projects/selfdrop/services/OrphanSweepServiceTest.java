package com.projects.selfdrop.services;

import com.projects.selfdrop.interfaces.IFileRepository;
import com.projects.selfdrop.interfaces.IFileStorage;
import com.projects.selfdrop.utils.IdGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanSweepServiceTest {

    private static final long GRACE_SECONDS = 60;
    private static final long STALE_AGE_SECONDS = 3600;

    @Mock
    private IFileRepository fileRepository;

    @TempDir
    Path storageRoot;

    private OrphanSweepService service() {
        return new OrphanSweepService(storageRoot, GRACE_SECONDS, fileRepository);
    }

    private Path file(String name) throws IOException {
        Path path = storageRoot.resolve(name);
        Files.writeString(path, "payload");
        return path;
    }

    private Path staleFile(String name) throws IOException {
        Path path = file(name);
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minusSeconds(STALE_AGE_SECONDS)));
        return path;
    }

    private Path staleUploadTempFile() throws IOException {
        Path path = Files.createTempFile(storageRoot, IFileStorage.UPLOAD_TEMP_PREFIX, IFileStorage.UPLOAD_TEMP_SUFFIX);
        Files.writeString(path, "partial");
        Files.setLastModifiedTime(path, FileTime.from(Instant.now().minusSeconds(STALE_AGE_SECONDS)));
        return path;
    }

    private static String idOf(Path path) {
        return path.getFileName().toString();
    }

    // ------------------------------------------------------- sweepOrphanFiles

    @Test
    void deletesStaleIdShapedFileWithoutRow() throws IOException {
        Path orphan = staleFile(IdGenerator.generate());
        when(fileRepository.existsById(idOf(orphan))).thenReturn(false);

        int deleted = service().sweepOrphanFiles();

        assertThat(deleted).isEqualTo(1);
        assertThat(orphan).doesNotExist();
        verify(fileRepository).existsById(idOf(orphan));
    }

    @Test
    void keepsStaleFileThatStillHasRow() throws IOException {
        Path stored = staleFile(IdGenerator.generate());
        when(fileRepository.existsById(idOf(stored))).thenReturn(true);

        assertThat(service().sweepOrphanFiles()).isZero();
        assertThat(stored).exists();
    }

    @Test
    void countsOnlyFilesThatWereActuallyDeleted() throws IOException {
        Path orphan = staleFile(IdGenerator.generate());
        Path stored = staleFile(IdGenerator.generate());
        when(fileRepository.existsById(idOf(orphan))).thenReturn(false);
        when(fileRepository.existsById(idOf(stored))).thenReturn(true);

        assertThat(service().sweepOrphanFiles()).isEqualTo(1);
        assertThat(orphan).doesNotExist();
        assertThat(stored).exists();
    }

    @Test
    void keepsFreshFileWithoutQueryingDatabase() throws IOException {
        Path inFlight = file(IdGenerator.generate());

        assertThat(service().sweepOrphanFiles()).isZero();
        assertThat(inFlight).exists();
        verifyNoInteractions(fileRepository);
    }

    @Test
    void ignoresFilesThatAreNotIdShaped() throws IOException {
        Path text = staleFile("notes.txt");
        Path temp = staleFile(IFileStorage.UPLOAD_TEMP_PREFIX + "123" + IFileStorage.UPLOAD_TEMP_SUFFIX);
        Path uppercase = staleFile(IdGenerator.generate().toUpperCase());

        assertThat(service().sweepOrphanFiles()).isZero();
        assertThat(text).exists();
        assertThat(temp).exists();
        assertThat(uppercase).exists();
        verifyNoInteractions(fileRepository);
    }

    @Test
    void abortsWithoutDeletingWhenDatabaseLookupFails() throws IOException {
        Path first = staleFile(IdGenerator.generate());
        Path second = staleFile(IdGenerator.generate());
        when(fileRepository.existsById(anyString()))
                .thenThrow(new DataAccessResourceFailureException("database is down"));

        assertThat(service().sweepOrphanFiles()).isZero();
        assertThat(first).exists();
        assertThat(second).exists();
    }

    @Test
    void returnsZeroForEmptyStorageRoot() {
        assertThat(service().sweepOrphanFiles()).isZero();
        verifyNoInteractions(fileRepository);
    }

    @Test
    void returnsZeroWhenStorageRootIsMissing() {
        OrphanSweepService service =
                new OrphanSweepService(storageRoot.resolve("missing"), GRACE_SECONDS, fileRepository);

        assertThat(service.sweepOrphanFiles()).isZero();
        verifyNoInteractions(fileRepository);
    }

    // -------------------------------------------------- sweepAbandonedUploads

    @Test
    void deletesStaleUploadTempFile() throws IOException {
        Path temp = staleUploadTempFile();

        assertThat(service().sweepAbandonedUploads()).isEqualTo(1);
        assertThat(temp).doesNotExist();
        verifyNoInteractions(fileRepository);
    }

    @Test
    void keepsFreshUploadTempFile() throws IOException {
        Path temp = file(IFileStorage.UPLOAD_TEMP_PREFIX + "123" + IFileStorage.UPLOAD_TEMP_SUFFIX);

        assertThat(service().sweepAbandonedUploads()).isZero();
        assertThat(temp).exists();
    }

    @Test
    void tempSweepIgnoresStoredFilesAndUnrelatedFiles() throws IOException {
        Path stored = staleFile(IdGenerator.generate());
        Path unrelated = staleFile("notes.txt");

        assertThat(service().sweepAbandonedUploads()).isZero();
        assertThat(stored).exists();
        assertThat(unrelated).exists();
        verifyNoInteractions(fileRepository);
    }

    @Test
    void tempSweepDoesNotNeedTheDatabase() throws IOException {
        Path temp = staleUploadTempFile();

        assertThat(service().sweepAbandonedUploads()).isEqualTo(1);
        assertThat(temp).doesNotExist();
        verifyNoInteractions(fileRepository);
    }
}
