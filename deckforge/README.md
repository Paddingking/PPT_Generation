# DeckForge · PPT 工作台

> AI PPT 生成工具（单机本地版）· 从一句话到一页页原生可编辑的 PPTX

**界面命名：**「PPT 工作台」｜ AI 助手自称「PPT 助手」｜ 品牌名 **DeckForge**

---

## 一、产品定位

把"输入想法 → 生成大纲 → 套用模板 → 优化内容与版式 → 预览微调 → 导出可编辑 PPTX"全流程半自动化。

- **单机本地**：数据不出本机（SQLite 本地存储），适合内容私密的个人 / 团队
- **双通道 LLM**：内容通道（润色）+ 格式通道（设计决策）彻底解耦
- **对话式多轮优化**：阶段A 生成前设约束 + 阶段B 预览微调（最小 diff 合并、防越界）
- **套用任意模板**：导入 .pptx 提取版式骨架，识别失败自动回退内置，不崩溃
- **原生可编辑导出**：逐 shape 落盘原生文本/形状，PowerPoint / WPS 可继续编辑（非图片）

---

## 二、快速启动

### 前置环境（已验证）
- **JDK 1.8**（本机 `1.8.0_492` 已验证）
- **Node.js 18+**（本机 `v22.22.2` 已验证）
- **Maven 3.9.x**（本机缓存于 `~/.m2/wrapper/dists/apache-maven-3.9.6-bin/…`）

### Windows 一键启动
```bat
start.bat
```
它将启动：
- 后端：`http://localhost:8090`（Spring Boot 2.7.18 / Apache POI 5.2.5 / SQLite）
- 前端：`http://localhost:5173`（Vue3 + Vite，`/api` 已代理到 8090）

### 手动启动（两条命令）
```bash
# 后端
cd deckforge/backend
java -jar target/deckforge-app.jar --server.port=8090

# 前端
cd deckforge/frontend
npm install   # 首次
npm run dev
```

> ⚠️ **重要**：启动后端时务必用 `--server.port=8090` 显式指定端口。
> 本机环境变量 `SERVER__PORT=0` 会让 Spring Boot random 端口，导致前端连不上。
> 请用 `start.bat` 或手动加参数。

---

## 三、核心使用流程（对应 PRD P0-1 ~ P0-14）

```
创作入口 ──▶ 模板库 ──▶ 预览编辑 ──▶ 导出
(意图+阶段A约束)  (内置/导入)   (阶段B微调+快照撤销)  (原生可编辑 PPTX)
```

### 每个环节的交互
1. **创作入口**：输入一句话/粘贴文本/上传 `.docx/.md/.txt`；可先与「PPT 助手」对话约定风格/页数/要点密度（阶段A），亦可跳过直接生成。点击「开始制作」→ 后端产出**内容 JSON + 版式 JSON 双稿**。
2. **模板库**：4 套内置预设（商务蓝/暖橙/翠绿/黑白）或导入自己的 `.pptx` 提取版式骨架；识别失败（纯背景图）→ 黄色横幅提示并回退内置。
3. **预览编辑（主战场）**：白纸画布渲染全部幻灯片缩略页；右侧**常驻** AI 对话面板做阶段B微调（支持「第3页标题更有力」「全文加深主色」等自然语言指令，按最小 diff 合并、越界保护）；底部快照撤销条（近5档 + 回到初稿）；改完一键导出。
4. **设置**：OpenAI 兼容 / Anthropic 兼容双协议、baseUrl 可自由配置、测试连接；内容润色/格式优化开关。**未配 API Key 时走内置 Mock Provider，可完整演示全链路。**

---

## 四、技术架构

```
┌─────────────────────────────┐
│  前端 Vue3 + Vite (5173)    │  创作入口/模板库/预览画布/对话面板/设置
└────────────┬────────────────┘  /api 代理
             ▼
┌─────────────────────────────┐
│  后端 Spring Boot 2.7 (8090)│
│  Controller → Service → DAO │
│  ├ LlmProvider(OpenAI/Anthro)│
│  ├ ContentEngine  → 内容 JSON│
│  ├ StyleEngine    → 版式 JSON│
│  ├ ChatDiffEngine → diff 合并│
│  ├ TemplateExtractor 骨架提取│
│  └ PptxGenerator  POI 生成   │
└────────────┬────────────────┘
             ▼ SQLite (deckforge.db)
   projects/outlines/styles/templates/llm_configs/conversations/snapshots/exports
```

### 关键设计（单一事实来源）
- **内容 JSON**（outlines 表）+ **样式/设计决策 JSON**（styles 表）= 两个独立事实来源
- LLM 先产内容（润色），再基于内容+骨架产样式（**有依赖序，不能并行**）
- 预览 = 内容+样式联合渲染，保证"预览所见即所得"
- 对话微调 = **最小增量 diff**（JSON Pointer path/op/value），绝不让 LLM 重发整份
- 快照撤销：snapshots 表保留近 5 档 + 回初始态

### 数据表
`projects / outlines / styles / templates / llm_configs / conversations / snapshots / exports`

---

## 五、验证清单（已完成）

| # | 能力 | 状态 |
|---|------|------|
| 1 | 多源输入（NL/.md/.txt/.docx 上传） | ✅ |
| 2 | LLM 大纲生成（mock/真 LLM 双通道） | ✅ |
| 3 | 内容 JSON Schema（单一事实来源） | ✅ |
| 4 | 预览渲染（16:9 画布 + 版式还原） | ✅ |
| 5 | 预览内编辑（对话微调驱动） | ✅ |
| 6 | PPTX 生成（POI，逐 shape 原生可编辑，0 图片） | ✅ |
| 7 | SQLite 持久化（重启可恢复） | ✅ |
| 8 | 模板导入 + 骨架提取 + 失败回退 | ✅ |
| 9 | 内容润色（保留原意不增删要点） | ✅ |
| 10 | 格式设计决策（非法值回退骨架默认） | ✅ |
| 11 | 对话两阶段（A约束 / B微调） | ✅ |
| 12 | 最小 diff 合并 + 越界校验 | ✅ |
| 13 | 局部/全局一致性（开关 + ASK_USER） | ✅ |
| 14 | 快照撤销（近5档 + 回初稿） | ✅ |

### Mock → 真实 LLM 切换
在「设置」页填 baseUrl + API Key + 模型，保存并设为激活即可。
- OpenAI 兼容：`POST {baseUrl}/chat/completions`
- Anthropic 兼容：`POST {baseUrl}/v1/messages`

---

## 六、目录结构

```
deckforge/
├── start.bat              一键启动
├── backend/               Spring Boot 2.7 后端
│   ├── pom.xml
│   └── src/main/java/com/deckforge/
│       ├── controller/    API 接口
│       ├── service/       业务编排
│       ├── engine/        Content/Style/Template/ChatDiff/Pptx 引擎
│       ├── llm/           Provider(OpenAI/Anthropic/Mock) + 工厂
│       ├── repository/    SQLite 数据访问
│       └── model/         数据模型
│   └── src/main/resources/
│       ├── application.yml
│       └── db/schema.sql
├── frontend/              Vue3 + Vite 前端
│   └── src/
│       ├── api.ts
│       ├── App.vue
│       └── views/         Entry/Library/Preview/Settings
└── docs/                  PRD + 技术实现说明
```

---

## 七、常见问题

**Q: 后端端口不是 8090？**
A: 本机有 `SERVER__PORT=0` 环境变量。必须 `--server.port=8090` 启动（start.bat 已处理）。

**Q: 不配 API Key 能用吗？**
A: 能。走内置 Mock Provider 可完整演示"生成→微调→快照→导出"全链路，方便先熟悉流程。

**Q: 导出的 PPTX 能再编辑吗？**
A: 能。全部是原生文本/形状（实测 0 图片、每页含 `a:txBody`），PowerPoint/WPS 可改。

**Q: 内置模板 JSON 格式？**
A: schema.sql 里 `layoutGroups` 值为**数组**（`{"title":[...], "content":[...]}`）；后端有容错，兼容旧单对象格式。

---

## 八、文档
- `docs/PPT生成工具-PRD.md` — 完整产品规格（v1.3）
- `docs/技术实现补充说明.md` — 工程评审细节（jdk8 选型/双通道/可编辑落盘/mock先行）
