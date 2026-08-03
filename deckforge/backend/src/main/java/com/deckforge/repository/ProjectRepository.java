package com.deckforge.repository;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 项目主数据仓库：projects / outlines / styles / templates / conversations / snapshots / exports。
 * 单机 SQLite，WAL 场景下读写安全。
 */
@Repository
public class ProjectRepository {

    private final JdbcTemplate jdbc;

    public ProjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ---------- projects ----------

    public static class Project {
        public Long id;
        public String name;
        public Long templateId;
        public String inputText;
        public String inputFile;
        public String status;
        public String theme;
        public String aspectRatio;
    }

    private final RowMapper<Project> projectMapper = (rs, i) -> {
        Project p = new Project();
        p.id = rs.getLong("id");
        p.name = rs.getString("name");
        p.templateId = rs.getLong("template_id");
        p.inputText = rs.getString("input_text");
        p.inputFile = rs.getString("input_file");
        p.status = rs.getString("status");
        p.theme = rs.getString("theme");
        p.aspectRatio = rs.getString("aspect_ratio");
        return p;
    };

    public Long createProject(String name, Long templateId, String inputText, String inputFile, String theme) {
        jdbc.update("INSERT INTO projects(name, template_id, input_text, input_file, status, theme) VALUES(?,?,?,?, 'draft', ?)",
                name, templateId, inputText, inputFile, theme);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    public Project project(Long id) {
        List<Project> list = jdbc.query("SELECT * FROM projects WHERE id=?", projectMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public void updateProjectStatus(Long id, String status) {
        jdbc.update("UPDATE projects SET status=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", status, id);
    }

    public void setAspectRatio(Long id, String ratio) {
        jdbc.update("UPDATE projects SET aspect_ratio=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", ratio, id);
    }

    public void setTheme(Long id, String theme) {
        jdbc.update("UPDATE projects SET theme=?, updated_at=CURRENT_TIMESTAMP WHERE id=?", theme, id);
    }

    public List<Project> listProjects() {
        return jdbc.query("SELECT * FROM projects ORDER BY id DESC", projectMapper);
    }

    // ---------- outlines ----------

    public void saveOutline(Long projectId, String json) {
        jdbc.update("DELETE FROM outlines WHERE project_id=?", projectId);
        jdbc.update("INSERT INTO outlines(project_id, json) VALUES(?,?)", projectId, json);
    }

    public String loadOutline(Long projectId) {
        List<String> list = jdbc.query("SELECT json FROM outlines WHERE project_id=? ORDER BY id DESC LIMIT 1",
                (rs, i) -> rs.getString(1), projectId);
        return list.isEmpty() ? "{}" : list.get(0);
    }

    // ---------- styles ----------

    public void saveStyle(Long projectId, String json, String source) {
        jdbc.update("DELETE FROM styles WHERE project_id=?", projectId);
        jdbc.update("INSERT INTO styles(project_id, json, source) VALUES(?,?,?)", projectId, json, source);
    }

    public String loadStyle(Long projectId) {
        List<String> list = jdbc.query("SELECT json FROM styles WHERE project_id=? ORDER BY id DESC LIMIT 1",
                (rs, i) -> rs.getString(1), projectId);
        return list.isEmpty() ? "{}" : list.get(0);
    }

    // ---------- templates ----------

    public static class Template {
        public Long id;
        public String name;
        public String sourceType;
        public String sourceFile;
        public String skeletonJson;
        public String paletteJson;
        public boolean recognized;
        public boolean builtin;
    }

    private final RowMapper<Template> tplMapper = (rs, i) -> {
        Template t = new Template();
        t.id = rs.getLong("id");
        t.name = rs.getString("name");
        t.sourceType = rs.getString("source_type");
        t.sourceFile = rs.getString("source_file");
        t.skeletonJson = rs.getString("skeleton_json");
        t.paletteJson = rs.getString("palette_json");
        t.recognized = rs.getInt("recognized") == 1;
        t.builtin = rs.getInt("is_builtin") == 1;
        return t;
    };

    public List<Template> listTemplates() {
        return jdbc.query("SELECT * FROM templates ORDER BY is_builtin DESC, id", tplMapper);
    }

    public Template template(Long id) {
        List<Template> list = jdbc.query("SELECT * FROM templates WHERE id=?", tplMapper, id);
        return list.isEmpty() ? null : list.get(0);
    }

    public Template builtinDefault() {
        List<Template> list = jdbc.query("SELECT * FROM templates WHERE is_builtin=1 ORDER BY id LIMIT 1", tplMapper);
        return list.isEmpty() ? null : list.get(0);
    }

    public Long saveTemplate(String name, String sourceType, String sourceFile, String skeletonJson,
                             String paletteJson, boolean recognized) {
        jdbc.update("INSERT INTO templates(name, source_type, source_file, skeleton_json, palette_json, recognized, is_builtin) VALUES(?,?,?,?,?,?,0)",
                name, sourceType, sourceFile, skeletonJson, paletteJson, recognized ? 1 : 0);
        return jdbc.queryForObject("SELECT last_insert_rowid()", Long.class);
    }

    // ---------- conversations ----------

    public void upsertConversation(Long projectId, String stage, String historyJson, int syncLocalStyle) {
        List<Long> ids = jdbc.query("SELECT id FROM conversations WHERE project_id=? LIMIT 1",
                (rs, i) -> rs.getLong(1), projectId);
        if (ids.isEmpty()) {
            jdbc.update("INSERT INTO conversations(project_id, stage, history_json, sync_local_style, version) VALUES(?,?,?,?,1)",
                    projectId, stage, historyJson, syncLocalStyle);
        } else {
            jdbc.update("UPDATE conversations SET stage=?, history_json=?, sync_local_style=?, updated_at=CURRENT_TIMESTAMP WHERE project_id=?",
                    stage, historyJson, syncLocalStyle, projectId);
        }
    }

    public String loadConversationHistory(Long projectId) {
        List<String> list = jdbc.query("SELECT history_json FROM conversations WHERE project_id=? LIMIT 1",
                (rs, i) -> rs.getString(1), projectId);
        return list.isEmpty() ? "[]" : list.get(0);
    }

    public int loadSyncLocalStyle(Long projectId) {
        List<Integer> list = jdbc.query("SELECT sync_local_style FROM conversations WHERE project_id=? LIMIT 1",
                (rs, i) -> rs.getInt(1), projectId);
        return list.isEmpty() ? 0 : list.get(0);
    }

    public void setSyncLocalStyle(Long projectId, int val) {
        jdbc.update("UPDATE conversations SET sync_local_style=?, updated_at=CURRENT_TIMESTAMP WHERE project_id=?", val, projectId);
    }

    // ---------- snapshots ----------

    public static class Snapshot {
        public Long id;
        public String kind;
        public String payloadJson;
        public int seq;
    }

    private final RowMapper<Snapshot> snapMapper = (rs, i) -> {
        Snapshot s = new Snapshot();
        s.id = rs.getLong("id");
        s.kind = rs.getString("kind");
        s.payloadJson = rs.getString("payload_json");
        s.seq = rs.getInt("seq");
        return s;
    };

    public void pushSnapshot(Long projectId, String kind, JsonNode payload) {
        String json = payload.toString();
        // 计算下一个 seq
        int nextSeq = 1;
        List<Integer> seqs = jdbc.query("SELECT seq FROM snapshots WHERE project_id=? AND kind=? ORDER BY seq DESC LIMIT 1",
                (rs, i) -> rs.getInt(1), projectId, kind);
        if (!seqs.isEmpty()) nextSeq = seqs.get(0) + 1;
        jdbc.update("INSERT INTO snapshots(project_id, kind, payload_json, seq) VALUES(?,?,?,?)",
                projectId, kind, json, nextSeq);
        trimSnapshots(projectId, kind);
    }

    /** 仅保留最近 5 档 */
    private void trimSnapshots(Long projectId, String kind) {
        List<Integer> seqs = jdbc.query(
                "SELECT seq FROM snapshots WHERE project_id=? AND kind=? ORDER BY seq DESC",
                (rs, i) -> rs.getInt(1), projectId, kind);
        if (seqs.size() > 5) {
            for (int i = 5; i < seqs.size(); i++) {
                jdbc.update("DELETE FROM snapshots WHERE project_id=? AND kind=? AND seq=?",
                        projectId, kind, seqs.get(i));
            }
        }
    }

    public List<Snapshot> listSnapshots(Long projectId, String kind) {
        return jdbc.query("SELECT * FROM snapshots WHERE project_id=? AND kind=? ORDER BY seq",
                snapMapper, projectId, kind);
    }

    public Snapshot snapshotBySeq(Long projectId, String kind, int seq) {
        List<Snapshot> list = jdbc.query("SELECT * FROM snapshots WHERE project_id=? AND kind=? AND seq=?",
                snapMapper, projectId, kind, seq);
        return list.isEmpty() ? null : list.get(0);
    }

    // ---------- exports ----------

    public void addExport(Long projectId, String filePath, long size) {
        jdbc.update("INSERT INTO exports(project_id, file_path, file_size) VALUES(?,?,?)",
                projectId, filePath, size);
    }

    public List<String> listExports(Long projectId) {
        return jdbc.query("SELECT file_path FROM exports WHERE project_id=? ORDER BY id DESC",
                (rs, i) -> rs.getString(1), projectId);
    }
}
