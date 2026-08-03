package com.deckforge.controller;

import com.deckforge.model.Dtos;
import com.deckforge.repository.ProjectRepository;
import com.deckforge.service.ProjectService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * 主接口：健康检查 / 项目生成 / 项目详情 / 模板库 / 模板导入 / 导出。
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class AppController {

    private final ProjectService projectService;

    public AppController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> m = new HashMap<>();
        m.put("status", "UP");
        m.put("service", "DeckForge · PPT 工作台");
        m.put("version", "1.0.0");
        m.put("defaultProvider", "mock/真实LLM按配置");
        return m;
    }

    /** 首版生成（阶段A 或 跳过直生成）。 */
    @PostMapping("/projects/generate")
    public Dtos.ApiResponse generate(@RequestBody Dtos.GenerateRequest req) {
        try {
            Object result = projectService.generate(req);
            return Dtos.ApiResponse.ok("内容 + 版式双稿已生成", result);
        } catch (Exception e) {
            return Dtos.ApiResponse.err("生成失败: " + e.getMessage());
        }
    }

    /** 项目详情。 */
    @GetMapping("/projects/{id}")
    public Dtos.ApiResponse detail(@PathVariable Long id) {
        try {
            return Dtos.ApiResponse.ok("项目详情", projectService.projectDetail(id));
        } catch (Exception e) {
            return Dtos.ApiResponse.err(e.getMessage());
        }
    }

    /** 项目列表。 */
    @GetMapping("/projects")
    public Dtos.ApiResponse list() {
        Map<String, Object> m = new HashMap<>();
        m.put("projects", projectService.listRaw());
        return Dtos.ApiResponse.ok("项目列表", m);
    }

    /** 模板库。 */
    @GetMapping("/templates")
    public Dtos.ApiResponse templates() {
        Map<String, Object> m = new HashMap<>();
        m.put("templates", projectService.listTemplates());
        return Dtos.ApiResponse.ok("模板库", m);
    }

    /** 导入 .pptx 提取骨架。 */
    @PostMapping("/templates/import")
    public Dtos.ApiResponse importTemplate(@RequestParam("file") MultipartFile file) {
        try {
            return Dtos.ApiResponse.ok("模板解析完成", projectService.importTemplate(file));
        } catch (Exception e) {
            return Dtos.ApiResponse.err("模板导入失败: " + e.getMessage());
        }
    }

    /** 导出可编辑 PPTX（返回下载）。 */
    @GetMapping("/projects/{id}/export")
    public ResponseEntity<Resource> export(@PathVariable Long id) {
        try {
            String path = projectService.export(id, "data/exports");
            File f = new File(path);
            if (!f.exists()) return ResponseEntity.notFound().build();
            Resource res = new FileSystemResource(f);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + f.getName() + "\"")
                    .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.presentationml.presentation"))
                    .body(res);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    /** 导出信息（含文件路径）。 */
    @GetMapping("/projects/{id}/exports")
    public Dtos.ApiResponse exports(@PathVariable Long id) {
        ProjectRepository.Project p = projectService.getProject(id);
        if (p == null) return Dtos.ApiResponse.err("项目不存在");
        Map<String, Object> m = new HashMap<>();
        m.put("exports", projectService.listExports(id));
        return Dtos.ApiResponse.ok("导出记录", m);
    }
}
