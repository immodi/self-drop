package com.projects.selfdrop.repositories;

import com.projects.selfdrop.entities.File;
import com.projects.selfdrop.interfaces.IFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Primary
@Repository
public class FileRepository implements IFileRepository {
    private static final BeanPropertyRowMapper<File> ROW_MAPPER = new BeanPropertyRowMapper<>(File.class);
    private static final String COLUMNS =
            "seq, id, kind, text, name, mime, size, created_at";
    private static final Logger log = LoggerFactory.getLogger(FileRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public FileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<File> findAll() {
        String sql = "SELECT %s FROM files ORDER BY created_at, seq".formatted(COLUMNS);
        return jdbcTemplate.query(sql, ROW_MAPPER);
    }

    public List<File> findSince(long seq) {
        String sql = "SELECT %s FROM files WHERE seq > ? ORDER BY seq".formatted(COLUMNS);
        return jdbcTemplate.query(sql, ROW_MAPPER, seq);
    }

    public List<File> findLatest(int n) {
        int limit = Math.clamp(n, 1, 100);
        String sql = "SELECT %s FROM files ORDER BY created_at DESC, seq DESC LIMIT ?".formatted(COLUMNS);
        return jdbcTemplate.query(sql, ROW_MAPPER, limit);
    }

    public Optional<File> findById(String id) {
        String sql = "SELECT %s FROM files WHERE id = ?".formatted(COLUMNS);
        try {
            return jdbcTemplate.query(sql, ROW_MAPPER, id).stream().findFirst();
        } catch (DataAccessException e) {
            log.error("Failed to retrieve file with id: {}", id, e);
            throw e;
        }
    }

    @Override
    public boolean existsById(String id) {
        String sql = "SELECT EXISTS (SELECT 1 FROM files WHERE id = ?)";
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(sql, Boolean.class, id));
    }

    public void create(File file) {
        String sql = """
                INSERT INTO files (id, kind, text, name, mime, size, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                RETURNING seq
                """;
        Long seq = jdbcTemplate.queryForObject(sql, Long.class,
                file.getId(),
                file.getKind(),
                file.getText(),
                file.getName(),
                file.getMime(),
                file.getSize(),
                file.getCreatedAt());
        file.setSeq(seq);
    }

    public boolean update(File file) {
        String sql = """
                UPDATE files
                SET kind = ?, text = ?, name = ?, mime = ?, size = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql,
                file.getKind(),
                file.getText(),
                file.getName(),
                file.getMime(),
                file.getSize(),
                file.getId()) > 0;
    }

    public boolean deleteById(String id) {
        String sql = "DELETE FROM files WHERE id = ?";
        return jdbcTemplate.update(sql, id) > 0;
    }
}
