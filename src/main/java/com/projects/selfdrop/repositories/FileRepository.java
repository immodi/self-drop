package com.projects.selfdrop.repositories;

import com.projects.selfdrop.entities.File;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class FileRepository {
    private static final BeanPropertyRowMapper<File> ROW_MAPPER = new BeanPropertyRowMapper<>(File.class);
    private static final String COLUMNS =
            "seq, id, kind, text, name, mime, size, device, created_at";
    private static final Logger log = LoggerFactory.getLogger(FileRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public FileRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<File> findAll() {
        String sql = "SELECT %s FROM files ORDER BY created_at DESC, seq DESC".formatted(COLUMNS);
        return jdbcTemplate.query(sql, ROW_MAPPER).stream().toList();
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

    public void create(File file) {
        String sql = """
                INSERT INTO files (id, kind, text, name, mime, size, device, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING seq
                """;
        Long seq = jdbcTemplate.queryForObject(sql, Long.class,
                file.getId(),
                file.getKind(),
                file.getText(),
                file.getName(),
                file.getMime(),
                file.getSize(),
                file.getDevice(),
                file.getCreatedAt());
        file.setSeq(seq);
    }

    public boolean update(File file) {
        String sql = """
                UPDATE files
                SET kind = ?, text = ?, name = ?, mime = ?, size = ?, device = ?
                WHERE id = ?
                """;
        return jdbcTemplate.update(sql,
                file.getKind(),
                file.getText(),
                file.getName(),
                file.getMime(),
                file.getSize(),
                file.getDevice(),
                file.getId()) > 0;
    }

    public boolean deleteById(String id) {
        String sql = "DELETE FROM files WHERE id = ?";
        return jdbcTemplate.update(sql, id) > 0;
    }
}
