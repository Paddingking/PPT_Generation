package com.deckforge.llm;

import com.deckforge.repository.ConfigRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;

/**
 * LLM Provider 工厂（PRD 5.7）：
 * 根据用户保存的 protocol + baseUrl 运行时创建实例，切换供应商零改码。
 * 未配置真实 Key 时默认用 MockProvider 打通主链路。
 */
@Component
public class LlmProviderFactory {

    private final ConfigRepository configRepo;
    private final int connectTimeoutMs;
    private final int readTimeoutMs;
    private final String defaultProvider;

    private volatile LlmHttpClient httpClient;

    public LlmProviderFactory(
            ConfigRepository configRepo,
            @Value("${deckforge.llm.default-provider:mock}") String defaultProvider,
            @Value("${deckforge.llm.connect-timeout-ms:15000}") int connectTimeoutMs,
            @Value("${deckforge.llm.read-timeout-ms:120000}") int readTimeoutMs) {
        this.configRepo = configRepo;
        this.defaultProvider = defaultProvider;
        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;
    }

    @PostConstruct
    public void init() {
        this.httpClient = new LlmHttpClient(connectTimeoutMs, readTimeoutMs);
    }

    /** 返回当前激活配置对应的 Provider；无激活配置则返回 Mock。 */
    public LlmProvider provider() {
        ConfigRepository.LlmConfig cfg = configRepo.activeConfig();
        return build(cfg);
    }

    /** 返回指定配置的 Provider（用于"测试连接"）。 */
    public LlmProvider providerFor(ConfigRepository.LlmConfig cfg) {
        return build(cfg);
    }

    private LlmProvider build(ConfigRepository.LlmConfig cfg) {
        if (cfg == null) {
            return new MockProvider();
        }
        String protocol = cfg.protocol == null ? "OPENAI_COMPAT" : cfg.protocol.trim().toUpperCase();
        String baseUrl = cfg.baseUrl == null ? "" : cfg.baseUrl;
        String apiKey = cfg.apiKey == null ? "" : cfg.apiKey;
        String model = (cfg.model == null || cfg.model.isEmpty()) ? defaultModelFor(protocol) : cfg.model;

        if (baseUrl.isEmpty() || apiKey.isEmpty()) {
            return new MockProvider();
        }
        switch (protocol) {
            case "ANTHROPIC_COMPAT":
                return new AnthropicProvider(baseUrl, apiKey, model, httpClient);
            case "OPENAI_COMPAT":
            default:
                return new OpenAiProvider(baseUrl, apiKey, model, httpClient);
        }
    }

    private String defaultModelFor(String protocol) {
        return "OPENAI_COMPAT".equals(protocol) ? "gpt-4o-mini" : "claude-3-5-sonnet";
    }
}
