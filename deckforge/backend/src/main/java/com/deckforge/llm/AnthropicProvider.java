package com.deckforge.llm;

import java.util.Collections;
import java.util.Map;

/**
 * Anthropic 兼容 Provider。POST {baseUrl}/v1/messages。
 * 例如 baseUrl 填 "https://api.anthropic.com" 或代理地址。
 */
public class AnthropicProvider implements LlmProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final LlmHttpClient http;

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    public AnthropicProvider(String baseUrl, String apiKey, String model, LlmHttpClient http) {
        this.baseUrl = trimSlashes(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.http = http;
    }

    @Override
    public String protocol() {
        return "ANTHROPIC_COMPAT";
    }

    @Override
    public String chat(ChatRequest req) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode body = http.buildAnthropicBody(req);
            String url = baseUrl + "/v1/messages";
            Map<String, String> headers = new java.util.HashMap<>();
            headers.put("x-api-key", apiKey);
            headers.put("anthropic-version", ANTHROPIC_VERSION);
            String resp = http.postJson(url, headers, body);
            return http.extractAnthropicContent(resp);
        } catch (Exception e) {
            throw new LlmException("Anthropic 兼容调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String testConnection() {
        if (apiKey.isEmpty()) return "未配置 API Key";
        okhttp3.OkHttpClient probe = new okhttp3.OkHttpClient.Builder()
                .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
                .build();
        try {
            okhttp3.Request req = new okhttp3.Request.Builder()
                    .url(baseUrl + "/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", ANTHROPIC_VERSION)
                    .post(okhttp3.RequestBody.create(
                            okhttp3.MediaType.parse("application/json"),
                            "{\"model\":\"" + model + "\",\"max_tokens\":1,\"messages\":[{\"role\":\"user\",\"content\":[{\"type\":\"text\",\"text\":\"hi\"}]}]}"))
                    .build();
            try (okhttp3.Response resp = probe.newCall(req).execute()) {
                if (resp.isSuccessful()) return null;
                return "HTTP " + resp.code() + ": " + (resp.body() != null ? resp.body().string() : "");
            }
        } catch (Exception e) {
            return "连接失败: " + e.getMessage();
        }
    }

    private static String trimSlashes(String s) {
        if (s == null) return "";
        String t = s.trim();
        while (t.endsWith("/")) t = t.substring(0, t.length() - 1);
        return t;
    }
}
