import { test, expect, type Page } from '@playwright/test';

/**
 * DeckForge · PPT 工作台 边界与异常场景 E2E
 * 验证：空意图校验、非法指令回退、快照链、一致性开关、损坏模板导入回退
 */

async function gotoEntry(page: Page) {
  await page.goto('/');
  await page.waitForSelector('.entry-wrap', { timeout: 10000 });
}

async function createProject(page: Page, intent: string) {
  await gotoEntry(page);
  await page.locator('.textinput').first().fill(intent);
  await page.getByRole('button', { name: '开始制作' }).click();
  await page.waitForSelector('.preview-layout', { timeout: 30000 });
}

test.describe('DeckForge 边界与异常场景', () => {

  test('EC01 空意图校验：不发请求直接提示「请输入演示意图」', async ({ page }) => {
    await gotoEntry(page);
    // 清空文本框
    await page.locator('.textinput').first().fill('');
    // 拦截网络请求，确认没有发起生成
    let genCalled = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/projects/generate')) genCalled = true;
    });
    await page.getByRole('button', { name: '开始制作' }).click();
    await expect(page.locator('.toast'), { timeout: 5000 }).toContainText('请输入演示意图');
    expect(genCalled).toBe(false); // 未发请求
  });

  test('EC02 空白输入（仅空格）亦被拦截', async ({ page }) => {
    await gotoEntry(page);
    await page.locator('.textinput').first().fill('    ');
    let genCalled = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/projects/generate')) genCalled = true;
    });
    await page.getByRole('button', { name: '开始制作' }).click();
    await expect(page.locator('.toast')).toContainText('请输入演示意图');
    expect(genCalled).toBe(false);
  });

  test('EC03 微调空指令：点击发送不发请求', async ({ page }) => {
    await createProject(page, '测试空指令微调');
    // 聚焦到第1页
    await page.locator('.slide-row').nth(0).click();
    let polishCalled = false;
    page.on('request', (req) => {
      if (req.url().includes('/api/chat/polish')) polishCalled = true;
    });
    await page.locator('.chat-input textarea').fill('   ');
    await page.locator('.chat-input .send').click();
    // 简短等待，确认没有调用 polish
    await page.waitForTimeout(800);
    expect(polishCalled).toBe(false);
  });

  test('EC04 全局样式微调：「全文主色加深」应应用到样式且快照可回退', async ({ page }) => {
    await createProject(page, '验证全局样式');
    // 记录原始主色（顶栏风格按钮显示主题色）
    const styleTextBefore = await page.locator('.stage-top .btn.ghost').textContent();
    const before = (styleTextBefore || '').trim();

    // 输入全局样式指令
    await page.locator('.chat-input textarea').fill('全文主色加深');
    await page.locator('.chat-input .send').click();
    await expect(page.locator('.cmsg.ai .body').last()).toContainText('已应用', { timeout: 20000 });

    // 样式主色应改变（按钮上显示新主题色）
    const styleTextAfter = (await page.locator('.stage-top .btn.ghost').textContent())?.trim();
    expect(styleTextAfter).not.toBe(before);

    // 快照条出现
    await expect(page.locator('.undo-strip')).toBeVisible();
    // 撤销回初稿后主色恢复
    await page.locator('.undo-chip.root').click();
    await expect(page.locator('.toast')).toContainText('已回退');
    const afterUndo = (await page.locator('.stage-top .btn.ghost').textContent())?.trim();
    expect(afterUndo).toBe(before);
  });

  test('EC05 局部样式与同步全局一致性开关切换', async ({ page }) => {
    await createProject(page, '验证一致性开关');
    // 开关默认关
    await expect(page.locator('.chat-head .switch')).not.toHaveClass(/on/);
    // 点击开启
    await page.locator('.chat-head .switch').click();
    await expect(page.locator('.chat-head .switch')).toHaveClass(/on/);
    // 指令微调后无崩溃
    await page.locator('.chat-input textarea').fill('第2页标题改动一下');
    await page.locator('.chat-input .send').click();
    await expect(page.locator('.cmsg.ai .body').last()).toContainText('已应用', { timeout: 20000 });
  });

  test('EC06 前端空态兜底：预览页无项目时显示「这里还没有任何幻灯片」', async ({ page }) => {
    // 直接进预览（无项目）
    await page.goto('/');
    await page.locator('.top-nav a', { hasText: '预览编辑' }).click();
    await page.waitForSelector('.preview-layout');
    await expect(page.locator('.empty-t')).toContainText('这里还没有任何幻灯片');
  });

  test('EC07 损坏/纯背景模板上传应回退内置且不崩溃', async ({ page }) => {
    await page.goto('/');
    await page.locator('.top-nav a', { hasText: '模板库' }).click();
    await page.waitForSelector('.tpl-wrap');

    // 构造一个无法识别骨架的 "假 PPTX"（纯背景图场景，用非 pptx 扩展名的 zip）
    // Playwright 直接用 setInputFiles 喂一个不含占位符的 pptx 包
    const { tmpdir } = require('os') as any;
    const path = require('path') as any;
    const fs = require('fs') as any;
    // 用一个无效内容文件模拟损坏模板（后端应返回 err 而非崩溃）
    const badFile = path.join(tmpdir(), 'bad-template.pptx');
    fs.writeFileSync(badFile, 'not a valid pptx content');

    await page.setInputFiles('#tplFile', badFile);
    // 最终 toast 应为「已回退内置预设」或「导入失败/异常」（识别失败回退内置，不崩溃）。
    // 因解析中会先出现「正在解析…」瞬时 toast，故用轮询等到最终结果。
    await expect.poll(async () => {
      const txt = (await page.locator('.toast').textContent()) || '';
      return /回退内置|导入失败|导入异常|未识别/.test(txt);
    }, { timeout: 25000, message: '应出现回退内置或导入失败提示' }).toBe(true);
    // 页面主体仍在
    await expect(page.locator('.tpl-wrap')).toBeVisible();
    // 清理
    fs.unlinkSync(badFile);
  });

  test('EC08 会话历史接口可用：项目生成后可取历史', async ({ page }) => {
    await createProject(page, '验证历史接口');
    // 通过浏览器上下文调接口（走代理）
    const resp = await page.request.get('/api/projects');
    expect(resp.ok()).toBeTruthy();
    const data = await resp.json();
    expect(data.success).toBe(true);
    expect(Array.isArray(data.data?.projects)).toBe(true);
  });

});
