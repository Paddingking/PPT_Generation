package com.deckforge.llm;

import java.util.List;

/**
 * LLM 调用请求载体（协议无关）。
 */
public class ChatRequest {
    private String model;
    private String systemPrompt;
    private String userContent;
    private Double temperature;
    private Integer maxTokens;
    /** 可选：多轮历史（precede the user content）。role=user/assistant。 */
    private List<HistoryMsg> history;

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public String getUserContent() { return userContent; }
    public void setUserContent(String userContent) { this.userContent = userContent; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
    public List<HistoryMsg> getHistory() { return history; }
    public void setHistory(List<HistoryMsg> history) { this.history = history; }

    public static class HistoryMsg {
        private String role;   // user | assistant
        private String content;
        public HistoryMsg() {}
        public HistoryMsg(String role, String content) { this.role = role; this.content = content; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
