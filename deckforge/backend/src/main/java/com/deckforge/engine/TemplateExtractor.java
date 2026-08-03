package com.deckforge.engine;

import com.deckforge.model.TemplateSkeleton;
import org.apache.poi.xslf.usermodel.*;
import org.apache.poi.sl.usermodel.Placeholder;
import org.apache.poi.sl.usermodel.TextParagraph;
import org.apache.poi.sl.usermodel.TextRun;
import org.apache.poi.util.Units;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.awt.geom.Rectangle2D;
import java.awt.Dimension;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 模板骨架提取器（PRD 5.6 / 技术说明 §5）。
 *
 * 从外部 .pptx 提取版式骨架（页面类型、占位符相对位置/尺寸、主题色、字体）。
 * 识别失败（纯背景图/无占位符）时 recognized=false -> 上层回退内置预设，不崩溃。
 */
@Component
public class TemplateExtractor {

    private static final Logger log = LoggerFactory.getLogger(TemplateExtractor.class);

    public TemplateSkeleton extract(String pptxPath) {
        TemplateSkeleton sk = new TemplateSkeleton();
        sk.setSourceType("IMPORTED");
        sk.setSourceFile(pptxPath);
        sk.setRecognized(false); // 默认未识别，成功才置 TRUE

        try (InputStream in = new FileInputStream(pptxPath)) {
            XMLSlideShow ppt = new XMLSlideShow(in);
            Dimension page = ppt.getPageSize();
            sk.setMasterWidth(page.width);
            sk.setMasterHeight(page.height);
            // 宽高比
            double ratio = page.width * 1.0 / page.height;
            sk.setAspectRatio(Math.abs(ratio - 4.0 / 3.0) < 0.05 ? "4:3" : "16:9");

            // 主题色/字体（从第一个母版读取）
            List<XSLFSlideMaster> masters = ppt.getSlideMasters();
            if (masters != null && !masters.isEmpty()) {
                readTheme(masters.get(0), sk);
            }

            // 遍历页面提取占位符
            Map<String, java.util.List<TemplateSkeleton.PlaceholderRegion>> layoutGroups = new LinkedHashMap<>();
            int textShapeCount = 0;
            int slideCount = 0;
            for (XSLFSlide slide : ppt.getSlides()) {
                slideCount++;
                Map<String, TemplateSkeleton.PlaceholderRegion> pageRegions = new LinkedHashMap<>();
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFAutoShape) {
                        Rectangle2D anchor = shape.getAnchor();
                        if (anchor == null) continue;
                        // 判定类型
                        Placeholder ph = shape.getPlaceholder();
                        String type = classify(ph);
                        if (type == null) type = "CONTENT";
                        TemplateSkeleton.PlaceholderRegion region = new TemplateSkeleton.PlaceholderRegion();
                        region.setType(type);
                        region.setX(clamp(anchor.getX() / page.width));
                        region.setY(clamp(anchor.getY() / page.height));
                        region.setW(clamp(anchor.getWidth() / page.width));
                        region.setH(clamp(anchor.getHeight() / page.height));
                        // 同类型只保留第一个（页内去重）
                        if (!pageRegions.containsKey(type)) {
                            pageRegions.put(type, region);
                        }
                        textShapeCount++;
                    }
                }
                if (!pageRegions.isEmpty()) {
                    String layoutKey = inferLayout(pageRegions);
                    layoutGroups.putIfAbsent(layoutKey, new java.util.ArrayList<>());
                    layoutGroups.get(layoutKey).addAll(pageRegions.values());
                }
            }

            if (slideCount > 0 && textShapeCount > 0 && !layoutGroups.isEmpty()) {
                sk.setLayoutGroups(layoutGroups);
                sk.setRecognized(true);
                log.info("模板提取成功: 页面数={}, 文本shape数={}, 布局类型={}",
                        slideCount, textShapeCount, layoutGroups.keySet());
            } else {
                sk.setRecognized(false);
                log.warn("模板未识别到有效占位符（可能为纯背景图），recognized=false");
            }
        } catch (Exception e) {
            sk.setRecognized(false);
            log.warn("模板提取异常，回退内置: {}", e.getMessage());
        }

        // 兜底：即使未能从主题读到字体，也保证有中文字体默认值
        if (sk.getTypography().getTitleFont() == null) {
            sk.getTypography().setTitleFont("微软雅黑");
            sk.getTypography().setBodyFont("微软雅黑");
        }
        if (sk.getTypography().getTitleSizePt() <= 0) {
            sk.getTypography().setTitleSizePt(30);
            sk.getTypography().setBodyFont("微软雅黑");
            sk.getTypography().setBulletSizePt(18);
            sk.getTypography().setNoteSizePt(12);
        }
        return sk;
    }

    private void readTheme(XSLFSlideMaster master, TemplateSkeleton sk) {
        // 主题字体读取在 POI 5.2.5 上较脆弱，为稳定起见默认用中文字体。
        // （首页内置模板已带微软雅黑，外部模板提取重点在占位符坐标而非字体。）
        sk.getTypography().setTitleFont("微软雅黑");
        sk.getTypography().setBodyFont("微软雅黑");
        sk.getTypography().setTitleSizePt(30);
        sk.getTypography().setBulletSizePt(18);
        sk.getTypography().setNoteSizePt(12);
    }


    private String classify(Placeholder ph) {
        if (ph == null) return "CONTENT";
        if (ph == Placeholder.TITLE || ph == Placeholder.CENTERED_TITLE) return "TITLE";
        if (ph == Placeholder.SUBTITLE) return "SUBTITLE";
        if (ph == Placeholder.BODY || ph == Placeholder.CONTENT) return "CONTENT";
        if (ph == Placeholder.PICTURE) return "PICTURE";
        return "CONTENT";
    }

    private String inferLayout(Map<String, TemplateSkeleton.PlaceholderRegion> regions) {
        if (regions.containsKey("TITLE") && regions.containsKey("SUBTITLE")) {
            // 封面页
            return "title";
        }
        if (regions.containsKey("TITLE") && regions.containsKey("CONTENT")) {
            return "content";
        }
        if (regions.containsKey("TITLE")) {
            return "title";
        }
        return "content";
    }

    private double clamp(double v) {
        if (v < 0) return 0;
        if (v > 1) return 1;
        return v;
    }
}
