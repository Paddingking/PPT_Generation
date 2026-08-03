package com.deckforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板骨架（PRD 5.6）—— 从外部 .pptx 提取或内置预设，作为版式与风格定义。
 * 新内容 + 骨架 -> 套用生成。这是"模板驱动"能力的载体。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TemplateSkeleton {

    private String id;
    private String sourceType;        // IMPORTED | BUILTIN
    private String sourceFile;
    private String aspectRatio;       // 16:9 / 4:3
    private int masterWidth;
    private int masterHeight;
    /** 页面类型 -> 布局坐标集合 */
    private Map<String, List<PlaceholderRegion>> layoutGroups;
    private Palette palette;
    private Typography typography;
    private boolean recognized;

    public TemplateSkeleton() {
        this.layoutGroups = new LinkedHashMap<>();
        this.palette = new Palette();
        this.typography = new Typography();
        this.aspectRatio = "16:9";
        this.masterWidth = 960;
        this.masterHeight = 540;
        this.recognized = true;
    }

    // ---- getters / setters ----

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSourceType() { return sourceType; }
    public void setSourceType(String sourceType) { this.sourceType = sourceType; }
    public String getSourceFile() { return sourceFile; }
    public void setSourceFile(String sourceFile) { this.sourceFile = sourceFile; }
    public String getAspectRatio() { return aspectRatio; }
    public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }
    public int getMasterWidth() { return masterWidth; }
    public void setMasterWidth(int masterWidth) { this.masterWidth = masterWidth; }
    public int getMasterHeight() { return masterHeight; }
    public void setMasterHeight(int masterHeight) { this.masterHeight = masterHeight; }
    public Map<String, List<PlaceholderRegion>> getLayoutGroups() { return layoutGroups; }
    public void setLayoutGroups(Map<String, List<PlaceholderRegion>> layoutGroups) { this.layoutGroups = layoutGroups; }
    public Palette getPalette() { return palette; }
    public void setPalette(Palette palette) { this.palette = palette; }
    public Typography getTypography() { return typography; }
    public void setTypography(Typography typography) { this.typography = typography; }
    public boolean isRecognized() { return recognized; }
    public void setRecognized(boolean recognized) { this.recognized = recognized; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlaceholderRegion {
        private String type;     // TITLE / CONTENT / SUBTITLE / PICTURE / NOTES
        private double x, y, w, h; // 相对百分比坐标 0~1

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public double getX() { return x; }
        public void setX(double x) { this.x = x; }
        public double getY() { return y; }
        public void setY(double y) { this.y = y; }
        public double getW() { return w; }
        public void setW(double w) { this.w = w; }
        public double getH() { return h; }
        public void setH(double h) { this.h = h; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Palette {
        private String background;
        private String primary;
        private String secondary;
        private String accent;
        private String titleText;
        private String bodyText;

        public String getBackground() { return background; }
        public void setBackground(String background) { this.background = background; }
        public String getPrimary() { return primary; }
        public void setPrimary(String primary) { this.primary = primary; }
        public String getSecondary() { return secondary; }
        public void setSecondary(String secondary) { this.secondary = secondary; }
        public String getAccent() { return accent; }
        public void setAccent(String accent) { this.accent = accent; }
        public String getTitleText() { return titleText; }
        public void setTitleText(String titleText) { this.titleText = titleText; }
        public String getBodyText() { return bodyText; }
        public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Typography {
        private String titleFont;
        private String bodyFont;
        private int titleSizePt;
        private int bulletSizePt;
        private int noteSizePt;

        public String getTitleFont() { return titleFont; }
        public void setTitleFont(String titleFont) { this.titleFont = titleFont; }
        public String getBodyFont() { return bodyFont; }
        public void setBodyFont(String bodyFont) { this.bodyFont = bodyFont; }
        public int getTitleSizePt() { return titleSizePt; }
        public void setTitleSizePt(int titleSizePt) { this.titleSizePt = titleSizePt; }
        public int getBulletSizePt() { return bulletSizePt; }
        public void setBulletSizePt(int bulletSizePt) { this.bulletSizePt = bulletSizePt; }
        public int getNoteSizePt() { return noteSizePt; }
        public void setNoteSizePt(int noteSizePt) { this.noteSizePt = noteSizePt; }
    }
}
