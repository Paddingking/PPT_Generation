import { defineConfig, devices } from '@playwright/test';

/**
 * DeckForge · PPT 工作台 — E2E 测试配置
 * 目标：http://localhost:5173（前端 Vite）
 * 后端：http://localhost:8090（Spring Boot，/api 已被前端代理）
 */
export default defineConfig({
  testDir: './tests',
  timeout: 60000,
  fullyParallel: false,          // 有共享后端状态，串行更稳
  workers: 1,
  retries: 1,                    // 网络/渲染抖动容忍 1 次重试
  outputDir: 'C:/Users/11075/AppData/Local/Temp/deckforge-pw-artifacts', // 避开 WorkBuddy safe-delete 保护
  reporter: [
    ['list'],
    ['html', { outputFolder: '.pw-report', open: 'never' }],
  ],
  use: {
    baseURL: 'http://localhost:5173',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    headless: true,
    locale: 'zh-CN',             // 模拟中文环境
    viewport: { width: 1440, height: 900 },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
