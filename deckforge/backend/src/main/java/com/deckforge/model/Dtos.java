package com.deckforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * API 层请求/响应 DTO。统一无私有字段的轻量载体。
 */
public final class Dtos {

    private Dtos() {}

    /** 阶段A：生成前约束 + 首版生成请求 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GenerateRequest {
        private String intent;          // 用户原始想法/自然语言
        private String filePath;        // 已上传文件路径（可选）
        private String templateId;      // 选用模板（可选，缺省用内置默认）
        private String style;           // 风格约束（corporate/sunset/fresh/mono）
        private Integer pageCount;      // 页数约束
        private String density;         // 要点密度
        private String extraConstraint; // 其它自然语言约束（阶段A对话补充）
        private boolean skipStageA;     // 是否跳过阶段A直接生成

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        public String getFilePath() { return filePath; }
        public void setFilePath(String filePath) { this.filePath = filePath; }
        public String getTemplateId() { return templateId; }
        public void setTemplateId(String templateId) { this.templateId = templateId; }
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public Integer getPageCount() { return pageCount; }
        public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
        public String getDensity() { return density; }
        public void setDensity(String density) { this.density = density; }
        public String getExtraConstraint() { return extraConstraint; }
        public void setExtraConstraint(String extraConstraint) { this.extraConstraint = extraConstraint; }
        public boolean isSkipStageA() { return skipStageA; }
        public void setSkipStageA(boolean skipStageA) { this.skipStageA = skipStageA; }
    }

    /** 阶段A：约束对话请求 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class StageARequest {
        private String intent;
        private String reply;           // 用户对助手提问的回复
        private String style;
        private Integer pageCount;
        private String density;

        public String getIntent() { return intent; }
        public void setIntent(String intent) { this.intent = intent; }
        public String getReply() { return reply; }
        public void setReply(String reply) { this.reply = reply; }
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public Integer getPageCount() { return pageCount; }
        public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
        public String getDensity() { return density; }
        public void setDensity(String density) { this.density = density; }
    }

    /** 阶段B：预览微调对话请求（增量 diff，PRD 5.9） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PolishRequest {
        private Long projectId;
        private String instruction;      // 用户自然语言指令
        private Integer focusPage;       // 当前聚焦页（可空）
        private String focusElement;     // 聚焦元素路径（可空，P1")
        private Boolean syncLocalStyle;  // 一致性开关（覆盖会话级）

        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public String getInstruction() { return instruction; }
        public void setInstruction(String instruction) { this.instruction = instruction; }
        public Integer getFocusPage() { return focusPage; }
        public void setFocusPage(Integer focusPage) { this.focusPage = focusPage; }
        public String getFocusElement() { return focusElement; }
        public void setFocusElement(String focusElement) { this.focusElement = focusElement; }
        public Boolean getSyncLocalStyle() { return syncLocalStyle; }
        public void setSyncLocalStyle(Boolean syncLocalStyle) { this.syncLocalStyle = syncLocalStyle; }
    }

    /** 撤销请求 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class UndoRequest {
        private Long projectId;
        private Integer snapshotSeq;     // 目标快照序号（null -> 回到初始生成态）
        public Long getProjectId() { return projectId; }
        public void setProjectId(Long projectId) { this.projectId = projectId; }
        public Integer getSnapshotSeq() { return snapshotSeq; }
        public void setSnapshotSeq(Integer snapshotSeq) { this.snapshotSeq = snapshotSeq; }
    }

    /** 通用响应 */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ApiResponse {
        private boolean success;
        private String message;
        private Object data;

        public ApiResponse() {}
        public ApiResponse(boolean success, String message, Object data) {
            this.success = success;
            this.message = message;
            this.data = data;
        }
        public static ApiResponse ok(String message, Object data) { return new ApiResponse(true, message, data); }
        public static ApiResponse ok(String message) { return new ApiResponse(true, message, null); }
        public static ApiResponse err(String message) { return new ApiResponse(false, message, null); }

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public Object getData() { return data; }
        public void setData(Object data) { this.data = data; }
    }

    /** diff 指令（对话引擎内部 + API 传输） */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Patch {
        private String path;      // JSON Pointer: "slides[2].title" / "palette.primary"
        private String op;        // replace | add | remove
        private Object value;
        private Integer targetPage; // 可空：精确到页

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getOp() { return op; }
        public void setOp(String op) { this.op = op; }
        public Object getValue() { return value; }
        public void setValue(Object value) { this.value = value; }
        public Integer getTargetPage() { return targetPage; }
        public void setTargetPage(Integer targetPage) { this.targetPage = targetPage; }
    }

    /** LLM 配置保存请求 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LlmConfigRequest {
        private Long id;
        private String name;
        private String protocol;   // OPENAI_COMPAT | ANTHROPIC_COMPAT
        private String baseUrl;
        private String apiKey;
        private String model;
        private Boolean active;
        private Boolean anonymize;

        public Long getId() { return id; }
        public void setId(Long id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getProtocol() { return protocol; }
        public void setProtocol(String protocol) { this.protocol = protocol; }
        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public Boolean getActive() { return active; }
        public void setActive(Boolean active) { this.active = active; }
        public Boolean getAnonymize() { return anonymize; }
        public void setAnonymize(Boolean anonymize) { this.anonymize = anonymize; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ConfigPayload {
        public String name;
        public String protocol;
        public String baseUrl;
        public String apiKey;
        public String model;
        public boolean anonymize;
    }
}
