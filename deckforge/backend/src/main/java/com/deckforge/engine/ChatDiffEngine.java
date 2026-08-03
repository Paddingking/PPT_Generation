package com.deckforge.engine;

import com.deckforge.llm.ChatRequest;
import com.deckforge.llm.LlmProvider;
import com.deckforge.llm.LlmProviderFactory;
import com.deckforge.model.Dtos;
import com.deckforge.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话式多轮优化引擎（PRD 5.9 / 技术说明 §7）。
 * 阶段B：用户自然语言指令 -> 意图路由 -> LLM 产出最小 diff -> 校验合并 -> 一致性规则 -> 快照。
 *
 * 核心铁律：最小 diff 合并，绝不让 LLM 重发整份 JSON（防越界、保用户定制）。
 */
@Service
public class ChatDiffEngine {

    private static final Logger log = LoggerFactory.getLogger(ChatDiffEngine.class);

    private final LlmProviderFactory factory;
    private final ProjectRepository repo;
    private final IntentRouter intentRouter;

    public ChatDiffEngine(LlmProviderFactory factory, ProjectRepository repo, IntentRouter intentRouter) {
        this.factory = factory;
        this.repo = repo;
        this.intentRouter = intentRouter;
    }

    public static class DiffResult {
        public ObjectNode contentJson;
        public ObjectNode styleJson;
        public List<String> appliedPatches;   // 已应用 patch 描述（前端 diff 展示）
        public List<String> rejectedPatches;  // 被拒绝 patch
        public String askUser;                // null 无需询问；否则表示询问类型（如 global-style）
        public IntentRouter.Intent intent;

        public DiffResult() {
            appliedPatches = new ArrayList<>();
            rejectedPatches = new ArrayList<>();
        }
    }

    /**
     * 处理一轮预览微调对话。
     */
    public DiffResult polish(Long projectId, String instruction, Integer focusPage, boolean syncLocalStyle) {
        ObjectNode content = JsonUtil.parseObject(repo.loadOutline(projectId));
        ObjectNode style = JsonUtil.parseObject(repo.loadStyle(projectId));
        if (content == null) content = JsonUtil.parseObject("{\"meta\":{},\"slides\":[]}");
        if (style == null) style = JsonUtil.parseObject("{}");

        DiffResult result = new DiffResult();
        IntentRouter.Intent intent = intentRouter.classify(instruction);
        result.intent = intent;

        // 一致性规则判定：是否触发全局询问
        int sameStyleHits = countStyleHits(projectId, instruction);
        String ask = decideAsk(intent, syncLocalStyle, sameStyleHits);
        result.askUser = ask;

        // 快照（合并前）；多轮直接 push 当前快照
        repo.pushSnapshot(projectId, "CONTENT", content);
        repo.pushSnapshot(projectId, "STYLE", style);

        // 调 LLM 产出 diff
        List<Dtos.Patch> patches = callLlmForPatches(projectId, content, style, instruction, focusPage, intent);
        if (patches.isEmpty()) {
            result.contentJson = content;
            result.styleJson = style;
            return result;
        }

        // 逐条校验 + 合并
        for (Dtos.Patch p : patches) {
            boolean isStyleDomain = isStylePatch(p.getPath());
            PatchValidator.Result v = PatchValidator.validate(p, isStyleDomain ? style : content, isStyleDomain);
            if (!v.pass) {
                result.rejectedPatches.add(p.getPath() + " => " + v.reason);
                log.debug("diff 拒绝: {} - {}", p.getPath(), v.reason);
                continue;
            }
            JsonNode value = JsonUtil.toNode(p.getValue());
            String op = p.getOp() == null ? "replace" : p.getOp();
            if (isStyleDomain) {
                applyTo(style, p.getPath(), op, value);
                // 一致性：若 syncLocalStyle/全局意图，且改的是样式，扩展为全局（简单：直接改 palette/typography）
                if ((intent == IntentRouter.Intent.GLOBAL_STYLE || syncLocalStyle) &&
                        (p.getPath().startsWith("palette") || p.getPath().startsWith("typography"))) {
                    // 已是全局字段，无需额外扩展
                }
            } else {
                applyTo(content, p.getPath(), op, value);
            }
            result.appliedPatches.add(describe(p));
        }

        // 保存更新后的 JSON + 会话历史
        repo.saveOutline(projectId, content.toString());
        repo.saveStyle(projectId, style.toString(), "llm");
        appendHistory(projectId, instruction);

        result.contentJson = content;
        result.styleJson = style;
        return result;
    }

    /** 将单个 patch 应用到 JSON（replace/add/remove）。 */
    private void applyTo(ObjectNode root, String path, String op, JsonNode value) {
        switch (op) {
            case "add":
            case "replace":
                JsonUtil.setAtPath(root, path, value);
                break;
            case "remove":
                JsonUtil.removeAtPath(root, path);
                break;
            default:
                JsonUtil.setAtPath(root, path, value);
        }
    }

    private boolean isStylePatch(String path) {
        return path != null && (path.startsWith("palette") || path.startsWith("typography")
                || path.startsWith("layoutRules") || path.startsWith("meta.aspectRatio"));
    }

    /** 调用 LLM 产出最小 diff patch 列表。 */
    private List<Dtos.Patch> callLlmForPatches(Long projectId, JsonNode content, JsonNode style,
                                               String instruction, Integer focusPage, IntentRouter.Intent intent) {
        LlmProvider provider = factory.provider();
        ChatRequest req = new ChatRequest();
        req.setSystemPrompt(Prompts.polishSystem());
        String history = repo.loadConversationHistory(projectId);
        req.setUserContent("当前内容JSON:\n" + safeJson(content)
                + "\n\n当前样式JSON:\n" + safeJson(style)
                + "\n\n意图路由结果: " + intent.name()
                + "\n聚焦页: " + (focusPage == null ? "无（由系统判断）" : focusPage)
                + "\n多轮历史: " + history
                + "\n\n<<<INSTRUCTION>>>" + instruction + "<<<INSTRUCTION>>>"
                + "\n标记：polish-diff");

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String raw = provider.chat(req);
                JsonNode parsed = JsonUtil.extractJsonFromText(raw);
                parsed = parsed != null ? parsed : JsonUtil.parse(raw);
                if (parsed != null && parsed.has("patches") && parsed.get("patches").isArray()) {
                    return parsePatches(parsed.get("patches"));
                }
                log.warn("diff 解析异常(第{}次)", attempt + 1);
            } catch (Exception e) {
                log.warn("diff 调用异常(第{}次): {}", attempt + 1, e.getMessage());
            }
        }
        return new ArrayList<>();
    }

    private List<Dtos.Patch> parsePatches(JsonNode arr) {
        List<Dtos.Patch> out = new ArrayList<>();
        for (JsonNode n : arr) {
            Dtos.Patch p = new Dtos.Patch();
            p.setPath(n.has("path") ? n.get("path").asText() : null);
            p.setOp(n.has("op") ? n.get("op").asText() : "replace");
            if (n.has("value")) p.setValue(JsonUtil.MAPPER.convertValue(n.get("value"), Object.class));
            if (n.has("targetPage")) p.setTargetPage(n.get("targetPage").asInt());
            out.add(p);
        }
        return out;
    }

    /** 一致性规则决策（PRD 5.9.4）。 */
    private String decideAsk(IntentRouter.Intent intent, boolean syncLocalStyle, int sameStyleHits) {
        if (intent == IntentRouter.Intent.GLOBAL_STYLE) return null; // 必然全局，不询问
        if (syncLocalStyle) return null; // 开关开启，自动全局，不询问
        if (intent == IntentRouter.Intent.STYLE && sameStyleHits >= 2) {
            return "全局样式询问"; // 触发 ASK_USER
        }
        return null;
    }

    /** 同类样式连续修改命中计数（启发式：最近 2 条历史是否都是 STYLE）。 */
    private int countStyleHits(Long projectId, String currentInstruction) {
        String history = repo.loadConversationHistory(projectId);
        if (history == null || history.isEmpty()) return 0;
        // 简化：统计历史里含样式关键词的条数
        int count = 0;
        try {
            JsonNode arr = JsonUtil.MAPPER.readTree(history);
            if (arr.isArray()) {
                for (JsonNode n : arr) {
                    String c = n.path("content").asText("");
                    if (c.contains("颜色") || c.contains("色") || c.contains("字体") || c.contains("风格")) count++;
                }
            }
        } catch (Exception ignored) {}
        return count;
    }

    private void appendHistory(Long projectId, String instruction) {
        String history = repo.loadConversationHistory(projectId);
        ArrayNode arr;
        try {
            JsonNode parsed = JsonUtil.MAPPER.readTree(history);
            arr = parsed.isArray() ? (ArrayNode) parsed : JsonUtil.MAPPER.createArrayNode();
        } catch (Exception e) {
            arr = JsonUtil.MAPPER.createArrayNode();
        }
        int sync = repo.loadSyncLocalStyle(projectId);
        arr.addObject().put("role", "user").put("content", instruction);
        String json;
        try { json = JsonUtil.MAPPER.writeValueAsString(arr); }
        catch (Exception e) { json = "[]"; }
        repo.upsertConversation(projectId, "POLISH", json, sync);
    }

    private String describe(Dtos.Patch p) {
        return p.getOp() + " " + p.getPath() + " = " + (p.getValue() == null ? "null" : p.getValue().toString());
    }

    private String safeJson(JsonNode n) {
        try {
            return JsonUtil.MAPPER.writeValueAsString(n != null ? n : JsonUtil.MAPPER.createObjectNode());
        } catch (Exception e) {
            return "{}";
        }
    }
}
