package com.projects.selfdrop.repositories;

import com.projects.selfdrop.entities.Chat;
import com.projects.selfdrop.interfaces.IChatRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class ChatRepository implements IChatRepository {
    private static final BeanPropertyRowMapper<Chat> ROW_MAPPER = new BeanPropertyRowMapper<>(Chat.class);
    private static final String COLUMNS = "seq, id, name, created_at";
    private static final Logger log = LoggerFactory.getLogger(ChatRepository.class);

    private final JdbcTemplate jdbcTemplate;

    public ChatRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<Chat> findAll() {
        String sql = "SELECT %s FROM chats ORDER BY created_at, seq".formatted(COLUMNS);
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public Optional<Chat> findById(String id) {
        String sql = "SELECT %s FROM chats WHERE id = ?".formatted(COLUMNS);
        try {
            return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
        } catch (DataAccessException e) {
            log.error("Failed to retrieve chat with id: {}", id, e);
            throw e;
        }
    }

    @Override
    public boolean existsById(String id) {
        String sql = "SELECT EXISTS (SELECT 1 FROM chats WHERE id = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    public void create(Chat chat) {
        String sql = """
                INSERT INTO chats (id, name, created_at)
                VALUES (?, ?, ?)
                RETURNING seq
                """;
        Long seq = jdbcTemplate.queryForObject(sql, Long.class,
                chat.getId(),
                chat.getName(),
                chat.getCreatedAt());
        chat.setSeq(seq);
    }

    public boolean update(Chat chat) {
        String sql = "UPDATE chats SET name = ? WHERE id = ?";
        return jdbcTemplate.update(sql, chat.getName(), chat.getId()) > 0;
    }

    public boolean deleteById(String id) {
        String sql = "DELETE FROM chats WHERE id = ?";
        return jdbcTemplate.update(sql, id) > 0;
    }
}
