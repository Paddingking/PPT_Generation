package com.deckforge.service;

import com.deckforge.llm.LlmProviderFactory;
import com.deckforge.model.Dtos;
import com.deckforge.repository.ConfigRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 配置服务：保存/激活/删除配置 + 测试连接（P1-2）。
 */
@Service
public class ConfigService {

    private static final Logger log = LoggerFactory.getLogger(ConfigService.class);

    private final ConfigRepository configRepo;
    private final LlmProviderFactory factory;

    public ConfigService(ConfigRepository configRepo, LlmProviderFactory factory) {
        this.configRepo = configRepo;
        this.factory = factory;
    }

    public List<ConfigRepository.LlmConfig> list() {
        return configRepo.all();
    }

    /** 当前激活配置（脱敏后用于前端展示，不含完整 apiKey）。 */
    public Map<String, Object> active() {
        ConfigRepository.LlmConfig cfg = configRepo.activeConfig();
        if (cfg == null) {
            Map<String, Object> m0 = new HashMap<>();
            m0.put("present", false);
            m0.put("protocol", "MOCK");
            return m0;
        }
        Map<String, Object> m = new HashMap<>();
        m.put("present", true);
        m.put("id", cfg.id);
        m.put("name", cfg.name);
        m.put("protocol", cfg.protocol);
        m.put("baseUrl", cfg.baseUrl);
        m.put("model", cfg.model);
        m.put("apiKeyMasked", mask(cfg.apiKey));
        return m;
    }

    private String mask(String key) {
        if (key == null || key.isEmpty()) return "";
        if (key.length() <= 8) return "****";
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }

    public Map<String, Object> save(Dtos.LlmConfigRequest req) {
        ConfigRepository.LlmConfig cfg = new ConfigRepository.LlmConfig();
        cfg.id = req.getId();
        cfg.name = req.getName() != null ? req.getName() : "LLM 配置";
        cfg.protocol = req.getProtocol() != null ? req.getProtocol() : "OPENAI_COMPAT";
        cfg.baseUrl = req.getBaseUrl() != null ? req.getBaseUrl().trim() : "";
        cfg.apiKey = req.getApiKey() != null ? req.getApiKey().trim() : "";
        cfg.model = req.getModel() != null ? req.getModel() : "";
        cfg.active = Boolean.TRUE.equals(req.getActive());

        long id = configRepo.save(cfg);
        if (cfg.active) configRepo.setActive(id);
        Map<String, Object> out = new HashMap<>();
        out.put("id", id);
        out.put("message", "配置已保存（" + cfg.protocol + " @ " + cfg.baseUrl + "）");
        return out;
    }

    /** 测试连接：返回错误字符串（null=成功）。 */
    public String testConnection(Long configId) {
        ConfigRepository.LlmConfig cfg;
        if (configId != null) {
            cfg = configRepo.byId(configId);
        } else {
            cfg = configRepo.activeConfig();
        }
        if (cfg == null) {
            return "未找到配置";
        }
        return factory.providerFor(cfg).testConnection();
    }

    public void delete(Long id) {
        configRepo.delete(id);
    }

    public void setActive(Long id) {
        configRepo.setActive(id);
    }
}
