package com.projects.selfdrop.repositories;

import com.projects.selfdrop.entities.Chat;
import com.projects.selfdrop.interfaces.IChatRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ChatRepository.class)
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:sqlite:file:selfdrop-chat-test?mode=memory&cache=shared",
    "spring.datasource.driver-class-name=org.sqlite.JDBC",
    "spring.sql.init.mode=always"
})
public class ChatRepositoryTest {
    private static final long CREATED_EARLY = 1_700_000_000L;
    private static final long CREATED_LATE = 1_700_000_100L;

    @Autowired
    private IChatRepository repository;

    private Chat first;
    private Chat second;

    @BeforeEach
    void setUp() {
        first = new Chat("1", "General", CREATED_LATE);
        second = new Chat("2", "Work", CREATED_EARLY);
        repository.create(first);
        repository.create(second);
    }

    @Test
    void findAllOrdersByCreatedAtThenSeq() {
        Chat third = new Chat("3", "Later", CREATED_LATE);
        repository.create(third);

        assertThat(repository.findAll())
                .extracting(Chat::getId)
                .containsExactly("2", "1", "3");
    }

    @Test
    void findByIdMapsAllColumns() {
        Chat chat = repository.findById("1").orElseThrow();

        assertThat(chat.getSeq()).isEqualTo(first.getSeq());
        assertThat(chat.getId()).isEqualTo("1");
        assertThat(chat.getName()).isEqualTo("General");
        assertThat(chat.getCreatedAt()).isEqualTo(CREATED_LATE);
    }

    @Test
    void findByIdReturnsEmptyWhenMissing() {
        assertThat(repository.findById("999")).isEmpty();
    }

    @Test
    void existsById() {
        assertThat(repository.existsById("1")).isTrue();
        assertThat(repository.existsById("999")).isFalse();
    }

    @Test
    void createAssignsSeqAndRejectsDuplicateIds() {
        Chat third = new Chat("3", "Later", CREATED_LATE);
        repository.create(third);

        assertThat(third.getSeq()).isNotNull().isPositive();
        assertThat(repository.findAll()).hasSize(3);

        assertThatThrownBy(() -> repository.create(new Chat("1", "Duplicate", CREATED_EARLY)))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void updateChangesOnlyTheName() {
        first.setName("Renamed");

        assertThat(repository.update(first)).isTrue();

        Chat chat = repository.findById("1").orElseThrow();
        assertThat(chat.getName()).isEqualTo("Renamed");
        assertThat(chat.getCreatedAt()).isEqualTo(CREATED_LATE);
    }

    @Test
    void updateMissingReturnsFalse() {
        assertThat(repository.update(new Chat("999", "Missing", CREATED_EARLY))).isFalse();
    }

    @Test
    void deleteById() {
        assertThat(repository.deleteById("1")).isTrue();
        assertThat(repository.findById("1")).isEmpty();
        assertThat(repository.findAll()).extracting(Chat::getId).containsExactly("2");
    }

    @Test
    void deleteByIdMissingReturnsFalse() {
        assertThat(repository.deleteById("999")).isFalse();
    }
}
