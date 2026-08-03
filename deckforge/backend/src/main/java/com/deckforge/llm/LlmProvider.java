package com.deckforge.llm;

/**
 * LLM Provider 抽象（PRD 5.7）。
 * 上层只收统一的 String，不关心协议差异。
 * OpenAI 兼容 + Anthropic 兼容双协议，baseUrl 可自由配置。
 */
public interface LlmProvider {

    /** 协议类型 */
    String protocol();

    /** 以给定系统提示 + 用户内容(可含多轮历史) 返回 LLM 原始文本 */
    String chat(ChatRequest req);

    /** 校验配置连通性（设置页"测试连接"用），返回错误信息；null 表示成功 */
    String testConnection();
}
