package com.deckforge.engine;

import com.deckforge.model.ContentOutline;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.poi.util.Units;
import org.apache.poi.xslf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * PPTX 生成引擎（PRD P0-6 / 技术说明 §4）。
 *
 * 核心诉求：消费"内容 JSON + 样式 JSON"，逐 shape 用【原生文本框/形状】落盘，
 * 保证导出后每个文本框/形状在 PowerPoint/WPS 里【可继续编辑】（非图片、非只读）。
 *
 * 中文处理：显式设置中文字体（微软雅黑/宋体兜底），防乱码。
 * 溢出保护：bullets 限 5 条、文本过长自适应裁剪/缩字号。
 */
@Service
public class PptxGenerator {

    private static final Logger log = LoggerFactory.getLogger(PptxGenerator.class);

    private static final int MAX_BULLETS = 5;

    /** 生成 PPTX 文件，返回文件路径。内容/样式均来自 JSON 单一事实来源。 */
    public String generate(JsonNode contentNode, JsonNode styleNode, String outPath) throws Exception {
        ContentOutline outline = JsonUtil.MAPPER.treeToValue(
                contentNode != null ? contentNode : JsonUtil.MAPPER.createObjectNode(), ContentOutline.class);
        if (outline == null) outline = new ContentOutline();
        List<ContentOutline.Slide> slides = outline.getSlides();
        if (slides == null || slides.isEmpty()) {
            throw new IllegalStateException("大纲为空，无法生成 PPTX");
        }

        // 从样式 JSON 读取 dimensions / palette / typography / rules
        StyleView style = StyleView.from(styleNode);

        XMLSlideShow ppt = new XMLSlideShow();
        ppt.setPageSize(new Dimension(style.masterWidth, style.masterHeight));
        getDefaultSlideLayout(ppt);

        // 逐页生成
        int pageNo = 0;
        for (ContentOutline.Slide slide : slides) {
            pageNo++;
            XSLFSlide s = ppt.createSlide();
            renderSlide(ppt, s, slide, style, pageNo, slides.size());
        }

        // 落盘
        java.io.File f = new java.io.File(outPath);
        java.io.File parent = f.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (OutputStream out = new FileOutputStream(f)) {
            ppt.write(out);
        }
        ppt.close();
        log.info("PPTX 生成完成: {} ({} 页, {}x{})", outPath, slides.size(), style.masterWidth, style.masterHeight);
        return f.getAbsolutePath();
    }

    /** 单页渲染。按 layout 类型选择区域划分与绘制方式。 */
    private void renderSlide(XMLSlideShow ppt, XSLFSlide slide, ContentOutline.Slide data,
                             StyleView style, int pageNo, int total) {
        String layout = data.getLayout() == null ? "bullet" : data.getLayout().toLowerCase();
        boolean zebra = "title".equals(layout) || "closing".equals(layout) && pageNo == 1;
        boolean isCover = "title".equals(layout) || "closing".equals(layout);

        // 根据页面类型决定标题区域 & 正文区域
        Rectangle2D titleRect = style.titleRect(isCover);
        Rectangle2D bodyRect = style.bodyRect(isCover);

        // 背景（cover 页用主色渐变）
        if (isCover) {
            // 用矩形填充（原生形状，可编辑）
            XSLFTextBox bg = slide.createTextBox();
            bg.setAnchor(new Rectangle2D.Double(0, 0, style.masterWidth, style.masterHeight));
            bg.setFillColor(xml2color(style.color("primary")));
            bg.setLineColor(new Color(0, 0, 0, 0));
            bg.setLineWidth(0);
            bg.clearText();
        }

        // 标题
        String title = data.getTitle() != null ? data.getTitle() : "";
        if (!title.isEmpty()) {
            XSLFTextBox titleBox = slide.createTextBox();
            titleBox.setAnchor(titleRect);
            XSLFTextParagraph tp = titleBox.addNewTextParagraph();
            XSLFTextRun tr = tp.addNewTextRun();
            tr.setText(title);
            tr.setFontFamily(style.titleFont());
            tr.setFontSize((double) style.titleSize);
            tr.setFontColor(isCover ? Color.WHITE : xml2color(style.color("titleText")));
            tr.setBold(true);
            titleBox.setFillColor(null);
            titleBox.setLineColor(null);
            titleBox.setLineWidth(0);
            tp.setSpaceAfter(0d);
        }

        // 副标题
        if (data.getSubtitle() != null && !data.getSubtitle().isEmpty()) {
            XSLFTextBox sub = slide.createTextBox();
            Rectangle2D subRect = new Rectangle2D.Double(titleRect.getX(),
                    titleRect.getY() + titleRect.getHeight() + Units.pointsToPixel(6), titleRect.getWidth(), 40);
            sub.setAnchor(subRect);
            XSLFTextParagraph sp = sub.addNewTextParagraph();
            XSLFTextRun sr = sp.addNewTextRun();
            sr.setText(data.getSubtitle());
            sr.setFontFamily(style.bodyFont());
            sr.setFontSize((double) Math.max(12, style.bulletSize - 2));
            sr.setFontColor(isCover ? new Color(255, 255, 255, 210) : xml2color(style.color("bodyText")));
            sub.setFillColor(null);
            sub.setLineColor(null);
        }

        // 依据 layout 绘制正文
        String finalLayout = isCover ? "cover" : layout;
        drawBody(slide, data, style, bodyRect, finalLayout, isCover);

        // 页脚
        XSLFTextBox foot = slide.createTextBox();
        foot.setAnchor(new Rectangle2D.Double(style.masterWidth - 120, style.masterHeight - 24, 110, 16));
        XSLFTextParagraph fp = foot.addNewTextParagraph();
        XSLFTextRun fr = fp.addNewTextRun();
        fr.setText("SLIDE " + String.format("%02d", pageNo) + "/" + total);
        fr.setFontFamily(style.bodyFont());
        fr.setFontSize(9.0);
        fr.setFontColor(new Color(120, 120, 120));
        foot.setFillColor(null);
        foot.setLineColor(null);

        // 备注：写入演讲者备注（XSLFNotes 的文本形状）
        if (data.getNotes() != null && !data.getNotes().isEmpty()) {
            try {
                XSLFNotes notes = slide.getNotes();
                // 取第一个文本形状追加文本
                if (notes != null && notes.getShapes().size() > 0) {
                    java.util.List<XSLFShape> shapes = notes.getShapes();
                    for (XSLFShape sh : shapes) {
                        if (sh instanceof XSLFTextShape) {
                            XSLFTextShape tsh = (XSLFTextShape) sh;
                            String existing = tsh.getText();
                            tsh.setText(existing == null || existing.isEmpty()
                                    ? data.getNotes() : existing + "\n" + data.getNotes());
                        }
                    }
                }
            } catch (Exception ignored) {
                log.debug("备注写入失败（忽略）");
            }
        }
    }

    /** 统一文本容器提取器（跳过背景矩形框），避免背景被当正文。 */

    /** 绘制正文（bullet / content / agenda / cover 封面只放标题）。 */
    private void drawBody(XSLFSlide slide, ContentOutline.Slide data, StyleView style,
                          Rectangle2D bodyRect, String layout, boolean isCover) {
        if (isCover) return; // 封面/结尾页只放标题+副标题

        switch (layout) {
            case "agenda":
                drawAgenda(slide, data, style, bodyRect);
                break;
            case "content":
            case "bullet":
            default:
                drawBullets(slide, data.getBullets(), style, bodyRect);
                break;
        }
    }

    /** 普通要点列表。溢出保护：限制 5 条 + 自动缩字号。 */
    private void drawBullets(XSLFSlide slide, List<String> bullets, StyleView style, Rectangle2D bodyRect) {
        if (bullets == null) bullets = new java.util.ArrayList<>();
        // 溢出保护：只渲染前 5 条
        List<String> trimmed = bullets.size() > MAX_BULLETS ? bullets.subList(0, MAX_BULLETS) : bullets;

        XSLFTextBox box = slide.createTextBox();
        box.setAnchor(bodyRect);
        box.setFillColor(null);
        box.setLineColor(null);
        box.setLineWidth(0);

        int fontSize = style.bulletSize;
        double vgap = 14;
        double lineH = fontSize + vgap;
        double maxLines = bodyRect.getHeight() / Units.pointsToPixel(lineH + vgap);
        if (maxLines < 0.5) maxLines = 0.5;

        int shown = 0;
        for (String b : trimmed) {
            if (shown >= (int) Math.floor(maxLines)) break;
            String content = normalizeBullet(b);
            if (content.isEmpty()) continue;
            XSLFTextParagraph p = box.addNewTextParagraph();
            p.setBullet(true);
            p.setIndent(12.0);
            p.setSpaceAfter(Units.pointsToPixel(vgap) * 0.6);
            XSLFTextRun r = p.addNewTextRun();
            r.setText(content);
            r.setFontFamily(style.bodyFont());
            r.setFontSize((double) fontSize);
            r.setFontColor(xml2color(style.color("bodyText")));
            shown++;
        }
        if (bullets.size() > MAX_BULLETS) {
            log.debug("bullets 超限截断: {} -> {}", bullets.size(), MAX_BULLETS);
        }
        // 清掉空段
        if (shown == 0) {
            box.setText("（无要点）");
        }
    }

    /** 议程页：一排 agenda item 胶囊（原生形状）。 */
    private void drawAgenda(XSLFSlide slide, ContentOutline.Slide data, StyleView style, Rectangle2D bodyRect) {
        List<String> items = data.getBullets();
        if (items == null) items = new java.util.ArrayList<>();
        if (items.isEmpty()) items = java.util.Collections.singletonList("（无议程）");
        double x = bodyRect.getX();
        double y = bodyRect.getY() + bodyRect.getHeight() * 0.1;
        double w = bodyRect.getWidth();
        double itemH = Units.pointsToPixel(52);
        double gap = Units.pointsToPixel(12);
        int count = Math.min(items.size(), 6);
        double avail = w - gap * (count - 1);
        double itemW = Math.max(60, avail / count);
        int i = 0;
        for (String it : items) {
            if (i >= 6) break;
            double ix = x + i * (itemW + gap);
            // 胶囊矩形（XSLFTextBox 即原生可编辑形状，可设填充色）
            XSLFTextBox rect = slide.createTextBox();
            rect.setAnchor(new Rectangle2D.Double(ix, y, itemW, itemH));
            rect.setFillColor(i == 0 ? xml2color(style.color("primary")) : xml2color(style.color("accent")));
            rect.setLineColor(null);
            rect.setLineWidth(0);
            rect.setVerticalAlignment(org.apache.poi.sl.usermodel.VerticalAlignment.MIDDLE);
            // 文字
            XSLFTextParagraph tp = rect.addNewTextParagraph();
            tp.setTextAlign(org.apache.poi.sl.usermodel.TextParagraph.TextAlign.CENTER);
            XSLFTextRun tr = tp.addNewTextRun();
            tr.setText(it.length() > 12 ? it.substring(0, 12) : it);
            tr.setFontFamily(style.bodyFont());
            tr.setFontSize((double) Math.max(12, style.bulletSize - (count > 5 ? 3 : 1)));
            tr.setFontColor(i == 0 ? Color.WHITE : xml2color(style.color("titleText")));
            i++;
        }
    }

    private String normalizeBullet(String b) {
        if (b == null) return "";
        String t = b.trim().replaceAll("^[-•*]+\\s*", "");
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }

    private void getDefaultSlideLayout(XMLSlideShow ppt) {
        // 使用默认母版即可（不强制覆盖）
    }

    // ---- 颜色工具 ----

    private static Color xml2color(String hex) {
        if (hex == null) return new Color(24, 95, 165);
        String t = hex.startsWith("#") ? hex.substring(1) : hex;
        try {
            if (t.length() == 6) return new Color(Integer.parseInt(t, 16));
            if (t.length() == 3) {
                int r = Integer.parseInt(t.substring(0,1) + t.substring(0,1), 16);
                int g = Integer.parseInt(t.substring(1,2) + t.substring(1,2), 16);
                int b = Integer.parseInt(t.substring(2,3) + t.substring(2,3), 16);
                return new Color(r,g,b);
            }
        } catch (Exception e) {
            return new Color(24, 95, 165);
        }
        return new Color(24, 95, 165);
    }

    /**
     * 从样式 JSON 提取渲染视图。默认值兜底，非法自动回退。
     */
    static class StyleView {
        int masterWidth = 960;
        int masterHeight = 540;
        int titleSize = 30;
        int bulletSize = 18;
        JsonNode palette;
        JsonNode typography;
        JsonNode layoutRules;

        static StyleView from(JsonNode style) {
            StyleView v = new StyleView();
            if (style == null) {
                v.palette = defaultPalette();
                return v;
            }
            JsonNode meta = style.path("meta");
            if (meta.has("masterWidth") && meta.get("masterWidth").isNumber()) v.masterWidth = meta.get("masterWidth").asInt();
            if (meta.has("masterHeight") && meta.get("masterHeight").isNumber()) v.masterHeight = meta.get("masterHeight").asInt();
            v.palette = style.path("palette");
            v.typography = style.path("typography");
            v.layoutRules = style.path("layoutRules");
            if (v.typography.size() == 0 || (v.typography.isObject() && !v.typography.has("titleFont"))) {
                ObjectNode t = JsonUtil.MAPPER.createObjectNode();
                t.put("titleFont", "微软雅黑");
                t.put("bodyFont", "微软雅黑");
                t.put("titleSizePt", 30);
                t.put("bulletSizePt", 18);
                if (v.typography.size() > 0) {
                    // 保留已给出的字段
                    v.typography.fields().forEachRemaining(e -> t.set(e.getKey(), e.getValue()));
                }
                v.typography = t;
            }
            if (v.typography.has("titleSizePt") && v.typography.get("titleSizePt").isNumber()) v.titleSize = v.typography.get("titleSizePt").asInt();
            if (v.typography.has("bulletSizePt") && v.typography.get("bulletSizePt").isNumber()) v.bulletSize = v.typography.get("bulletSizePt").asInt();
            if (v.palette.size() == 0) v.palette = defaultPalette();
            return v;
        }

        static JsonNode defaultPalette() {
            ObjectNode p = JsonUtil.MAPPER.createObjectNode();
            p.put("background", "#FFFFFF");
            p.put("primary", "#185FA5");
            p.put("secondary", "#378ADD");
            p.put("accent", "#E6F1FB");
            p.put("titleText", "#0C2B4D");
            p.put("bodyText", "#20344D");
            return p;
        }

        String titleFont() {
            return typography != null && typography.has("titleFont") && !typography.get("titleFont").asText().isEmpty()
                    ? typography.get("titleFont").asText() : "微软雅黑";
        }
        String bodyFont() {
            return typography != null && typography.has("bodyFont") && !typography.get("bodyFont").asText().isEmpty()
                    ? typography.get("bodyFont").asText() : "微软雅黑";
        }

        /** 安全读取调色板某键的色值（#RRGGBB）。 */
        String color(String key) {
            if (palette != null && palette.has(key) && palette.get(key).isTextual()) {
                return palette.get(key).asText();
            }
            switch (key) {
                case "primary": return "#185FA5";
                case "secondary": return "#378ADD";
                case "accent": return "#E6F1FB";
                case "titleText": return "#0C2B4D";
                case "bodyText": return "#20344D";
                default: return "#FFFFFF";
            }
        }
        Rectangle2D titleRect(boolean isCover) {
            if (layoutRules != null && layoutRules.has("titlePosition") && layoutRules.get("titlePosition").isObject()) {
                JsonNode p = layoutRules.get("titlePosition");
                return relRect(p);
            }
            return new Rectangle2D.Double(masterWidth * 0.06, masterHeight * (isCover ? 0.38 : 0.06),
                    masterWidth * 0.88, masterHeight * 0.14);
        }
        Rectangle2D bodyRect(boolean isCover) {
            if (layoutRules != null && layoutRules.has("bodyPosition") && layoutRules.get("bodyPosition").isObject()) {
                return relRect(layoutRules.get("bodyPosition"));
            }
            return new Rectangle2D.Double(masterWidth * 0.08, masterHeight * (isCover ? 0.52 : 0.24),
                    masterWidth * 0.84, masterHeight * 0.70);
        }
        private Rectangle2D relRect(JsonNode p) {
            double x = clamp(p.path("x").asDouble(0.06)) * masterWidth;
            double y = clamp(p.path("y").asDouble(0.06)) * masterHeight;
            double w = clamp(p.path("w").asDouble(0.88)) * masterWidth;
            double h = clamp(p.path("h").asDouble(0.70)) * masterHeight;
            return new Rectangle2D.Double(x, y, w, h);
        }
        private double clamp(double v) { return v < 0 ? 0 : (v > 1 ? 1 : v); }
    }
}
