package com.deckforge.llm;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OpenAI 兼容 Provider。POST {baseUrl}/chat/completions。
 * 例如 baseUrl 填 "https://api.deepseek.com/v1" 或 "https://api.openai.com/v1"。
 */
public class OpenAiProvider implements LlmProvider {

    private final String baseUrl;
    private final String apiKey;
    private final String model;
    private final LlmHttpClient http;

    public OpenAiProvider(String baseUrl, String apiKey, String model, LlmHttpClient http) {
        this.baseUrl = trimSlashes(baseUrl);
        this.apiKey = apiKey == null ? "" : apiKey;
        this.model = model;
        this.http = http;
    }

    @Override
    public String protocol() {
        return "OPENAI_COMPAT";
    }

    @Override
    public String chat(ChatRequest req) {
        try {
            com.fasterxml.jackson.databind.node.ObjectNode body = http.buildOpenAiBody(req, true);
            String url = baseUrl + "/chat/completions";
            Map<String, String> headers = Collections.singletonMap("Authorization", "Bearer " + apiKey);
            String resp = http.postJson(url, headers, body);
            return http.extractOpenAiContent(resp);
        } catch (Exception e) {
            throw new LlmException("OpenAI 兼容调用失败: " + e.getMessage(), e);
        }
    }

    @Override
    public String testConnection() {
        if (apiKey.isEmpty()) return "未配置 API Key";
        OkHttpClient probe = new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build();
        try {
            Request req = new Request.Builder()
                    .url(baseUrl + "/models")
                    .header("Authorization", "Bearer " + apiKey)
                    .get()
                    .build();
            try (Response resp = probe.newCall(req).execute()) {
                if (resp.isSuccessful()) return null; // null = 成功
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
