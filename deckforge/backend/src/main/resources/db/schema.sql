-- ============================================================
-- DeckForge · PPT 工作台 - SQLite Schema
-- 对应 PRD 5.7 / 5.9.7 数据模型
-- ============================================================

-- 项目（一次 PPT 制作任务）
CREATE TABLE IF NOT EXISTS projects (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        TEXT NOT NULL,
    template_id INTEGER,
    input_text  TEXT,
    input_file  TEXT,
    status      TEXT DEFAULT 'draft',
    theme       TEXT DEFAULT 'corporate',
    aspect_ratio TEXT DEFAULT '16:9',
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP
);

-- 大纲（内容 JSON 整体存储，单一事实来源）
CREATE TABLE IF NOT EXISTS outlines (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    json       TEXT NOT NULL,
    version    INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP
);

-- 样式/设计决策（格式通道单一事实来源）
CREATE TABLE IF NOT EXISTS styles (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    json       TEXT NOT NULL,
    source     TEXT DEFAULT 'llm',
    version    INTEGER DEFAULT 1,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 模板（外部导入或内置预设）
CREATE TABLE IF NOT EXISTS templates (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          TEXT NOT NULL,
    source_type   TEXT NOT NULL,
    source_file   TEXT,
    skeleton_json TEXT NOT NULL,
    palette_json  TEXT,
    recognized    INTEGER DEFAULT 1,
    is_builtin    INTEGER DEFAULT 0,
    created_at    TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- LLM 配置（支持多套，运行时切换）
CREATE TABLE IF NOT EXISTS llm_configs (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    name       TEXT NOT NULL,
    protocol   TEXT NOT NULL,
    base_url   TEXT NOT NULL,
    api_key    TEXT,
    model      TEXT,
    is_active  INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 导出记录
CREATE TABLE IF NOT EXISTS exports (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id INTEGER NOT NULL,
    file_path  TEXT NOT NULL,
    file_size  INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 对话会话（阶段A约束 / 阶段B微调）
CREATE TABLE IF NOT EXISTS conversations (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id       INTEGER NOT NULL,
    stage            TEXT NOT NULL,
    history_json     TEXT NOT NULL,
    sync_local_style INTEGER DEFAULT 0,
    version          INTEGER DEFAULT 1,
    created_at       TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at       TIMESTAMP
);

-- 快照（撤销用，保留最近 5 档）
CREATE TABLE IF NOT EXISTS snapshots (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    project_id  INTEGER NOT NULL,
    kind        TEXT NOT NULL,
    payload_json TEXT NOT NULL,
    seq         INTEGER NOT NULL,
    created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_outlines_project ON outlines(project_id);
CREATE INDEX IF NOT EXISTS idx_styles_project ON styles(project_id);
CREATE INDEX IF NOT EXISTS idx_conversations_project ON conversations(project_id);
CREATE INDEX IF NOT EXISTS idx_snapshots_project ON snapshots(project_id);

-- 内置预设模板基线（首次启动 seed）
INSERT OR IGNORE INTO templates (id, name, source_type, skeleton_json, palette_json, recognized, is_builtin)
VALUES
(1, '商务蓝 · 稳重', 'BUILTIN', '{"aspectRatio":"16:9","masterWidth":960,"masterHeight":540,"layoutGroups":{"title":[{"type":"TITLE","x":0.06,"y":0.06,"w":0.88,"h":0.14}],"content":[{"type":"CONTENT","x":0.08,"y":0.24,"w":0.84,"h":0.7}]}}',
 '{"background":"#FFFFFF","primary":"#185FA5","secondary":"#378ADD","accent":"#E6F1FB","titleText":"#0C2B4D","bodyText":"#20344D"}', 1, 1),
(2, '暖橙 · 进取', 'BUILTIN', '{"aspectRatio":"16:9","masterWidth":960,"masterHeight":540,"layoutGroups":{"title":[{"type":"TITLE","x":0.06,"y":0.06,"w":0.88,"h":0.14}],"content":[{"type":"CONTENT","x":0.08,"y":0.24,"w":0.84,"h":0.7}]}}',
 '{"background":"#FFF7F0","primary":"#B8502E","secondary":"#FF9D66","accent":"#FFE4D1","titleText":"#7A2E14","bodyText":"#4A342A"}', 1, 1),
(3, '翠绿 · 生机', 'BUILTIN', '{"aspectRatio":"16:9","masterWidth":960,"masterHeight":540,"layoutGroups":{"title":[{"type":"TITLE","x":0.06,"y":0.06,"w":0.88,"h":0.14}],"content":[{"type":"CONTENT","x":0.08,"y":0.24,"w":0.84,"h":0.7}]}}',
 '{"background":"#FFFFFF","primary":"#2E8B57","secondary":"#6BBF8A","accent":"#E3F3E8","titleText":"#1C5340","bodyText":"#27402F"}', 1, 1),
(4, '黑白 · 极简', 'BUILTIN', '{"aspectRatio":"16:9","masterWidth":960,"masterHeight":540,"layoutGroups":{"title":[{"type":"TITLE","x":0.06,"y":0.06,"w":0.88,"h":0.14}],"content":[{"type":"CONTENT","x":0.08,"y":0.24,"w":0.84,"h":0.7}]}}',
 '{"background":"#FFFFFF","primary":"#1A1A1A","secondary":"#555555","accent":"#F2F2F2","titleText":"#000000","bodyText":"#333333"}', 1, 1);
