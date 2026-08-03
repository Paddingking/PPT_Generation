package com.deckforge.llm;

/**
 * LLM 调用统一异常。
 */
public class LlmException extends RuntimeException {
    public LlmException(String message) { super(message); }
    public LlmException(String message, Throwable cause) { super(message, cause); }
}
