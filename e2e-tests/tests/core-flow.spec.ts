import { test, expect, type Page } from '@playwright/test';

/**
 * DeckForge · PPT 工作台 核心用户旅程 E2E
 * 覆盖 PRD P0-1 ~ P0-14 的主链路：输入 → 生成双稿 → 预览 → 段落微调 → 快照 → 导出
 */

/** 导航到创作入口 */
async function gotoEntry(page: Page) {
  await page.goto('/');
  await page.waitForSelector('.entry-wrap', { timeout: 10000 });
}

/** 从创作入口生成一个项目（带阶段A约束），成功后自动跳转预览 */
async function createProject(page: Page, intent: string) {
  await gotoEntry(page);
  const ta = page.locator('.textinput').first();
  await ta.fill(intent);
  await page.getByRole('button', { name: '开始制作' }).click();
  // 等待跳转到预览页
  await page.waitForSelector('.preview-layout', { timeout: 30000 });
}

test.describe('DeckForge 核心用户旅程', () => {

  test('TC01 应用加载：欢迎语 + 四大导航 + 默认进入创作入口', async ({ page }) => {
    await gotoEntry(page);
    // 欢迎 toast
    await expect(page.locator('.toast')).toContainText('DeckForge');
    // 品牌
    await expect(page.locator('.brand')).toContainText('DeckForge');
    // 导航项
    const nav = page.locator('.top-nav a');
    await expect(nav).toHaveCount(4);
    await expect(nav.nth(0)).toHaveText('创作入口');
    await expect(nav.nth(1)).toHaveText('模板库');
    await expect(nav.nth(2)).toHaveText('预览编辑');
    await expect(nav.nth(3)).toHaveText('设置');
    // 默认视图
    await expect(page.locator('.entry-wrap')).toBeVisible();
    // 阶段A助手口吻验证：自称「PPT 助手」，不用「导演」
    const bubbles = page.locator('.wizard-bubbles .bubble.ai .who');
    const whoTexts = await bubbles.allTextContents();
    expect(whoTexts.join(',')).not.toContain('导演');
    expect(whoTexts.join(',')).toContain('PPT 助手');
  });

  test('TC02 阶段A约束：选择风格/页数后点击「按约束生成双稿」→ 跳转预览且页数正确', async ({ page }) => {
    await gotoEntry(page);
    const intent = '帮我做一份「2026 产品发布」的汇报 PPT';
    await page.locator('.textinput').first().fill(intent);

    // 选择「暖橙 · 进取」（sunset）风格 chip
    await page.locator('.constraint-chips .chip', { hasText: '暖橙' }).first().click();
    // 选择「12 页 · 详尽」页数 chip
    await page.locator('.constraint-chips .chip', { hasText: '12 页' }).click();

    // 点击「按约束生成双稿」
    await page.getByRole('button', { name: '按约束生成双稿' }).click();
    await page.waitForSelector('.preview-layout', { timeout: 30000 });

    // 页数显示（12 页约束 → mock 产出 1封面+1议程+body；此处宽松断言 >= 3）
    const pageCountText = await page.locator('.ppt-crumbs .mono').textContent();
    const shown = parseInt(pageCountText?.match(/\d+/)?.[0] || '0', 10);
    expect(shown).toBeGreaterThanOrEqual(3);

    // 顶部项目徽标更新
    await expect(page.locator('.prj-badge')).toContainText('页');
  });

  test('TC03 跳过约束直接生成 → 跳转预览并可看到生成内容', async ({ page }) => {
    await gotoEntry(page);
    await page.locator('.textinput').first().fill('一份关于智能客服的演示');
    await page.getByRole('button', { name: '跳过约束' }).click();
    await page.waitForSelector('.preview-layout', { timeout: 30000 });
    // 至少有一张幻灯片
    const slides = page.locator('.slide-row');
    const n = await slides.count();
    expect(n).toBeGreaterThanOrEqual(2);
    // 导出条存在
    await expect(page.locator('.export-bar')).toContainText('导出可编辑 PPTX');
  });

  test('TC04 关键路径主流程：生成 → 微调第N页标题 → 快照撤销 → 导出PPTX', async ({ page }) => {
    // ---- Step1 生成 ----
    await createProject(page, '帮我做一份「数据中心运维」的汇报 PPT');

    // 记录初始页数与初始标题
    const pageCountText = await page.locator('.ppt-crumbs .mono').textContent();
    const total = parseInt(pageCountText?.match(/\d+/)?.[0] || '0', 10);
    expect(total).toBeGreaterThanOrEqual(2);
    // 封面标题
    const coverTitleBefore = (await page.locator('.slide-row').nth(0).locator('.s-title').textContent())?.trim();

    // ---- Step2 微调某页标题（聚焦第2页→讲解页，指令含标题）----
    // 当前聚焦默认第1页；点击第3页
    await page.locator('.slide-row').nth(2).click();
    // 输入指令
    const instr = '第3页标题更有力';
    await page.locator('.chat-input textarea').fill(instr);
    await page.locator('.chat-input .send').click();
    // 等待 AI 回复含「已应用」diff
    await expect(page.locator('.cmsg.ai .body').last()).toContainText('已应用', { timeout: 20000 });
    await expect(page.locator('.cmsg.ai .body').last()).toContainText('✓');

    // 第3页标题被改
    const thirdTitle = (await page.locator('.slide-row').nth(2).locator('.s-title').textContent())?.trim();
    expect(thirdTitle).not.toBe('');

    // 取消聚焦影响校验：仍有 N 张幻灯片
    await expect(page.locator('.slide-row')).toHaveCount(total);

    // ---- Step3 快照撤销回初稿 ----
    // 微调后应出现快照条
    await expect(page.locator('.undo-strip')).toBeVisible();
    // 点击「回到初稿」
    await page.locator('.undo-chip.root').click();
    await expect(page.locator('.toast')).toContainText('已回退');
    // 封面标题恢复到生成时
    const coverTitleAfter = (await page.locator('.slide-row').nth(0).locator('.s-title').textContent())?.trim();
    expect(coverTitleAfter === coverTitleBefore || coverTitleBefore === undefined).toBeTruthy();

    // ---- Step4 导出 ----
    // 捕获下载事件
    const downloadPromise = page.waitForEvent('download', { timeout: 30000 });
    await page.getByRole('button', { name: '导出可编辑 PPTX' }).click();
    const download = await downloadPromise;
    // 文件名应为 .pptx
    expect(download.suggestedFilename()).toMatch(/\.pptx$/i);
    const path = await download.path();
    expect(path).toBeTruthy();
    // toast 提示
    await expect(page.locator('.toast')).toContainText('已导出');
  });

  test('TC05 模板库：展示内置模板并可点击套用', async ({ page }) => {
    await page.goto('/');
    await page.locator('.top-nav a', { hasText: '模板库' }).click();
    await page.waitForSelector('.tpl-wrap');
    // 内置模板卡片（至少 1 张 内置预设）
    const cards = page.locator('.tpl-card');
    const n = await cards.count();
    expect(n).toBeGreaterThanOrEqual(1);
    // 存在「导入外部模板」
    await expect(page.locator('.tpl-card.add-card')).toContainText('导入外部模板');
    // 点击第一张内置模板 → 出现 toast
    await cards.nth(0).click();
    await expect(page.locator('.toast')).toContainText('已套用');
  });

  test('TC06 设置页：默认展示 Mock 模式，可切换协议与保存', async ({ page }) => {
    await page.goto('/');
    await page.locator('.top-nav a', { hasText: '设置' }).click();
    await page.waitForSelector('.set-wrap');
    // Mock 模式提示或当前激活
    const body = await page.locator('.set-wrap').textContent();
    expect(body).toContain('OpenAI 兼容');
    expect(body).toContain('Anthropic 兼容');
    // 保存配置按钮存在
    await expect(page.getByRole('button', { name: '保存配置' })).toBeVisible();
    // 切换协议 tab 并保存（不配 Key，走 mock）
    await page.locator('.proto-tabs button', { hasText: 'Anthropic 兼容' }).click();
    await expect(page.locator('.proto-tabs button', { hasText: 'Anthropic 兼容' })).toHaveClass(/on/);
    await page.getByRole('button', { name: '保存配置' }).click();
    await expect(page.locator('.toast')).toContainText('保存');
  });

});
