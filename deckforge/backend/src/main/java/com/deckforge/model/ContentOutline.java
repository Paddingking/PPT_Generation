package com.deckforge.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * 内容大纲 JSON（PRD 5.4）—— 预览与生成的单一事实来源之一。
 * LLM 产出该结构，前端按它渲染，POI 按它输出。
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ContentOutline {

    private Meta meta;
    private List<Slide> slides;

    public Meta getMeta() { return meta; }
    public void setMeta(Meta meta) { this.meta = meta; }
    public List<Slide> getSlides() { return slides; }
    public void setSlides(List<Slide> slides) { this.slides = slides; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Meta {
        private String title;
        private String theme;
        private String aspectRatio;
        private String author;

        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getTheme() { return theme; }
        public void setTheme(String theme) { this.theme = theme; }
        public String getAspectRatio() { return aspectRatio; }
        public void setAspectRatio(String aspectRatio) { this.aspectRatio = aspectRatio; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }

        /** 在 JsonNode 基础上做兜底：保证有默认值，防 NPE。 */
        public static Meta fromNode(JsonNode node) {
            Meta m = new Meta();
            if (node == null) return m;
            if (node.has("title") && node.get("title").isTextual()) m.setTitle(node.get("title").asText());
            if (node.has("theme") && node.get("theme").isTextual()) m.setTheme(node.get("theme").asText());
            if (node.has("aspectRatio") && node.get("aspectRatio").isTextual()) m.setAspectRatio(node.get("aspectRatio").asText());
            if (node.has("author") && node.get("author").isTextual()) m.setAuthor(node.get("author").asText());
            return m;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Slide {
        private String layout;
        private String title;
        private String subtitle;
        private List<String> bullets;
        private String notes;
        private String imagePrompt;

        public String getLayout() { return layout; }
        public void setLayout(String layout) { this.layout = layout; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getSubtitle() { return subtitle; }
        public void setSubtitle(String subtitle) { this.subtitle = subtitle; }
        public List<String> getBullets() { return bullets; }
        public void setBullets(List<String> bullets) { this.bullets = bullets; }
        public String getNotes() { return notes; }
        public void setNotes(String notes) { this.notes = notes; }
        public String getImagePrompt() { return imagePrompt; }
        public void setImagePrompt(String imagePrompt) { this.imagePrompt = imagePrompt; }
    }

    /** 持久化到数据库所用。 */
    public JsonNode toJson() {
        throw new UnsupportedOperationException("Use Jackson ObjectMapper instead");
    }
}
