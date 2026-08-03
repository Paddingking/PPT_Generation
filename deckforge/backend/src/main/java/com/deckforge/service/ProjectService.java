package com.deckforge.service;

import com.deckforge.engine.ContentEngine;
import com.deckforge.engine.JsonUtil;
import com.deckforge.engine.PptxGenerator;
import com.deckforge.engine.StyleEngine;
import com.deckforge.engine.TemplateExtractor;
import com.deckforge.model.Dtos;
import com.deckforge.model.TemplateSkeleton;
import com.deckforge.repository.ConfigRepository;
import com.deckforge.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目服务：编排"输入→内容→样式→持久化→导出→模板"等核心链路。
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository repo;
    private final ConfigRepository configRepo;
    private final ContentEngine contentEngine;
    private final StyleEngine styleEngine;
    private final TemplateExtractor templateExtractor;
    private final PptxGenerator pptxGenerator;

    public ProjectService(ProjectRepository repo, ConfigRepository configRepo,
                          ContentEngine contentEngine, StyleEngine styleEngine,
                          TemplateExtractor templateExtractor, PptxGenerator pptxGenerator) {
        this.repo = repo;
        this.configRepo = configRepo;
        this.contentEngine = contentEngine;
        this.styleEngine = styleEngine;
        this.templateExtractor = templateExtractor;
        this.pptxGenerator = pptxGenerator;
    }

    /** 创建项目 + 首次生成（阶段A约束或跳过直接生成） */
    public Map<String, Object> generate(Dtos.GenerateRequest req) {
        // 1. 确定模板
        ProjectRepository.Template tpl = resolveTemplate(req.getTemplateId());
        TemplateSkeleton skeleton = skeletonOf(tpl);

        String theme = req.getStyle() != null ? req.getStyle() : "corporate";
        // 2. 建项目
        Long projectId = repo.createProject(guessName(req.getIntent()), tpl != null ? tpl.id : null,
                req.getIntent(), req.getFilePath(), theme);

        // 3. 约束集（阶段A）
        ObjectNode constraints = JsonUtil.MAPPER.createObjectNode();
        if (req.getStyle() != null) constraints.put("style", req.getStyle());
        if (req.getPageCount() != null) constraints.put("pageCount", req.getPageCount());
        if (req.getDensity() != null) constraints.put("density", req.getDensity());
        if (req.getExtraConstraint() != null) constraints.put("extra", req.getExtraConstraint());
        constraints.put("templateId", tpl != null ? tpl.id : 1);

        // 4. 内容通道（带约束）
        String intentInput = buildIntentInput(req);
        JsonNode content = contentEngine.generate(intentInput, constraints.toString());

        // 5. 样式通道（依赖内容）
        JsonNode style = styleEngine.generate(skeleton, content, req.getStyle());

        // 6. 持久化
        repo.saveOutline(projectId, content.toString());
        repo.saveStyle(projectId, style.toString(), "llm");
        repo.upsertConversation(projectId, "CONSTRAIN", "[]", 0);
        repo.updateProjectStatus(projectId, "preview");

        Map<String, Object> result = new HashMap<>();
        result.put("projectId", projectId);
        result.put("content", content);
        result.put("style", style);
        result.put("template", tpl);
        return result;
    }

    private String buildIntentInput(Dtos.GenerateRequest req) {
        StringBuilder sb = new StringBuilder();
        if (req.getIntent() != null) sb.append(req.getIntent());
        if (req.getExtraConstraint() != null && !req.getExtraConstraint().isEmpty()) {
            sb.append("\n约束补充：").append(req.getExtraConstraint());
        }
        return sb.toString().trim();
    }

    /** 解析模板：未指定用内置默认。 */
    public ProjectRepository.Template resolveTemplate(String templateId) {
        if (templateId != null && !templateId.isEmpty()) {
            try {
                ProjectRepository.Template t = repo.template(Long.parseLong(templateId));
                if (t != null) return t;
            } catch (Exception ignored) {}
        }
        ProjectRepository.Template def = repo.builtinDefault();
        return def;
    }

    public TemplateSkeleton skeletonOf(ProjectRepository.Template tpl) {
        if (tpl == null || tpl.skeletonJson == null || tpl.skeletonJson.trim().isEmpty()) {
            return builtinSkeleton();
        }
        String json = tpl.skeletonJson;
        ObjectNode node = JsonUtil.parseObject(json);
        TemplateSkeleton sk = new TemplateSkeleton();
        if (node != null) {
            // 容错：layoutGroups 值可能是"单对象"或"数组"，归一化为数组，避免反序列化失败
            if (node.has("layoutGroups") && node.get("layoutGroups").isObject()) {
                ObjectNode lg = (ObjectNode) node.get("layoutGroups");
                java.util.Iterator<java.util.Map.Entry<String, JsonNode>> it = lg.fields();
                java.util.List<String> keysToFix = new java.util.ArrayList<>();
                it.forEachRemaining(e -> {
                    if (e.getValue().isObject()) keysToFix.add(e.getKey());
                });
                for (String k : keysToFix) {
                    ObjectNode single = (ObjectNode) lg.get(k);
                    com.fasterxml.jackson.databind.node.ArrayNode arr = JsonUtil.MAPPER.createArrayNode();
                    arr.add(single);
                    lg.set(k, arr);
                }
            }
            try {
                String normalized = JsonUtil.MAPPER.writeValueAsString(node);
                sk = JsonUtil.MAPPER.convertValue(JsonUtil.parse(normalized), TemplateSkeleton.class);
            } catch (Exception e) {
                log.warn("模板骨架反序列化失败，用默认: {}", e.getMessage());
            }
        }
        if (sk.getTypography() == null) sk.setTypography(new TemplateSkeleton.Typography());
        if (sk.getPalette() == null) sk.setPalette(new TemplateSkeleton.Palette());
        // 兜底 palette：若模板 palettes_json 有
        if (tpl.paletteJson != null && (sk.getPalette().getPrimary() == null)) {
            ObjectNode pn = JsonUtil.parseObject(tpl.paletteJson);
            if (pn != null && pn.has("primary")) sk.setPalette(JsonUtil.MAPPER.convertValue(pn, TemplateSkeleton.Palette.class));
        }
        if (sk.getPalette().getPrimary() == null) {
            sk.getPalette().setPrimary("#185FA5");
            sk.getPalette().setBackground("#FFFFFF");
            sk.getPalette().setSecondary("#378ADD");
            sk.getPalette().setAccent("#E6F1FB");
            sk.getPalette().setTitleText("#0C2B4D");
            sk.getPalette().setBodyText("#20344D");
        }
        if (sk.getTypography().getTitleFont() == null) {
            sk.getTypography().setTitleFont("微软雅黑");
            sk.getTypography().setBodyFont("微软雅黑");
            sk.getTypography().setTitleSizePt(30);
            sk.getTypography().setBulletSizePt(18);
            sk.getTypography().setNoteSizePt(12);
        }
        sk.setRecognized(tpl.recognized);
        return sk;
    }

    private TemplateSkeleton builtinSkeleton() {
        return skeletonOf(repo.builtinDefault());
    }

    /** 上传并解析 .pptx 模板，提取骨架。失败则 recognized=false 并回退内置。 */
    public Map<String, Object> importTemplate(MultipartFile file) throws Exception {
        String original = file.getOriginalFilename();
        if (original == null || !original.toLowerCase().endsWith(".pptx")) {
            throw new IllegalArgumentException("仅支持 .pptx 文件");
        }
        File tmp = File.createTempFile("tpl_", ".pptx");
        file.transferTo(tmp);
        TemplateSkeleton sk = templateExtractor.extract(tmp.getAbsolutePath());
        String paletteJson = JsonUtil.MAPPER.writeValueAsString(sk.getPalette());
        String skeletonJson = JsonUtil.MAPPER.writeValueAsString(sk);
        String name = original.replace(".pptx", "") + " · 导入";
        Long id = repo.saveTemplate(name, "IMPORTED", tmp.getAbsolutePath(),
                skeletonJson, paletteJson, sk.isRecognized());
        ProjectRepository.Template saved = repo.template(id);
        Map<String, Object> result = new java.util.HashMap<>();
        result.put("template", saved);
        result.put("recognized", sk.isRecognized());
        result.put("skeleton", sk);
        result.put("fallback", !sk.isRecognized());
        return result;
    }

    /** 项目详情（含内容/样式/template 列表/快照列表）。 */
    public Map<String, Object> projectDetail(Long id) {
        ProjectRepository.Project p = repo.project(id);
        if (p == null) throw new IllegalArgumentException("项目不存在: " + id);
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("project", p);
        m.put("content", JsonUtil.parse(repo.loadOutline(id)));
        m.put("style", JsonUtil.parse(repo.loadStyle(id)));
        m.put("templates", repo.listTemplates());
        m.put("snapshots", repo.listSnapshots(id, "CONTENT"));
        m.put("conversationHistory", repo.loadConversationHistory(id));
        m.put("exports", repo.listExports(id));
        return m;
    }

    public List<ProjectRepository.Template> listTemplates() {
        return repo.listTemplates();
    }

    /** 项目列表（原始）. */
    public List<ProjectRepository.Project> listRaw() {
        return repo.listProjects();
    }

    public ProjectRepository.Project getProject(Long id) {
        return repo.project(id);
    }

    public List<String> listExports(Long id) {
        return repo.listExports(id);
    }


    public void setAspectRatio(Long id, String ratio) {
        repo.setAspectRatio(id, ratio);
    }

    /** 导出可编辑 PPTX。 */
    public String export(Long projectId, String exportDir) throws Exception {
        JsonNode content = JsonUtil.parse(repo.loadOutline(projectId));
        JsonNode style = JsonUtil.parse(repo.loadStyle(projectId));
        if (content == null || !content.has("slides") || content.get("slides").size() == 0) {
            throw new IllegalArgumentException("项目大纲为空，无法导出。请先生成内容。");
        }
        File dir = new File(exportDir);
        if (!dir.exists()) dir.mkdirs();
        String fileName = "DeckForge_" + System.currentTimeMillis() + ".pptx";
        String outPath = new File(exportDir, fileName).getAbsolutePath();
        String finalPath = pptxGenerator.generate(content, style, outPath);
        File f = new File(finalPath);
        repo.addExport(projectId, finalPath, f.exists() ? f.length() : 0);
        repo.updateProjectStatus(projectId, "exported");
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("file", fileName);
        m.put("path", finalPath);
        m.put("size", f.exists() ? f.length() : 0);
        return finalPath;
    }

    /** 尝试文件名推导。 */
    private String guessName(String intent) {
        if (intent == null || intent.trim().isEmpty()) return "未命名项目";
        String t = intent.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("「(.+?)」|《(.+?)》|\"(.+?)\"").matcher(t);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            if (g != null && g.length() > 2) return g.length() > 30 ? g.substring(0, 30) : g;
        }
        return t.length() > 30 ? t.substring(0, 30) : t;
    }
}
