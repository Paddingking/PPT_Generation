package com.deckforge.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * 输入文件文本抽取（PRD P0-1）：.docx / .md / .txt 提取纯文本。
 */
@Service
public class TextExtractor {

    private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);

    public String extract(MultipartFile file) throws Exception {
        String name = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase();
        if (name.endsWith(".docx")) {
            return fromDocx(file.getInputStream());
        } else if (name.endsWith(".txt") || name.endsWith(".md")) {
            return fromText(file.getInputStream());
        } else {
            throw new IllegalArgumentException("不支持的文件类型，仅支持 .docx / .md / .txt");
        }
    }

    private String fromDocx(InputStream in) throws Exception {
        // 防止 Zip bomb / 超大文件：限制文档段落数
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(in)) {
            int paragraphCount = 0;
            for (XWPFParagraph p : doc.getParagraphs()) {
                String text = p.getText();
                if (text != null && !text.trim().isEmpty()) {
                    sb.append(text.trim()).append("\n");
                    paragraphCount++;
                    if (paragraphCount > 5000) { // 防爆
                        sb.append("\n（文档过长，已截断）");
                        break;
                    }
                }
            }
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) throw new IllegalArgumentException("未从文档提取到文本内容");
        return result;
    }

    private String fromText(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            int lineCount = 0;
            while ((line = r.readLine()) != null) {
                sb.append(line).append("\n");
                lineCount++;
                if (lineCount > 10000) { // 防爆
                    sb.append("\n（文本过长，已截断）");
                    break;
                }
            }
        }
        String result = sb.toString().trim();
        if (result.isEmpty()) throw new IllegalArgumentException("未获取到文本内容");
        return result;
    }
}
