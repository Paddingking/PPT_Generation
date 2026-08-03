package com.deckforge.controller;

import com.deckforge.model.Dtos;
import com.deckforge.service.ConversationService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话式多轮优化接口：
 * - 阶段A：生成前约束（约束随首版生成一起走 ProjectService.generate）
 * - 阶段B：预览微调（增量 diff）+ 一致性开关 + 快照撤销
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final ConversationService conversationService;

    public ChatController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }

    /** 阶段A：记录一条约束回复到会话历史（可扩展）。这里返回助手确认。 */
    @PostMapping("/constrain")
    public Dtos.ApiResponse constrain(@RequestBody Dtos.StageARequest req) {
        Map<String, Object> c = new HashMap<>();
        c.put("style", req.getStyle() == null ? "corporate" : req.getStyle());
        c.put("pageCount", req.getPageCount() == null ? 10 : req.getPageCount());
        c.put("density", req.getDensity() == null ? "平衡" : req.getDensity());
        Map<String, Object> m = new HashMap<>();
        m.put("stage", "CONSTRAIN");
        m.put("constraints", c);
        m.put("message", "约束集已就绪，可继续生成双稿或补充要求。");
        return Dtos.ApiResponse.ok("约束已记录", m);
    }

    /** 阶段B：预览微调。 */
    @PostMapping("/polish")
    public Dtos.ApiResponse polish(@RequestBody Dtos.PolishRequest req) {
        try {
            java.util.List<Object> result = conversationService.polish(req);
            Map<String, Object> m = new HashMap<>();
            m.put("content", result.get(0));
            m.put("style", result.get(1));
            m.put("applied", result.get(2));
            m.put("rejected", result.get(3));
            m.put("askUser", result.get(4));
            m.put("intent", result.get(5));
            return Dtos.ApiResponse.ok("已按指令做最小 diff 合并", m);
        } catch (Exception e) {
            return Dtos.ApiResponse.err("微调失败: " + e.getMessage());
        }
    }

    /** 一致性开关。 */
    @PostMapping("/sync-style")
    public Dtos.ApiResponse setSyncStyle(@RequestParam Long projectId, @RequestParam boolean on) {
        conversationService.setSyncLocalStyle(projectId, on);
        return Dtos.ApiResponse.ok("一致性开关: " + (on ? "开（局部改动同步全局）" : "关（仅改所指页）"));
    }

    /** 撤销：回指定快照或初稿。 */
    @PostMapping("/undo")
    public Dtos.ApiResponse undo(@RequestBody Dtos.UndoRequest req) {
        try {
            java.util.List<Object> result = conversationService.undo(req);
            Boolean ok = (Boolean) result.get(0);
            if (Boolean.TRUE.equals(ok)) {
                Map<String, Object> data = new HashMap<>();
                data.put("content", result.size() > 1 ? result.get(1) : null);
                data.put("style", result.size() > 2 ? result.get(2) : null);
                return Dtos.ApiResponse.ok("已回退", data);
            }
            return Dtos.ApiResponse.err("回退失败: " + (result.size() > 1 ? result.get(1) : "未知"));
        } catch (Exception e) {
            return Dtos.ApiResponse.err("撤销异常: " + e.getMessage());
        }
    }

    /** 会话历史。 */
    @GetMapping("/history/{projectId}")
    public Dtos.ApiResponse history(@PathVariable Long projectId) {
        return Dtos.ApiResponse.ok("会话历史", conversationService.history(projectId));
    }
}
