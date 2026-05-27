package com.example.photoscore.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PhotoScoreSchemaInitializer {

    private final JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void ensureUserIsolationColumns() {
        addColumnIfMissing("photo_score_record", "user_id", "BIGINT NULL");
        addColumnIfMissing("photo_score_record", "username", "VARCHAR(64) NULL");
        addColumnIfMissing("user_account", "last_login_time", "DATETIME NULL");
        addIndexIfMissing("photo_score_record", "idx_photo_score_user_hash", "user_id, file_hash");
        addIndexIfMissing("photo_score_record", "idx_photo_score_user_created", "user_id, created_time");
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.COLUMNS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND COLUMN_NAME = ?
                    """, Integer.class, tableName, columnName);

            if (count != null && count > 0) {
                return;
            }

            jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
            log.info("已为表 {} 添加字段 {}", tableName, columnName);
        } catch (Exception e) {
            log.warn("检查或添加字段 {}.{} 失败，继续启动: {}", tableName, columnName, e.getMessage());
        }
    }

    private void addIndexIfMissing(String tableName, String indexName, String columns) {
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM information_schema.STATISTICS
                    WHERE TABLE_SCHEMA = DATABASE()
                      AND TABLE_NAME = ?
                      AND INDEX_NAME = ?
                    """, Integer.class, tableName, indexName);

            if (count != null && count > 0) {
                return;
            }

            jdbcTemplate.execute("CREATE INDEX " + indexName + " ON " + tableName + " (" + columns + ")");
            log.info("已为表 {} 添加索引 {}", tableName, indexName);
        } catch (Exception e) {
            log.warn("检查或添加索引 {}.{} 失败，继续启动: {}", tableName, indexName, e.getMessage());
        }
    }
}
