package com.deckforge.engine;

import com.deckforge.model.Dtos;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * 增量 diff 补丁校验器（PRD 5.9.2 / 技术说明 §7.2）。
 *
 * 铁律：path 越界 -> 拒绝；value 非法 -> 回退；越界保护 -> 未指代元素不得改动。
 */
public class PatchValidator {

    /**
     * 校验单条 diff。
     * @param patch   待校验补丁
     * @param current 当前内容 或 样式 JSON
     * @param isStyle 是否样式域（样式全局字段允许 targetPage==-1）
     * @return 校验结果（reason 为 null 表示通过）
     */
    public static Result validate(Dtos.Patch patch, JsonNode current, boolean isStyle) {
        if (patch == null || patch.getPath() == null || patch.getPath().trim().isEmpty()) {
            return Result.reject("path 为空");
        }
        String path = patch.getPath().trim();
        String op = patch.getOp() == null ? "replace" : patch.getOp().trim().toLowerCase();
        if (!op.equals("replace") && !op.equals("add") && !op.equals("remove")) {
            return Result.reject("非法 op: " + op);
        }

        // 越界保护：targetPage 为 null 且非样式全局字段 -> 拒绝
        Integer tp = patch.getTargetPage();
        boolean touchedGlobalStyle = isStyle && (tp == null || tp == -1) && path.startsWith("palette")
                || (isStyle && (tp == null || tp == -1) && path.startsWith("typography"));
        if (!touchedGlobalStyle && (tp == null)) {
            // 允许内容无 targetPage 但 path 指向明确页（如 slides[2]）
            if (!path.startsWith("slides[")) {
                return Result.reject("未指定 targetPage 且非样式全局字段，拒绝越界 (" + path + ")");
            }
        }

        // path 存在性校验
        JsonNode target = JsonUtil.atPath(current, path);
        if ("replace".equals(op) || "remove".equals(op)) {
            if (target == null) {
                return Result.reject("path 越界（节点不存在）: " + path);
            }
        }
        // remove
        if ("remove".equals(op)) {
            return Result.pass();
        }

        // value 合法性校验
        JsonNode value = JsonUtil.toNode(patch.getValue());
        if (value == null || value.isNull()) {
            return Result.reject("value 为空");
        }
        // 颜色/数值域校验（仅当 value 是标量）
        if (value.isTextual() && path.endsWith("primary") || isColorPath(path)) {
            if (!isHexColor(value.asText())) {
                return Result.reject("非法颜色值: " + value.asText());
            }
        }
        if (isNumberRangeField(path)) {
            if (!value.isNumber() || value.asInt() < 8 || value.asInt() > 72) {
                return Result.reject("非法字号: " + value.asText());
            }
        }
        if (isCoordinatePath(path)) {
            if (value.isNumber()) {
                double v = value.asDouble();
                if (v < 0 || v > 1) return Result.reject("坐标越界: " + v);
            }
        }
        return Result.pass();
    }

    private static boolean isColorPath(String path) {
        return path.contains("palette") || path.endsWith("Text") || path.endsWith("accbar");
    }

    private static boolean isNumberRangeField(String path) {
        return path.contains("SizePt") || path.contains("sizePt");
    }

    private static boolean isCoordinatePath(String path) {
        return path.contains("Position") || path.matches(".*\\.(x|y|w|h)$");
    }

    private static boolean isHexColor(String s) {
        if (s == null) return false;
        String t = s.startsWith("#") ? s.substring(1) : s;
        return t.length() == 6 && t.matches("[0-9a-fA-F]{6}");
    }

    public static class Result {
        public boolean pass;
        public String reason;
        private Result(boolean pass, String reason) { this.pass = pass; this.reason = reason; }
        public static Result pass() { return new Result(true, null); }
        public static Result reject(String reason) { return new Result(false, reason); }
    }
}
