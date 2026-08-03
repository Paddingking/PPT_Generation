package com.deckforge.engine;

import com.deckforge.llm.ChatRequest;
import com.deckforge.llm.LlmProvider;
import com.deckforge.llm.LlmProviderFactory;
import com.deckforge.model.TemplateSkeleton;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 格式/样式通道引擎（PRD §6.2）：LLM 分析模板骨架 + 内容，产出"设计决策 JSON"。
 * 合法取值域严格校验，非法值回退骨架默认（不抛异常，PRD 5.5 约束）。
 */
@Service
public class StyleEngine {

    private static final Logger log = LoggerFactory.getLogger(StyleEngine.class);

    private final LlmProviderFactory factory;

    public StyleEngine(LlmProviderFactory factory) {
        this.factory = factory;
    }

    /**
     * 生成设计决策 JSON。
     * @param skeleton 模板骨架（决定回退默认）
     * @param contentJson 内容 JSON（用于自适应）
     */
    public JsonNode generate(TemplateSkeleton skeleton, JsonNode contentJson, String styleHint) {
        LlmProvider provider = factory.provider();
        ChatRequest req = new ChatRequest();
        req.setSystemPrompt(Prompts.styleSystem());
        String user = "模板骨架:\n" + toCompactString(skeleton)
                + "\n\n风格偏好: " + (styleHint == null || styleHint.isEmpty() ? "按骨架默认" : styleHint)
                + "\n\n请产出设计决策 JSON。\n标记：style-json";
        req.setUserContent(user);

        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                String raw = provider.chat(req);
                JsonNode parsed = JsonUtil.extractJsonFromText(raw);
                parsed = parsed != null ? parsed : JsonUtil.parse(raw);
                // 非法值回退 + 缺省补齐
                JsonNode cleaned = sanitize(parsed, skeleton);
                if (cleaned != null) return cleaned;
                log.warn("样式 JSON 校验失败(第{}次)", attempt + 1);
            } catch (Exception e) {
                log.warn("样式通道调用异常(第{}次): {}", attempt + 1, e.getMessage());
            }
        }
        // 回退：用骨架默认生成一份合法样式
        return fallbackFromSkeleton(skeleton, styleHint);
    }

    /** 非法取值回退骨架默认，缺省字段补齐。返回清理后的 JSON；完全非法返回 null。 */
    private JsonNode sanitize(JsonNode node, TemplateSkeleton skeleton) {
        if (node == null || !node.isObject()) return null;
        ObjectNode out = (ObjectNode) node.deepCopy();
        if (!out.has("meta")) out.set("meta", JsonUtil.MAPPER.createObjectNode());
        if (!out.has("palette")) out.set("palette", paletteFromSkeleton(skeleton));
        if (!out.has("typography")) out.set("typography", typoFromSkeleton(skeleton));
        if (!out.has("layoutRules")) out.set("layoutRules", defaultRules());

        // 颜色合法性：必须是 #RRGGBB
        ObjectNode palette = (ObjectNode) out.get("palette");
        String[] colorKeys = {"background","primary","secondary","accent","titleText","bodyText"};
        for (String k : colorKeys) {
            if (!palette.has(k) || !isHexColor(palette.get(k).asText())) {
                applyDefault(k, palette, skeleton.getPalette());
            }
        }
        // 字号合法性：12~40
        ObjectNode typo = (ObjectNode) out.get("typography");
        if (!isInRange(typo, "titleSizePt", 12, 72)) typo.put("titleSizePt", skelInt(skeleton, 30));
        if (!isInRange(typo, "bulletSizePt", 12, 40)) typo.put("bulletSizePt", skelInt(skeleton, 18));
        if (!isInRange(typo, "noteSizePt", 8, 24)) typo.put("noteSizePt", 12);
        if (!typo.has("titleFont")) typo.put("titleFont", "微软雅黑");
        if (!typo.has("bodyFont")) typo.put("bodyFont", "微软雅黑");
        // 坐标合法性 0~1
        ObjectNode rules = (ObjectNode) out.get("layoutRules");
        sanitizeRegion(rules, "titlePosition");
        sanitizeRegion(rules, "bodyPosition");
        if (!rules.has("bulletLineSpacing")) rules.put("bulletLineSpacing", 1.5);
        if (!rules.has("align")) rules.put("align", "left");
        return out;
    }

    private void sanitizeRegion(ObjectNode rules, String field) {
        if (rules.has(field) && rules.get(field).isObject()) {
            ObjectNode region = (ObjectNode) rules.get(field);
            double x = in01(region, "x", 0.06);
            double y = in01(region, "y", 0.06);
            double w = in01(region, "w", 0.88);
            double h = in01(region, "h", 0.7);
            region.put("x", x); region.put("y", y); region.put("w", w); region.put("h", h);
        } else {
            ObjectNode region = JsonUtil.MAPPER.createObjectNode();
            region.put("x", "titlePosition".equals(field) ? 0.06 : 0.08);
            region.put("y", "titlePosition".equals(field) ? 0.06 : 0.24);
            region.put("w", "titlePosition".equals(field) ? 0.88 : 0.84);
            region.put("h", "titlePosition".equals(field) ? 0.14 : 0.70);
            rules.set(field, region);
        }
    }

    private double in01(ObjectNode region, String key, double def) {
        if (region.has(key) && region.get(key).isNumber()) {
            double v = region.get(key).asDouble();
            if (v >= 0 && v <= 1) return v;
        }
        return def;
    }

    private boolean isHexColor(String s) {
        if (s == null || s.isEmpty()) return false;
        String t = s.startsWith("#") ? s.substring(1) : s;
        return t.length() == 6 && t.matches("[0-9a-fA-F]{6}");
    }

    private boolean isInRange(ObjectNode obj, String key, int min, int max) {
        if (!obj.has(key) || !obj.get(key).isNumber()) return false;
        int v = obj.get(key).asInt();
        return v >= min && v <= max;
    }

    private void applyDefault(String key, ObjectNode palette, TemplateSkeleton.Palette skel) {
        String def = keyColor(skel, key);
        if (def != null) palette.put(key, def);
    }

    private String keyColor(TemplateSkeleton.Palette p, String key) {
        if (p == null) return null;
        switch (key) {
            case "background": return p.getBackground() != null ? p.getBackground() : "#FFFFFF";
            case "primary": return p.getPrimary() != null ? p.getPrimary() : "#185FA5";
            case "secondary": return p.getSecondary() != null ? p.getSecondary() : "#378ADD";
            case "accent": return p.getAccent() != null ? p.getAccent() : "#E6F1FB";
            case "titleText": return p.getTitleText() != null ? p.getTitleText() : "#0C2B4D";
            case "bodyText": return p.getBodyText() != null ? p.getBodyText() : "#20344D";
            default: return null;
        }
    }

    private int skelInt(TemplateSkeleton sk, int def) {
        TemplateSkeleton.Typography t = sk.getTypography();
        if (t == null) return def;
        if (t.getTitleSizePt() > 0) return t.getTitleSizePt();
        return def;
    }

    private ObjectNode paletteFromSkeleton(TemplateSkeleton sk) {
        ObjectNode p = JsonUtil.MAPPER.createObjectNode();
        TemplateSkeleton.Palette skel = sk.getPalette();
        p.put("background", keyColor(skel, "background"));
        p.put("primary", keyColor(skel, "primary"));
        p.put("secondary", keyColor(skel, "secondary"));
        p.put("accent", keyColor(skel, "accent"));
        p.put("titleText", keyColor(skel, "titleText"));
        p.put("bodyText", keyColor(skel, "bodyText"));
        return p;
    }

    private ObjectNode typoFromSkeleton(TemplateSkeleton sk) {
        ObjectNode t = JsonUtil.MAPPER.createObjectNode();
        t.put("titleFont", "微软雅黑");
        t.put("bodyFont", "微软雅黑");
        t.put("titleSizePt", 30);
        t.put("bulletSizePt", 18);
        t.put("noteSizePt", 12);
        if (sk.getTypography() != null) {
            if (sk.getTypography().getTitleFont() != null) t.put("titleFont", sk.getTypography().getTitleFont());
            if (sk.getTypography().getBodyFont() != null) t.put("bodyFont", sk.getTypography().getBodyFont());
            if (sk.getTypography().getTitleSizePt() > 0) t.put("titleSizePt", sk.getTypography().getTitleSizePt());
            if (sk.getTypography().getBulletSizePt() > 0) t.put("bulletSizePt", sk.getTypography().getBulletSizePt());
        }
        return t;
    }

    private ObjectNode defaultRules() {
        ObjectNode rules = JsonUtil.MAPPER.createObjectNode();
        ObjectNode tp = JsonUtil.MAPPER.createObjectNode();
        tp.put("x", 0.06); tp.put("y", 0.06); tp.put("w", 0.88); tp.put("h", 0.14);
        ObjectNode bp = JsonUtil.MAPPER.createObjectNode();
        bp.put("x", 0.08); bp.put("y", 0.24); bp.put("w", 0.84); bp.put("h", 0.70);
        rules.set("titlePosition", tp);
        rules.set("bodyPosition", bp);
        rules.put("bulletLineSpacing", 1.5);
        rules.put("align", "left");
        return rules;
    }

    private JsonNode fallbackFromSkeleton(TemplateSkeleton sk, String styleHint) {
        ObjectNode out = JsonUtil.MAPPER.createObjectNode();
        out.set("meta", JsonUtil.MAPPER.createObjectNode()
                .put("aspectRatio", sk.getAspectRatio())
                .put("masterWidth", sk.getMasterWidth())
                .put("masterHeight", sk.getMasterHeight()));
        out.set("palette", paletteFromSkeleton(sk));
        out.set("typography", typoFromSkeleton(sk));
        out.set("layoutRules", defaultRules());
        return out;
    }

    private String toCompactString(TemplateSkeleton sk) {
        try {
            return JsonUtil.MAPPER.writeValueAsString(sk);
        } catch (Exception e) {
            return "{}";
        }
    }
}
