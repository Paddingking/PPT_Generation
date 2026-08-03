package com.deckforge.controller;

import com.deckforge.service.TextExtractor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 输入文件上传接口（P0-1）：上传 .docx / .md / .txt，提取文本。
 */
@RestController
@RequestMapping("/api/upload")
@CrossOrigin(origins = "*")
public class FileController {

    private final TextExtractor textExtractor;

    public FileController(TextExtractor textExtractor) {
        this.textExtractor = textExtractor;
    }

    /** 上传并返回提取文本（供前端作为 intent 输入）。 */
    @PostMapping("/input")
    public ResponseEntity<Map<String, Object>> upload(@RequestParam("file") MultipartFile file) {
        Map<String, Object> m = new HashMap<>();
        try {
            if (file.isEmpty()) {
                throw new IllegalArgumentException("文件为空");
            }
            // 限 10MB
            if (file.getSize() > 10 * 1024 * 1024) {
                throw new IllegalArgumentException("文件超过 10MB 限制");
            }
            String text = textExtractor.extract(file);
            // 保存到 uploads 目录（供项目引用）
            File dir = new File("data/uploads");
            if (!dir.exists()) dir.mkdirs();
            String orig = file.getOriginalFilename() == null ? "input" : file.getOriginalFilename();
            String savedName = UUID.randomUUID().toString().substring(0, 8) + "_" + sanitize(orig);
            File dest = new File(dir, savedName);
            try (FileOutputStream fos = new FileOutputStream(dest)) {
                fos.write(file.getBytes());
            }
            m.put("success", true);
            m.put("text", text.length() > 20000 ? text.substring(0, 20000) : text);
            m.put("fileName", orig);
            m.put("path", dest.getAbsolutePath());
            m.put("chars", (long) text.length());
            return ResponseEntity.ok(m);
        } catch (Exception e) {
            m.put("success", false);
            m.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(m);
        }
    }

    private String sanitize(String name) {
        return name.replaceAll("[\\\\/:*?\"<>|]", "_");
    }
}
