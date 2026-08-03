package com.deckforge.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.Iterator;
import java.util.Map;

/**
 * JSON 工具：解析、安全导航、JSON Pointer 定位/合并。
 * 用于内容 JSON 与样式 JSON 的单一事实来源处理。
 */
public final class JsonUtil {
    private JsonUtil() {}

    public static final ObjectMapper MAPPER = new ObjectMapper();

    /** 解析字符串为 JsonNode，失败返回 null。 */
    public static JsonNode parse(String json) {
        if (json == null || json.trim().isEmpty()) return null;
        try {
            return MAPPER.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    public static ObjectNode parseObject(String json) {
        JsonNode n = parse(json);
        return n != null && n.isObject() ? (ObjectNode) n : null;
    }

    /** 提取 LLM 返回文本中首个 JSON 对象（兼容前后有多余文字）。 */
    public static JsonNode extractJsonFromText(String text) {
        if (text == null) return null;
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) return null;
        return parse(text.substring(start, end + 1));
    }

    /**
     * 按 JSON Pointer 路径定位节点。
     * 支持 "slides[2].title"、"palette.primary"、"slides[0].bullets" 等。
     * 返回 null 表示路径不存在（越界）。
     */
    public static JsonNode atPath(JsonNode root, String path) {
        if (root == null || path == null) return null;
        String[] parts = splitPointer(path);
        JsonNode cur = root;
        for (String part : parts) {
            if (cur == null) return null;
            int idx = findArrayIndex(part);
            if (idx >= 0) {
                // 数组索引
                String field = part.substring(0, part.indexOf('['));
                if (!field.isEmpty()) {
                    cur = cur.get(field);
                }
                if (cur == null || !cur.isArray() || idx >= cur.size()) return null;
                cur = cur.get(idx);
            } else {
                cur = cur.get(part);
            }
        }
        return cur;
    }

    /** 将 value 设置到 path 指向位置（replace / add）。返回 false 说明位置越界无法写入。 */
    public static boolean setAtPath(ObjectNode root, String path, JsonNode value) {
        if (root == null || path == null) return false;
        String[] parts = splitPointer(path);
        JsonNode cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            int idx = findArrayIndex(part);
            if (idx >= 0) {
                String field = part.substring(0, part.indexOf('['));
                if (!field.isEmpty()) cur = cur.get(field);
                if (cur == null || !cur.isArray()) return false;
                if (idx >= cur.size()) return false;
                cur = cur.get(idx);
            } else {
                cur = cur.get(part);
                if (cur == null) return false;
            }
        }
        String last = parts[parts.length - 1];
        int lastIdx = findArrayIndex(last);
        if (lastIdx >= 0) {
            String field = last.substring(0, last.indexOf('['));
            JsonNode arrNode = cur.get(field);
            if (arrNode == null || !arrNode.isArray() || lastIdx >= arrNode.size()) return false;
            ObjectNode conv = (ObjectNode) cur;
            ArrayNode arr = (ArrayNode) arrNode;
            arr.set(lastIdx, value);
            return true;
        } else {
            ObjectNode conv = cur instanceof ObjectNode ? (ObjectNode) cur : null;
            if (conv == null) return false;
            conv.set(last, value);
            return true;
        }
    }

    /** 移除 path 指向节点（remove op）。 */
    public static boolean removeAtPath(ObjectNode root, String path) {
        if (root == null || path == null) return false;
        String[] parts = splitPointer(path);
        JsonNode cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];
            int idx = findArrayIndex(part);
            if (idx >= 0) {
                String field = part.substring(0, part.indexOf('['));
                if (!field.isEmpty()) cur = cur.get(field);
                if (cur == null || !cur.isArray() || idx >= cur.size()) return false;
                cur = cur.get(idx);
            } else {
                cur = cur.get(part);
                if (cur == null) return false;
            }
        }
        String last = parts[parts.length - 1];
        int lastIdx = findArrayIndex(last);
        if (lastIdx >= 0) {
            String field = last.substring(0, last.indexOf('['));
            JsonNode arrNode = cur.get(field);
            if (arrNode == null || !arrNode.isArray() || lastIdx >= arrNode.size()) return false;
            ((ArrayNode) arrNode).remove(lastIdx);
            return true;
        }
        if (cur instanceof ObjectNode) {
            ObjectNode conv = (ObjectNode) cur;
            if (conv.has(last)) {
                conv.remove(last);
                return true;
            }
        }
        return false;
    }

    // ---- pointer split helpers ----

    private static String[] splitPointer(String path) {
        // 按 '.' 或 '[' 切分，需保留数组索引信息
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '.') {
                if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
            } else if (c == '[') {
                if (sb.length() > 0) { out.add(sb.toString()); sb.setLength(0); }
                // 读取 index ]
                int j = i + 1;
                while (j < path.length() && path.charAt(j) != ']') j++;
                String idxPart = path.substring(i, Math.min(j + 1, path.length()));
                out.add(idxPart);
                i = j;
            } else {
                sb.append(c);
            }
        }
        if (sb.length() > 0) out.add(sb.toString());
        return out.toArray(new String[0]);
    }

    private static int findArrayIndex(String part) {
        int b = part.indexOf('[');
        if (b < 0) return -1;
        int e = part.indexOf(']');
        if (e <= b) return -1;
        String num = part.substring(b + 1, e).trim();
        try {
            return Integer.parseInt(num);
        } catch (Exception ex) {
            return -1;
        }
    }

    /** 深拷贝 JsonNode。 */
    public static JsonNode deepCopy(JsonNode node) {
        if (node == null) return null;
        return node.deepCopy();
    }

    /** 计数路径命中数（辅助调试）。 */
    public static int count(ObjectNode root, String path) {
        return atPath(root, path) == null ? 0 : 1;
    }

    public static JsonNode toNode(Object value) {
        if (value instanceof JsonNode) return (JsonNode) value;
        try {
            return MAPPER.valueToTree(value);
        } catch (Exception e) {
            return null;
        }
    }
}
