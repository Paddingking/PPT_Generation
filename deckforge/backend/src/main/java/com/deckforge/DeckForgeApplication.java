package com.deckforge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DeckForge · PPT 工作台 后端入口
 * 单机本地 AI PPT 生成工具（jdk8 / Spring Boot 2.7 / POI 5.2）
 */
@SpringBootApplication
public class DeckForgeApplication {
    public static void main(String[] args) {
        SpringApplication.run(DeckForgeApplication.class, args);
        System.out.println("=========================================");
        System.out.println("  DeckForge · PPT 工作台 后端已启动");
        System.out.println("  预览: http://localhost:8090/api/health");
        System.out.println("=========================================");
    }
}
