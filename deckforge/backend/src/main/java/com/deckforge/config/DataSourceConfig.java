package com.deckforge.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import javax.sql.DataSource;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;

/**
 * SQLite 数据源配置。
 * 启动时确保数据目录存在并初始化 schema。
 */
@Configuration
public class DataSourceConfig {

    @Value("${deckforge.data.dir:./data}")
    private String dataDir;

    @Value("${deckforge.data.db-name:deckforge.db}")
    private String dbName;

    @Bean
    public DataSource dataSource(DataSourceProperties props) {
        File dir = new File(dataDir);
        if (!dir.exists()) {
            //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        }
        String dbPath = new File(dir, dbName).getAbsolutePath();
        // JDBC URL 转成文件路径形式
        String jdbcUrl = "jdbc:sqlite:" + dbPath.replace("\\", "/");

        DataSource ds = DataSourceBuilder.create()
                .driverClassName("org.sqlite.JDBC")
                .url(jdbcUrl)
                .build();

        initSchema(ds);
        return ds;
    }

    private void initSchema(DataSource ds) {
        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(true);
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema.sql"));
        } catch (Exception e) {
            throw new IllegalStateException("SQLite Schema 初始化失败", e);
        }
    }
}
