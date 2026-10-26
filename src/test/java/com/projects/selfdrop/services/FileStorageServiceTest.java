package com.projects.selfdrop.services;

import com.projects.selfdrop.interfaces.IFileStorage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class FileStorageServiceTest {
    private static final byte[] DATA = "hello selfdrop".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path tempDir;

    private IFileStorage storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(tempDir);
    }

    // ---------------------------------------------------------------- constructor

    @Test
    void constructor_createsMissingStorageRoot() {
        Path root = tempDir.resolve("nested/storage/root");

        new FileStorageService(root);

        assertTrue(Files.isDirectory(root));
    }

    // ------------------------------------------------------------- store(InputStream)

    @Test
    void store_createsFileWithContent() throws IOException {
        storage.store("note.txt", new ByteArrayInputStream(DATA));

        assertArrayEquals(DATA, Files.readAllBytes(tempDir.resolve("note.txt")));
        assertNoTempFilesLeftBehind();
    }

    @Test
    void store_overwritesExistingFile() throws IOException {
        storage.store("note.txt", "a longer first version".getBytes(StandardCharsets.UTF_8));

        storage.store("note.txt", new ByteArrayInputStream(DATA));

        assertArrayEquals(DATA, storage.read("note.txt"));
        assertEquals(DATA.length, storage.size("note.txt"));
        assertNoTempFilesLeftBehind();
    }

    @Test
    void store_createsMissingParentDirectories() throws IOException {
        storage.store("2026/10/photo.jpg", new ByteArrayInputStream(DATA));

        assertArrayEquals(DATA, Files.readAllBytes(tempDir.resolve("2026/10/photo.jpg")));
    }

    @Test
    void store_normalizesKeyInsideRoot() throws IOException {
        storage.store("a/../note.txt", new ByteArrayInputStream(DATA));

        assertArrayEquals(DATA, Files.readAllBytes(tempDir.resolve("note.txt")));
        assertFalse(Files.exists(tempDir.resolve("a")));
    }

    @Test
    void store_writesEmptyStreamAsEmptyFile() throws IOException {
        storage.store("empty.txt", new ByteArrayInputStream(new byte[0]));

        assertTrue(storage.exists("empty.txt"));
        assertEquals(0, storage.size("empty.txt"));
    }

    @Test
    void store_byteArrayWritesContent() throws IOException {
        storage.store("bytes.bin", DATA);

        assertArrayEquals(DATA, storage.read("bytes.bin"));
    }

    @Test
    void store_rejectsNullKey() {
        assertThrows(NullPointerException.class,
                () -> storage.store(null, new ByteArrayInputStream(DATA)));
    }

    @Test
    void store_rejectsKeyEscapingRoot() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("../outside.txt", new ByteArrayInputStream(DATA)));

        assertFalse(Files.exists(tempDir.getParent().resolve("outside.txt")));
    }

    @Test
    void store_rejectsAbsoluteKeyOutsideRoot() {
        Path outside = tempDir.resolveSibling("absolute-outside.txt");

        assertThrows(IllegalArgumentException.class,
                () -> storage.store(outside.toString(), new ByteArrayInputStream(DATA)));

        assertFalse(Files.exists(outside));
    }

    @Test
    void store_rejectsKeyResolvingToRoot() {
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("", new ByteArrayInputStream(DATA)));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store(".", new ByteArrayInputStream(DATA)));
        assertThrows(IllegalArgumentException.class,
                () -> storage.store("a/..", new ByteArrayInputStream(DATA)));
    }

    @Test
    void store_keepsExistingFileAndRemovesTempFileWhenStreamFails() throws IOException {
        storage.store("keep.txt", DATA);

        assertThrows(IOException.class,
                () -> storage.store("keep.txt", streamFailingWith(new IOException("boom"))));

        assertArrayEquals(DATA, storage.read("keep.txt"));
        assertNoTempFilesLeftBehind();
    }

    @Test
    void store_removesTempFileWhenStreamFailsWithUncheckedException() throws IOException {
        assertThrows(IllegalStateException.class,
                () -> storage.store("nested/dir/failed.bin",
                        streamFailingWith(new IllegalStateException("boom"))));

        assertFalse(Files.exists(tempDir.resolve("nested/dir/failed.bin")));
        assertNoTempFilesLeftBehind();
    }

    @Test
    void store_doesNotPublishPartialDataWhenStreamFailsMidway() throws IOException {
        InputStream partial = new InputStream() {
            private boolean firstRead = true;

            @Override
            public int read() throws IOException {
                if (firstRead) {
                    firstRead = false;
                    return 'x';
                }
                throw new IOException("boom");
            }
        };

        assertThrows(IOException.class, () -> storage.store("partial.bin", partial));

        assertFalse(Files.exists(tempDir.resolve("partial.bin")));
        assertNoTempFilesLeftBehind();
    }

    // ------------------------------------------------------------------------- open

    @Test
    void open_returnsStreamWithContent() throws IOException {
        storage.store("note.txt", DATA);

        try (InputStream in = storage.open("note.txt")) {
            assertArrayEquals(DATA, in.readAllBytes());
        }
    }

    @Test
    void open_throwsForMissingKey() {
        assertThrows(NoSuchFileException.class, () -> storage.open("missing.txt"));
    }

    @Test
    void open_throwsForDirectoryKey() throws IOException {
        Files.createDirectory(tempDir.resolve("dir"));

        assertThrows(NoSuchFileException.class, () -> storage.open("dir"));
    }

    // ------------------------------------------------------------------------- read

    @Test
    void read_returnsContent() throws IOException {
        storage.store("note.txt", DATA);

        assertArrayEquals(DATA, storage.read("note.txt"));
    }

    @Test
    void read_throwsForMissingKey() {
        assertThrows(NoSuchFileException.class, () -> storage.read("missing.txt"));
    }

    // ----------------------------------------------------------------------- stream

    @Test
    void stream_writesContentToOutputStream() throws IOException {
        storage.store("note.txt", DATA);
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        storage.stream("note.txt", out);

        assertArrayEquals(DATA, out.toByteArray());
    }

    @Test
    void stream_throwsForMissingKey() {
        assertThrows(NoSuchFileException.class,
                () -> storage.stream("missing.txt", new ByteArrayOutputStream()));
    }

    // ----------------------------------------------------------------------- exists

    @Test
    void exists_isTrueOnlyForStoredFiles() throws IOException {
        storage.store("note.txt", DATA);
        Files.createDirectory(tempDir.resolve("dir"));

        assertTrue(storage.exists("note.txt"));
        assertFalse(storage.exists("missing.txt"));
        assertFalse(storage.exists("dir"));
    }

    // ------------------------------------------------------------------------- size

    @Test
    void size_returnsFileSize() throws IOException {
        storage.store("note.txt", DATA);

        assertEquals(DATA.length, storage.size("note.txt"));
    }

    @Test
    void size_throwsForMissingKey() {
        assertThrows(NoSuchFileException.class, () -> storage.size("missing.txt"));
    }

    // ----------------------------------------------------------------------- delete

    @Test
    void delete_removesStoredFile() throws IOException {
        storage.store("note.txt", DATA);

        storage.delete("note.txt");

        assertFalse(storage.exists("note.txt"));
        assertFalse(Files.exists(tempDir.resolve("note.txt")));
    }

    @Test
    void delete_isNoOpForMissingKey() {
        assertDoesNotThrow(() -> storage.delete("missing.txt"));
    }

    // ---------------------------------------------------------------------- helpers

    private static InputStream streamFailingWith(IOException failure) {
        return new InputStream() {
            @Override
            public int read() throws IOException {
                throw failure;
            }
        };
    }

    private static InputStream streamFailingWith(RuntimeException failure) {
        return new InputStream() {
            @Override
            public int read() {
                throw failure;
            }
        };
    }

    private void assertNoTempFilesLeftBehind() throws IOException {
        try (Stream<Path> paths = Files.walk(tempDir)) {
            List<Path> leftovers = paths
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().startsWith(".upload-"))
                    .toList();

            assertTrue(leftovers.isEmpty(), "temporary files left behind: " + leftovers);
        }
    }
}
