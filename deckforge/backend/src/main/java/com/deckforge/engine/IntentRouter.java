package com.deckforge.engine;

import com.deckforge.llm.ChatRequest;
import com.deckforge.llm.LlmProvider;
import com.deckforge.llm.LlmProviderFactory;
import org.springframework.stereotype.Component;

/**
 * 意图路由（PRD 5.9.3 / 技术说明 §7.1）。
 * 关键词命中优先，命中不了交 LLM 兜底。
 */
@Component
public class IntentRouter {

    public enum Intent { CONTENT, STYLE, GLOBAL_STYLE, OTHER }

    private static final String[] STYLE_KW = {"颜色","配色","字体","字号","布局","对齐","间距","主题","加深","更浅","色调"};
    private static final String[] GLOBAL_KW = {"全文","整体","全部","所有页","统一"};
    private static final String[] CONTENT_KW = {"标题","要点","内容","备注","措辞","文字","删","加一","改标题"};

    private final LlmProviderFactory factory;

    public IntentRouter(LlmProviderFactory factory) {
        this.factory = factory;
    }

    public Intent classify(String text) {
        if (text == null || text.trim().isEmpty()) return Intent.OTHER;
        String t = text.trim();

        if (containsAny(t, STYLE_KW)) {
            return containsAny(t, GLOBAL_KW) ? Intent.GLOBAL_STYLE : Intent.STYLE;
        }
        if (containsAny(t, CONTENT_KW)) {
            return Intent.CONTENT;
        }
        // LLM 兜底
        try {
            LlmProvider p = factory.provider();
            ChatRequest req = new ChatRequest();
            req.setSystemPrompt("判断用户指令的意图：只返回一个词(CONTENT/STYLE/GLOBAL_STYLE/OTHER)。\n标记：意图路由");
            req.setUserContent(t);
            String r = p.chat(req);
            if (r != null) {
                String up = r.trim().toUpperCase();
                for (Intent i : Intent.values()) {
                    if (up.contains(i.name())) return i;
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return Intent.CONTENT;
    }

    private static boolean containsAny(String s, String[] kws) {
        for (String k : kws) {
            if (s.contains(k)) return true;
        }
        return false;
    }
}
