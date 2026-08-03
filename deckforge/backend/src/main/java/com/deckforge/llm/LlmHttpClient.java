package com.deckforge.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import okhttp3.*;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * LLM HTTP 调用底层封装（OkHttp，兼容 jdk8）。
 * 通过统一工具类降低双协议 Provider 的重复代码。
 */
public class LlmHttpClient {
    private final OkHttpClient client;
    private final ObjectMapper mapper;

    public LlmHttpClient(int connectTimeoutMs, int readTimeoutMs) {
        this.mapper = new ObjectMapper();
        this.client = new OkHttpClient.Builder()
                .connectTimeout(connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(readTimeoutMs, TimeUnit.MILLISECONDS)
                .build();
    }

    /** 通用 POST JSON，返回原始字符串；非 2xx 抛 IllegalStateException。 */
    public String postJson(String url, java.util.Map<String, String> headers, ObjectNode body) throws Exception {
        Request.Builder rb = new Request.Builder().url(url);
        rb.header("Content-Type", "application/json");
        if (headers != null) {
            for (java.util.Map.Entry<String, String> e : headers.entrySet()) {
                rb.header(e.getKey(), e.getValue());
            }
        }
        String payload = mapper.writeValueAsString(body);
        rb.post(RequestBody.create(MediaType.parse("application/json; charset=utf-8"), payload));

        try (Response resp = client.newCall(rb.build()).execute()) {
            String text = resp.body() != null ? resp.body().string() : "";
            if (!resp.isSuccessful()) {
                throw new IllegalStateException("LLM HTTP " + resp.code() + ": " + truncate(text, 500));
            }
            return text;
        }
    }

    /** 构建 OpenAI 兼容请求体（含多轮历史） */
    public ObjectNode buildOpenAiBody(ChatRequest req, boolean jsonMode) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", req.getModel());
        root.put("temperature", req.getTemperature() != null ? req.getTemperature() : 0.7);
        if (req.getMaxTokens() != null) root.put("max_tokens", req.getMaxTokens());
        ArrayNode messages = mapper.createArrayNode();
        messages.add(msg("system", req.getSystemPrompt()));
        if (req.getHistory() != null) {
            for (ChatRequest.HistoryMsg h : req.getHistory()) {
                messages.add(msg(h.getRole(), h.getContent()));
            }
        }
        messages.add(msg("user", req.getUserContent()));
        root.set("messages", messages);
        if (jsonMode) {
            ObjectNode rf = mapper.createObjectNode();
            rf.put("type", "json_object");
            root.set("response_format", rf);
        }
        return root;
    }

    /** 构建 Anthropic 兼容请求体 */
    public ObjectNode buildAnthropicBody(ChatRequest req) {
        ObjectNode root = mapper.createObjectNode();
        root.put("model", req.getModel());
        root.put("max_tokens", req.getMaxTokens() != null ? req.getMaxTokens() : 4096);
        root.put("system", req.getSystemPrompt());
        root.put("temperature", req.getTemperature() != null ? req.getTemperature() : 0.7);
        ArrayNode messages = mapper.createArrayNode();
        // Anthropic 要求首条通常是 user
        List<ChatRequest.HistoryMsg> history = req.getHistory();
        if (history != null && !history.isEmpty()) {
            for (ChatRequest.HistoryMsg h : history) {
                messages.add(anthropicMsg(h.getRole(), h.getContent()));
            }
        } else {
            messages.add(anthropicMsg("user", req.getUserContent()));
        }
        // 若 history 末条不是 user，补一条当前 user 指令
        boolean lastIsUser = messages.size() > 0 && messages.get(messages.size() - 1).has("role")
                && "user".equals(messages.get(messages.size() - 1).get("role").asText());
        if (!lastIsUser || history == null) {
            // 若只有一条我们已 push user，避免重复
            if (!(history == null && messages.size() == 1)) {
                messages.add(anthropicMsg("user", req.getUserContent()));
            }
        }
        root.set("messages", messages);
        return root;
    }

    private ObjectNode msg(String role, String content) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", role);
        m.put("content", content == null ? "" : content);
        return m;
    }

    private ObjectNode anthropicMsg(String role, String content) {
        ObjectNode m = mapper.createObjectNode();
        m.put("role", "user".equals(role) ? "user" : "assistant");
        ArrayNode arr = mapper.createArrayNode();
        ObjectNode inner = mapper.createObjectNode();
        inner.put("type", "text");
        inner.put("text", content == null ? "" : content);
        arr.add(inner);
        m.set("content", arr);
        return m;
    }

    /** 从 OpenAI 响应提取 content 文本 */
    public String extractOpenAiContent(String respText) throws Exception {
        JsonNode node = mapper.readTree(respText);
        JsonNode choices = node.path("choices");
        if (choices.isArray() && choices.size() > 0) {
            return choices.get(0).path("message").path("content").asText();
        }
        JsonNode error = node.path("error");
        if (!error.isMissingNode()) {
            throw new IllegalStateException("LLM 错误: " + error.path("message").asText());
        }
        throw new IllegalStateException("无法从 OpenAI 兼容响应解析内容: " + truncate(respText, 300));
    }

    /** 从 Anthropic 响应提取 content 文本 */
    public String extractAnthropicContent(String respText) throws Exception {
        JsonNode node = mapper.readTree(respText);
        JsonNode content = node.path("content");
        if (content.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode c : content) {
                if ("text".equals(c.path("type").asText())) {
                    sb.append(c.path("text").asText());
                }
            }
            if (sb.length() > 0) return sb.toString();
        }
        JsonNode error = node.path("error");
        if (!error.isMissingNode()) {
            throw new IllegalStateException("LLM 错误: " + error.path("message").asText());
        }
        throw new IllegalStateException("无法从 Anthropic 兼容响应解析内容: " + truncate(respText, 300));
    }

    public ObjectMapper mapper() { return mapper; }

    private static String truncate(String s, int n) {
        if (s == null) return "";
        return s.length() <= n ? s : s.substring(0, n) + "...";
    }
}
