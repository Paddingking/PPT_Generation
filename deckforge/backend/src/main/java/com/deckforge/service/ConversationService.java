package com.deckforge.service;

import com.deckforge.engine.ChatDiffEngine;
import com.deckforge.engine.JsonUtil;
import com.deckforge.model.Dtos;
import com.deckforge.repository.ProjectRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 对话服务：阶段B 预览微调 + 一致性开关 + 快照撤销。
 */
@Service
public class ConversationService {

    private static final Logger log = LoggerFactory.getLogger(ConversationService.class);

    private final ChatDiffEngine diffEngine;
    private final ProjectRepository repo;

    public ConversationService(ChatDiffEngine diffEngine, ProjectRepository repo) {
        this.diffEngine = diffEngine;
        this.repo = repo;
    }

    /** 阶段B 微调。返回最新内容/样式 + 应用/拒绝的 diff 描述 + 是否触发一致性询问。 */
    public List<Object> polish(Dtos.PolishRequest req) {
        // 处理一致性开关（若请求显式带了则覆盖）
        if (req.getSyncLocalStyle() != null) {
            repo.setSyncLocalStyle(req.getProjectId(), req.getSyncLocalStyle() ? 1 : 0);
        }
        boolean syncEnabled = repo.loadSyncLocalStyle(req.getProjectId()) == 1;

        ChatDiffEngine.DiffResult result = diffEngine.polish(
                req.getProjectId(), req.getInstruction(), req.getFocusPage(), syncEnabled);

        List<Object> out = new ArrayList<>();
        out.add(result.contentJson);
        out.add(result.styleJson);
        out.add(result.appliedPatches);
        out.add(result.rejectedPatches);
        out.add(result.askUser);
        out.add(result.intent == null ? "CONTENT" : result.intent.name());
        return out;
    }

    /** 一致性开关切换。 */
    public void setSyncLocalStyle(Long projectId, boolean on) {
        repo.setSyncLocalStyle(projectId, on ? 1 : 0);
    }

    /** 撤销：回到指定快照 seq，或（null）回到初始生成态。返回是否成功。 */
    public List<Object> undo(Dtos.UndoRequest req) {
        Long projectId = req.getProjectId();
        List<Object> out = new ArrayList<>();
        out.add(false); // 占位 success
        if (req.getSnapshotSeq() == null) {
            // 回到初始生成态 = 无快照（初始即无 diff 状态）=> 无法回到"初稿"，
            // 这里语义上：快照里 seq 最小者接近初稿，但更准确是回退到当前 outline 保存的"基线"。
            // 简化：列表第一个快照（seq最小）视作初稿。
            List<ProjectRepository.Snapshot> snaps = repo.listSnapshots(projectId, "CONTENT");
            if (snaps.isEmpty()) {
                out.set(0, true);
                out.add(true); // 无可回退，视为已回到基线
                return out;
            }
            ProjectRepository.Snapshot first = snaps.get(0);
            return applySnapshot(projectId, first);
        } else {
            ProjectRepository.Snapshot snap = repo.snapshotBySeq(projectId, "CONTENT", req.getSnapshotSeq());
            if (snap == null) {
                out.set(0, false);
                out.add("快照不存在: seq=" + req.getSnapshotSeq());
                return out;
            }
            return applySnapshot(projectId, snap);
        }
    }

    private List<Object> applySnapshot(Long projectId, ProjectRepository.Snapshot snap) {
        List<Object> out = new ArrayList<>();
        ObjectNode content = JsonUtil.parseObject(snap.payloadJson);
        if (content != null) {
            // 同步快照里的 style（同 seq）
            ProjectRepository.Snapshot styleSnap = repo.snapshotBySeq(projectId, "STYLE", snap.seq);
            String styleJson = (styleSnap != null && styleSnap.payloadJson != null)
                    ? styleSnap.payloadJson : repo.loadStyle(projectId);
            repo.saveOutline(projectId, content.toString());
            repo.saveStyle(projectId, styleJson, "undo");
            out.add(true);
            out.add(JsonUtil.parse(repo.loadOutline(projectId)));
            out.add(JsonUtil.parse(styleJson));
        } else {
            out.add(false);
        }
        return out;
    }

    /** 撤销到指定快照（前端 undo 条点击）。 */
    public ObjectNode branchTo(JsonNode content, JsonNode style) {
        ObjectNode res = JsonUtil.MAPPER.createObjectNode();
        res.set("content", content);
        res.set("style", style);
        return res;
    }

    /** 会话历史（阶段A/B 通用读）。 */
    public String history(Long projectId) {
        return repo.loadConversationHistory(projectId);
    }
}
