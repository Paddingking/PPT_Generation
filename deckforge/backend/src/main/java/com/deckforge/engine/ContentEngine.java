package com.deckforge.engine;

import com.deckforge.llm.ChatRequest;
import com.deckforge.llm.LlmProvider;
import com.deckforge.llm.LlmProviderFactory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 内容通道引擎（PRD §6.1）：调用 LLM 产出结构化内容 JSON。
 * 校验失败自动重试 1 次，仍失败给降级提示。
 */
@Service
public class ContentEngine {

    private static final Logger log = LoggerFactory.getLogger(ContentEngine.class);

    private final LlmProviderFactory factory;

    public ContentEngine(LlmProviderFactory factory) {
        this.factory = factory;
    }

    /**
     * 生成内容大纲 JSON。
     * @param intent 用户原始意图
     * @param constraints 阶段A约束（JSON 片段，可空）
     */
    public JsonNode generate(String intent, String constraints) {
        LlmProvider provider = factory.provider();
        String system = Prompts.contentSystem();
        if (constraints != null && !constraints.trim().isEmpty()) {
            // 带约束走 stageA system
            system = Prompts.stageASystem() + "\n用户约束集: " + constraints;
        }
        ChatRequest req = new ChatRequest();
        req.setSystemPrompt(system);
        req.setUserContent(intent);

        // 尝试 2 次（初次 + 重试 1 次）
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String raw = provider.chat(req);
                JsonNode parsed = JsonUtil.extractJsonFromText(raw);
                parsed = parsed != null ? parsed : JsonUtil.parse(raw);
                if (validate(parsed)) {
                    return parsed;
                }
                log.warn("内容 JSON 校验失败(第{}次): {}", attempt + 1, raw);
            } catch (Exception e) {
                log.warn("内容通道 LLM 调用异常(第{}次): {}", attempt + 1, e.getMessage());
            }
        }
        // 降级：纯文本分段兜底
        return fallbackOutline(intent);
    }

    /** 校验内容 JSON 基本结构。 */
    public boolean validate(JsonNode node) {
        if (node == null || !node.isObject()) return false;
        if (!node.has("slides") || !node.get("slides").isArray()) return false;
        if (node.get("slides").size() == 0) return false;
        return true;
    }

    /** 纯文本分段降级：至少保证有一个可用的 slides 结构。 */
    public JsonNode fallbackOutline(String raw) {
        ObjectNode root = JsonUtil.parseObject("{\"meta\":{},\"slides\":[]}");
        ObjectNode meta = (ObjectNode) root.get("meta");
        meta.put("title", "AI 生成大纲");
        meta.put("theme", "corporate");
        meta.put("aspectRatio", "16:9");
        JsonNode children = (JsonNode) root.get("slides");
        java.util.List<String> parts = splitByParagraph(raw);
        int idx = 0;
        for (String p : parts) {
            ObjectNode s = ((ObjectNode) root).putArray("slides").addObject();
            s.put("layout", idx == 0 ? "title" : "bullet");
            s.put("title", idx == 0 ? titleOf(raw) : "要点 " + (idx));
            s.put("notes", "");
            s.set("bullets", JsonUtil.MAPPER.createArrayNode().add(p.length() > 60 ? p.substring(0, 60) : p));
            idx++;
            if (idx >= 12) break;
        }
        return root;
    }

    private java.util.List<String> splitByParagraph(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            out.add("（无输入内容）");
            return out;
        }
        String[] lines = text.split("\\n+|。|；");
        for (String l : lines) {
            String t = l.trim();
            if (!t.isEmpty()) out.add(t);
        }
        if (out.isEmpty()) out.add(text.trim());
        return out;
    }

    private String titleOf(String raw) {
        if (raw == null) return "汇报主题";
        String t = raw.trim();
        return t.length() > 20 ? t.substring(0, 20) : (t.isEmpty() ? "汇报主题" : t);
    }
}
