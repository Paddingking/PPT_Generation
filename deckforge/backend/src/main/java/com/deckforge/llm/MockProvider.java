package com.deckforge.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Mock Provider —— 关键联调组件。
 *
 * PRD 强调"先 mock 后真人"：LLM 依赖是最大不稳定源，先用 mock 打通
 * "输入→内容JSON→样式JSON→预览→diff→导出"主链路，再接真实 LLM 联调。
 *
 * 本 Mock 依据 prompt 中的关键标记（engine=..., stage=...）返回合理结果，
 * 让前端在无任何 API Key 的情况下走通 P0-1~P0-14 全部交互。
 */
public class MockProvider implements LlmProvider {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public String protocol() {
        return "MOCK";
    }

    @Override
    public String chat(ChatRequest req) {
        String system = req.getSystemPrompt() == null ? "" : req.getSystemPrompt();
        String user = req.getUserContent() == null ? "" : req.getUserContent();

        // 意图路由：根据 system 关键词判断该返回什么
        if (system.contains("内容规则") || system.contains("content-json")) return mockContentJson(user, system);
        if (system.contains("样式规则") || system.contains("style-json")) return mockStyleJson(user);
        if (system.contains("阶段B") || system.contains("polish-diff") || system.contains("增量")) return mockPolishDiff(user);
        if (system.contains("约束") && system.contains("架构")) return mockConstrainedContent(user, system);
        if (system.contains("意图路由")) return mockIntent(user);
        return mockContentJson(user, system);
    }

    @Override
    public String testConnection() {
        return null; // mock 永远连得上
    }

    /** 根据用户意图生成一个合理内容大纲 JSON */
    private String mockContentJson(String user, String system) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode meta = root.putObject("meta");
        meta.put("title", guessTitle(user));
        meta.put("theme", "corporate");
        meta.put("aspectRatio", "16:9");
        meta.put("author", "DeckForge");
        ArrayNode slides = root.putArray("slides");

        String[] topics = {"现状与挑战", "目标与愿景", "核心方法论", "落地路线", "预期收益", "风险与应对", "行动计划", "总结展望"};
        String title = guessTitle(user);
        // 封面
        ObjectNode cover = slides.addObject();
        cover.put("layout", "title");
        cover.put("title", title);
        cover.put("subtitle", "DeckForge 演示 · 内容 + 版式双通道生成");
        cover.put("notes", "开场强调主题与定位");
        // 议程
        ObjectNode agenda = slides.addObject();
        agenda.put("layout", "agenda");
        agenda.put("title", "今日议程");
        ArrayNode agendaItems = agenda.putArray("bullets");
        for (String t : topics) agendaItems.add(t);
        agenda.put("notes", "");
        // 主体页
        for (int i = 0; i < 4; i++) {
            ObjectNode s = slides.addObject();
            s.put("layout", i % 2 == 0 ? "bullet" : "content");
            s.put("title", topics[i]);
            ArrayNode bs = s.putArray("bullets");
            bs.add("关键洞察点 " + (i + 1) + "：围绕 " + topics[i] + " 展开");
            bs.add("支撑数据与论据第 1 条");
            bs.add("支撑数据与论据第 2 条");
            s.put("notes", "讲演提示：强调" + topics[i]);
        }
        // 结尾
        ObjectNode closing = slides.addObject();
        closing.put("layout", "closing");
        closing.put("title", "感谢聆听");
        closing.put("subtitle", "期待你的反馈");
        closing.put("bullets", mapper.createArrayNode());
        closing.put("notes", "");
        return toJsonString(root);
    }

    /** 按约束生成（阶段A带约束） */
    private String mockConstrainedContent(String user, String system) {
        try {
            int pageCount = 10;
            if (system.contains("\"pageCount\":8") || system.contains("8 页")) pageCount = 8;
            if (system.contains("\"pageCount\":12") || system.contains("12 页")) pageCount = 12;
            ObjectNode root = mapper.createObjectNode();
            ObjectNode meta = root.putObject("meta");
            meta.put("title", guessTitle(user));
            meta.put("theme", system.contains("sunset") ? "sunset"
                    : system.contains("fresh") ? "fresh"
                    : system.contains("mono") ? "mono" : "corporate");
            meta.put("aspectRatio", "16:9");
            meta.put("author", "DeckForge");
            ArrayNode slides = root.putArray("slides");
            int body = pageCount - 2;
            String[] topics = {"现状盘点", "关键挑战", "战略目标", "方法论", "分步路线", "资源投入", "收益测算", "风险管理", "里程碑", "行动清单"};
            slides.add(coverSlide(guessTitle(user)));
            slides.add(agendaSlide(topics, body));
            for (int i = 0; i < body; i++) {
                ObjectNode s = slides.addObject();
                s.put("layout", i % 3 == 0 ? "content" : "bullet");
                s.put("title", topics[i]);
                ArrayNode bs = s.putArray("bullets");
                bs.add("要点一：关于" + topics[i] + "的核心判断");
                bs.add("要点二：关键行动与抓手");
                bs.add("要点三：预期成果 / 衡量指标");
                s.put("notes", "讲述" + topics[i] + "时的提词");
            }
            slides.add(closingSlide());
            return toJsonString(root);
        } catch (Exception e) {
            return mockContentJson(user, system);
        }
    }

    /** 生成样式决策 JSON */
    private String mockStyleJson(String user) {
        ObjectNode root = mapper.createObjectNode();
        ObjectNode meta = root.putObject("meta");
        meta.put("aspectRatio", "16:9");
        meta.put("masterWidth", 960);
        meta.put("masterHeight", 540);
        String theme = user.contains("sunset") ? "sunset" : user.contains("fresh") ? "fresh"
                : user.contains("mono") ? "mono" : "corporate";
        ObjectNode palette = root.putObject("palette");
        switch (theme) {
            case "sunset":
                palette.put("background", "#FFF7F0").put("primary", "#B8502E").put("secondary", "#FF9D66")
                        .put("accent", "#FFE4D1").put("titleText", "#7A2E14").put("bodyText", "#4A342A"); break;
            case "fresh":
                palette.put("background", "#FFFFFF").put("primary", "#2E8B57").put("secondary", "#6BBF8A")
                        .put("accent", "#E3F3E8").put("titleText", "#1C5340").put("bodyText", "#27402F"); break;
            case "mono":
                palette.put("background", "#FFFFFF").put("primary", "#1A1A1A").put("secondary", "#555555")
                        .put("accent", "#F2F2F2").put("titleText", "#000000").put("bodyText", "#333333"); break;
            default:
                palette.put("background", "#FFFFFF").put("primary", "#185FA5").put("secondary", "#378ADD")
                        .put("accent", "#E6F1FB").put("titleText", "#0C2B4D").put("bodyText", "#20344D");
        }
        ObjectNode typo = root.putObject("typography");
        typo.put("titleFont", "微软雅黑").put("bodyFont", "微软雅黑")
                .put("titleSizePt", 30).put("bulletSizePt", 18).put("noteSizePt", 12);
        ObjectNode rules = root.putObject("layoutRules");
        rules.putObject("titlePosition").put("x", 0.06).put("y", 0.06).put("w", 0.88).put("h", 0.14);
        rules.putObject("bodyPosition").put("x", 0.08).put("y", 0.24).put("w", 0.84).put("h", 0.7);
        rules.put("bulletLineSpacing", 1.5);
        rules.put("align", "left");
        return toJsonString(root);
    }

    /** 生成增量 diff（阶段B）—— 依据用户指令做启发式映射 */
    private String mockPolishDiff(String user) {
        // 真实指令在 <<<INSTRUCTION>>> 标记之间（ChatDiffEngine 注入），避免把 JSON 误当指令
        String u = extractInstruction(user);
        ObjectNode root = mapper.createObjectNode();
        ArrayNode patches = root.putArray("patches");

        // 全局主色加深
        if (u.contains("主色") || u.contains("颜色") || u.contains("加深") || u.contains("更深")) {
            ObjectNode p = patches.addObject();
            p.put("path", "palette.primary");
            p.put("op", "replace");
            p.put("value", "#0C3B66");
            p.put("targetPage", -1); // -1 表示全局
            root.put("intent", "GLOBAL_STYLE");
            return toJsonString(root);
        }
        // 全文统一
        if (u.contains("全文统一") || u.contains("全部统一") || u.contains("整体") || u.contains("统一")) {
            ObjectNode p = patches.addObject();
            p.put("path", "palette.primary");
            p.put("op", "replace");
            p.put("value", "#0C3B66");
            p.put("targetPage", -1);
            root.put("intent", "GLOBAL_STYLE");
            return toJsonString(root);
        }
        // 改标题措辞
        if (u.contains("标题") || u.contains("更有力") || u.contains("改标题")) {
            int page = extractPage(u);
            ObjectNode p = patches.addObject();
            p.put("path", "slides[" + page + "].title");
            p.put("op", "replace");
            p.put("value", mockNewTitle(u));
            p.put("targetPage", page);
            root.put("intent", "CONTENT");
            return toJsonString(root);
        }
        // 精炼某页要点
        if (u.contains("要点") || u.contains("精炼") || u.contains("精简")) {
            int page = extractPage(u);
            ObjectNode p = patches.addObject();
            p.put("path", "slides[" + page + "].bullets[0]");
            p.put("op", "replace");
            p.put("value", "核心结论：" + guessTitle(u) + " 精炼后的要点");
            p.put("targetPage", page);
            root.put("intent", "CONTENT");
            return toJsonString(root);
        }
        // 默认：内容微调
        ObjectNode d = patches.addObject();
        d.put("path", "meta.title");
        d.put("op", "replace");
        d.put("value", "（已按指令微调）" + guessTitle(u));
        d.put("targetPage", 0);
        root.put("intent", "CONTENT");
        return toJsonString(root);
    }

    private String extractInstruction(String user) {
        if (user == null) return "";
        int s = user.indexOf("<<<INSTRUCTION>>>");
        if (s >= 0) {
            int e = user.indexOf("<<<INSTRUCTION>>>", s + "<<<INSTRUCTION>>>".length());
            if (e > s) return user.substring(s + "<<<INSTRUCTION>>>".length(), e).trim();
        }
        return user.trim();
    }

    private String mockIntent(String user) {
        String u = user == null ? "" : user;
        if (u.contains("颜色") || u.contains("配色") || u.contains("字体") || u.contains("字号")
                || u.contains("布局") || u.contains("对齐") || u.contains("间距") || u.contains("主题")
                || u.contains("加深") || u.contains("风格")) {
            return (u.contains("全文") || u.contains("整体") || u.contains("统一")) ? "GLOBAL_STYLE" : "STYLE";
        }
        if (u.contains("标题") || u.contains("要点") || u.contains("内容") || u.contains("备注")
                || u.contains("措辞") || u.contains("删") || u.contains("加")) {
            return "CONTENT";
        }
        return "CONTENT";
    }

    // ---- helpers ----

    private int extractPage(String u) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(u);
        if (m.find()) {
            int p = Integer.parseInt(m.group(1));
            return Math.max(0, Math.min(20, p - 1));
        }
        return 0;
    }

    private String mockNewTitle(String u) {
        if (u.contains("更有力")) return "迈向 AI，从战略共识开始";
        if (u.contains("简短")) return "AI 转型决胜点";
        return "战略聚焦：" + guessTitle(u);
    }

    private String guessTitle(String user) {
        String t = user == null ? "" : user.trim();
        if (t.isEmpty()) return "AI 数字化战略汇报";
        // 去掉引号内前后的零碎
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("「(.+?)」|《(.+?)》|\"(.+?)\"").matcher(t);
        if (m.find()) {
            String g = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            if (g != null && g.length() > 2) return g.length() > 20 ? g.substring(0, 20) : g;
        }
        if (t.length() > 24) return t.substring(0, 24);
        return t.isEmpty() ? "AI 数字化战略汇报" : t;
    }

    private JsonNode coverSlide(String title) {
        ObjectNode c = mapper.createObjectNode();
        c.put("layout", "title");
        c.put("title", title);
        c.put("subtitle", "专题汇报 · 内容与版式双通道生成");
        c.put("notes", "开场点题");
        c.put("bullets", mapper.createArrayNode());
        return c;
    }

    private JsonNode agendaSlide(String[] topics, int body) {
        ObjectNode a = mapper.createObjectNode();
        a.put("layout", "agenda");
        a.put("title", "今日议程");
        ArrayNode arr = a.putArray("bullets");
        for (int i = 0; i < Math.min(body, topics.length); i++) arr.add(topics[i]);
        a.put("notes", "");
        return a;
    }

    private JsonNode closingSlide() {
        ObjectNode c = mapper.createObjectNode();
        c.put("layout", "closing");
        c.put("title", "感谢聆听");
        c.put("subtitle", "期待反馈与讨论");
        c.put("bullets", mapper.createArrayNode());
        c.put("notes", "");
        return c;
    }

    private String toJsonString(JsonNode node) {
        try {
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            return "{}";
        }
    }
}
