package com.deckforge.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * LLM 配置仓库（llm_configs 表）。
 */
@Repository
public class ConfigRepository {

    private final JdbcTemplate jdbc;

    public ConfigRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public static class LlmConfig {
        public Long id;
        public String name;
        public String protocol;
        public String baseUrl;
        public String apiKey;
        public String model;
        public boolean active;
        public boolean anonymize;
    }

    private final RowMapper<LlmConfig> mapper = (rs, i) -> {
        LlmConfig c = new LlmConfig();
        c.id = rs.getLong("id");
        c.name = rs.getString("name");
        c.protocol = rs.getString("protocol");
        c.baseUrl = rs.getString("base_url");
        c.apiKey = rs.getString("api_key");
        c.model = rs.getString("model");
        c.active = rs.getInt("is_active") == 1;
        return c;
    };

    public List<LlmConfig> all() {
        return jdbc.query("SELECT * FROM llm_configs ORDER BY id", mapper);
    }

    public LlmConfig activeConfig() {
        List<LlmConfig> list = jdbc.query(
                "SELECT * FROM llm_configs WHERE is_active=1 LIMIT 1", mapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public LlmConfig byId(Long id) {
        List<LlmConfig> list = jdbc.query("SELECT * FROM llm_configs WHERE id=?", mapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public long save(LlmConfig c) {
        if (c.id == null) {
            jdbc.update("INSERT INTO llm_configs(name, protocol, base_url, api_key, model, is_active) VALUES(?,?,?,?,?,?)",
                    c.name, c.protocol, c.baseUrl, c.apiKey, c.model, c.active ? 1 : 0);
            return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
        } else {
            jdbc.update("UPDATE llm_configs SET name=?, protocol=?, base_url=?, api_key=?, model=?, is_active=? WHERE id=?",
                    c.name, c.protocol, c.baseUrl, c.apiKey, c.model, c.active ? 1 : 0, c.id);
            return c.id;
        }
    }

    /** 将指定配置设为激活（其余置 0）。 */
    public void setActive(Long id) {
        jdbc.update("UPDATE llm_configs SET is_active=0");
        jdbc.update("UPDATE llm_configs SET is_active=1 WHERE id=?", id);
    }

    public void delete(Long id) {
        jdbc.update("DELETE FROM llm_configs WHERE id=?", id);
    }
}
