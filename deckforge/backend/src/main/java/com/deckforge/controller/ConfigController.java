package com.deckforge.controller;

import com.deckforge.model.Dtos;
import com.deckforge.service.ConfigService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 配置接口（P1-2）：多套配置 / 激活 / 删除 / 测试连接。
 */
@RestController
@RequestMapping("/api/config")
@CrossOrigin(origins = "*")
public class ConfigController {

    private final ConfigService configService;

    public ConfigController(ConfigService configService) {
        this.configService = configService;
    }

    @GetMapping("/llm")
    public Dtos.ApiResponse list() {
        Map<String, Object> m = new HashMap<>();
        m.put("configs", configService.list());
        m.put("active", configService.active());
        // 不把 apiKey 明文返回
        return Dtos.ApiResponse.ok("LLM 配置列表", m);
    }

    @PostMapping("/llm")
    public Dtos.ApiResponse save(@RequestBody Dtos.LlmConfigRequest req) {
        try {
            return Dtos.ApiResponse.ok("配置已保存", configService.save(req));
        } catch (Exception e) {
            return Dtos.ApiResponse.err("保存失败: " + e.getMessage());
        }
    }

    @PostMapping("/llm/test")
    public Dtos.ApiResponse test(@RequestBody(required = false) Map<String, Object> body) {
        Long id = null;
        if (body != null && body.get("id") != null) {
            id = Long.valueOf(body.get("id").toString());
        }
        String err = configService.testConnection(id);
        if (err == null) {
            return Dtos.ApiResponse.ok("连接成功 ✓");
        }
        return Dtos.ApiResponse.err(err);
    }

    @PostMapping("/llm/activate")
    public Dtos.ApiResponse activate(@RequestParam Long id) {
        configService.setActive(id);
        return Dtos.ApiResponse.ok("已设为激活配置");
    }

    @DeleteMapping("/llm/{id}")
    public Dtos.ApiResponse delete(@PathVariable Long id) {
        configService.delete(id);
        return Dtos.ApiResponse.ok("已删除");
    }

    @GetMapping("/llm/active")
    public Dtos.ApiResponse active() {
        Map<String, Object> m = new HashMap<>();
        m.put("active", configService.active());
        return Dtos.ApiResponse.ok("当前激活配置", m);
    }
}
