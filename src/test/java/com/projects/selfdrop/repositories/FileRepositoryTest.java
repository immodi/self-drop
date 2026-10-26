package com.projects.selfdrop.repositories;

import com.projects.selfdrop.entities.File;
import com.projects.selfdrop.interfaces.IFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(FileRepository.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:selfdrop-test?mode=memory&cache=shared",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.sql.init.mode=always"
})
public class FileRepositoryTest {
    private static final long CREATED_EARLY = 1_700_000_000L;
    private static final long CREATED_LATE = 1_700_000_100L;

    @Autowired
    private IFileRepository repository;

    private File first;
    private File second;

    @BeforeEach
    public void setUp() {
        first = new File("1", "text", "hello", "hello.txt", "text/plain", 11L, CREATED_LATE);
        second = new File("2", "file", "world", "world.bin", "application/octet-stream", 22L, CREATED_EARLY);
        repository.create(first);
        repository.create(second);
    }

    // -------------------------------------------------------------------- findAll

    @Test
    public void testFindAll() {
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    public void testFindAllOrdersByCreatedAtThenSeqAscending() {
        File third = new File("3", "text", "same time", "same.txt", "text/plain", 33L, CREATED_LATE);
        repository.create(third);

        assertThat(repository.findAll())
                .extracting(File::getId)
                .containsExactly("2", "1", "3");
    }

    // ------------------------------------------------------------------ findSince

    @Test
    public void testFindSince() {
        assertThat(repository.findSince(first.getSeq()))
                .extracting(File::getId)
                .containsExactly("2");
    }

    @Test
    public void testFindSinceFromZeroReturnsEverythingInSeqOrder() {
        assertThat(repository.findSince(0))
                .extracting(File::getId)
                .containsExactly("1", "2");
    }

    @Test
    public void testFindSinceAfterLastReturnsEmpty() {
        assertThat(repository.findSince(second.getSeq())).isEmpty();
    }

    // ---------------------------------------------------------------- findLatest

    @Test
    public void testFindLatestReturnsNewestFirst() {
        File third = new File("3", "text", "same time", "same.txt", "text/plain", 33L, CREATED_LATE);
        repository.create(third);

        assertThat(repository.findLatest(2))
                .extracting(File::getId)
                .containsExactly("3", "1");
    }

    @Test
    public void testFindLatestReturnsAllWhenFewerThanLimit() {
        assertThat(repository.findLatest(10))
                .extracting(File::getId)
                .containsExactly("1", "2");
    }

    @Test
    public void testFindLatestClampsLowerBoundToOne() {
        assertThat(repository.findLatest(0))
                .extracting(File::getId)
                .containsExactly("1");
    }

    @Test
    public void testFindLatestClampsUpperBoundToHundred() {
        for (int i = 3; i <= 101; i++) {
            repository.create(new File(String.valueOf(i), "text", "t", null, null, 0L, CREATED_EARLY));
        }

        assertThat(repository.findLatest(1000)).hasSize(100);
    }

    // ------------------------------------------------------------------- findById

    @Test
    public void testFindById() {
        Optional<File> file = repository.findById("1");

        assertThat(file).isPresent();
        assertThat(file.orElseThrow().getSize()).isEqualTo(11L);
    }

    @Test
    public void testFindByIdMapsAllColumns() {
        File file = repository.findById("1").orElseThrow();

        assertThat(file.getSeq()).isEqualTo(first.getSeq());
        assertThat(file.getId()).isEqualTo("1");
        assertThat(file.getKind()).isEqualTo("text");
        assertThat(file.getText()).isEqualTo("hello");
        assertThat(file.getName()).isEqualTo("hello.txt");
        assertThat(file.getMime()).isEqualTo("text/plain");
        assertThat(file.getSize()).isEqualTo(11L);
        assertThat(file.getCreatedAt()).isEqualTo(CREATED_LATE);
    }

    @Test
    public void testFindByIdMapsNullColumns() {
        repository.create(new File("3", "file", null, null, null, null, CREATED_LATE));

        File file = repository.findById("3").orElseThrow();

        assertThat(file.getText()).isNull();
        assertThat(file.getName()).isNull();
        assertThat(file.getMime()).isNull();
        assertThat(file.getSize()).isNull();
    }

    @Test
    public void testFindByIdNotFound() {
        assertThat(repository.findById("999")).isEmpty();
    }

    @Test
    public void testFindByNullId() {
        assertThat(repository.findById(null)).isEmpty();
    }

    // --------------------------------------------------------------------- create

    @Test
    public void testCreateAssignsSeq() {
        File file = new File("3", "text", "new", "new.txt", "text/plain", 1L, CREATED_LATE);

        repository.create(file);

        assertThat(file.getSeq()).isNotNull().isPositive();
        assertThat(file.getSeq()).isGreaterThan(second.getSeq());
        assertThat(repository.findById("3")).isPresent();
    }

    @Test
    public void testCreateWithDuplicateIdFails() {
        File duplicate = new File("1", "text", "dup", null, null, 0L, CREATED_EARLY);

        assertThatThrownBy(() -> repository.create(duplicate))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    public void testCreateWithInvalidKindFails() {
        File invalid = new File("3", "video", "nope", null, null, 0L, CREATED_EARLY);

        assertThatThrownBy(() -> repository.create(invalid))
                .isInstanceOf(DataAccessException.class);
    }

    // --------------------------------------------------------------------- update

    @Test
    public void testUpdateChangesMutableFields() {
        first.setKind("file");
        first.setText("updated");
        first.setName("updated.bin");
        first.setMime("application/octet-stream");
        first.setSize(99L);

        assertThat(repository.update(first)).isTrue();

        File file = repository.findById("1").orElseThrow();
        assertThat(file.getKind()).isEqualTo("file");
        assertThat(file.getText()).isEqualTo("updated");
        assertThat(file.getName()).isEqualTo("updated.bin");
        assertThat(file.getMime()).isEqualTo("application/octet-stream");
        assertThat(file.getSize()).isEqualTo(99L);
        assertThat(file.getSeq()).isEqualTo(first.getSeq());
        assertThat(file.getCreatedAt()).isEqualTo(CREATED_LATE);
    }

    @Test
    public void testUpdateMissingReturnsFalse() {
        File missing = new File("999", "text", "x", null, null, 0L, CREATED_EARLY);

        assertThat(repository.update(missing)).isFalse();
        assertThat(repository.findAll()).hasSize(2);
    }

    // ---------------------------------------------------------------- deleteById

    @Test
    public void testDeleteById() {
        assertThat(repository.deleteById("1")).isTrue();
        assertThat(repository.findById("1")).isEmpty();
        assertThat(repository.findAll())
                .extracting(File::getId)
                .containsExactly("2");
    }

    @Test
    public void testDeleteByIdMissingReturnsFalse() {
        assertThat(repository.deleteById("999")).isFalse();
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    public void testDeleteByNullIdReturnsFalse() {
        assertThat(repository.deleteById(null)).isFalse();
    }
}
