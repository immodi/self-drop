package com.projects.selfdrop.configs;

import com.projects.selfdrop.utils.IdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Brings databases created before chats existed up to the current schema.
 *
 * <p>Fresh databases get the full schema from {@code schema.sql}. SQLite has no
 * {@code ADD COLUMN IF NOT EXISTS}, so an existing {@code files} table is
 * upgraded here instead of in the idempotent script. Any files that predate
 * chats are moved into a single "General" chat so nothing disappears.</p>
 */
@Component
public class SchemaMigration implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(SchemaMigration.class);
    private static final String DEFAULT_CHAT_NAME = "General";

    private final JdbcTemplate jdbcTemplate;

    public SchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!hasColumn("files", "chat_id")) {
            jdbcTemplate.execute("ALTER TABLE files ADD COLUMN chat_id TEXT");
            log.info("Added chat_id column to files");
        }

        Integer orphans = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM files WHERE chat_id IS NULL", Integer.class);
        if (orphans != null && orphans > 0) {
            String chatId = IdGenerator.generate();
            jdbcTemplate.update(
                    "INSERT INTO chats (id, name, created_at) VALUES (?, ?, ?)",
                    chatId,
                    DEFAULT_CHAT_NAME,
                    Instant.now().getEpochSecond());
            jdbcTemplate.update("UPDATE files SET chat_id = ? WHERE chat_id IS NULL", chatId);
            log.info("Moved {} existing file(s) into chat '{}'", orphans, DEFAULT_CHAT_NAME);
        }

        jdbcTemplate.execute(
                "CREATE INDEX IF NOT EXISTS idx_files_chat ON files(chat_id, seq)");
    }

    private boolean hasColumn(String table, String column) {
        return jdbcTemplate.query(
                        "PRAGMA table_info(" + table + ")",
                        (rs, rowNum) -> rs.getString("name"))
                .stream()
                .anyMatch(column::equalsIgnoreCase);
    }
}
