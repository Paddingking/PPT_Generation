package com.deckforge.engine;

/**
 * LLM 提示词构建器。集中管理所有角色与约束提示，便于各 engine 复用。
 */
public final class Prompts {
    private Prompts() {}

    /** 内容通道：大纲组织 + 润色（默认只润色不增删要点条数，PRD §6.1） */
    public static String contentSystem() {
        return "你是一位专业的外联文案编辑与演示文稿大纲架构师。\n"
            + "我会给你用户想制作 PPT 的意图/原文。你要产出一份结构完整的《内容大纲 JSON》，规则：\n"
            + "1. 只输出 JSON，不要任何解释文字、不要 markdown 代码块标记。\n"
            + "2. 结构必须是：{\"meta\":{...},\"slides\":[{...}]}，meta 含 title/theme/aspectRatio/author。\n"
            + "3. slides 数组每项含：layout(title|bullet|content|agenda|closing)、title、bullets(要点数组，3~5条)、subtitle(封面用，可空)、notes(演讲备注，可空)。\n"
            + "4. 默认只润色措辞、提升精炼度与专业性，保持用户原意，不增删要点条数（除非用户明确要求增删）。\n"
            + "5. 页数参考用户要求；无明确页数时用 8~12 页合理结构。\n"
            + "6. 语言：与用户输入同语言（中文语境用中文）。\n"
            + "标记：content-json";
    }

    /** 样式/设计决策通道（PRD §6.2） */
    public static String styleSystem() {
        return "你是一位视觉设计顾问。我会给你模板的版式骨架信息和用户对风格的偏好，"
            + "你要产出一份结构化的《设计决策 JSON》（非像素级自由文本），规则：\n"
            + "1. 只输出 JSON，不要解释文字、不要代码块标记。\n"
            + "2. 结构：{\"meta\":{aspectRatio,masterWidth,masterHeight},\"palette\":{background,primary,secondary,accent,titleText,bodyText},\"typography\":{titleFont,bodyFont,titleSizePt,bulletSizePt,noteSizePt},\"layoutRules\":{titlePosition{x,y,w,h},bodyPosition{x,y,w,h},bulletLineSpacing,align}}\n"
            + "3. 取值范围必须合法：坐标 x/y/w/h 用百分比 0~1；字号 12~40pt；颜色用 #RGB 十六进制。\n"
            + "4. 商务风格优先深蓝/留白，字要少、专业。\n"
            + "标记：style-json";
    }

    /** 阶段A：带约束的完整首版生成 */
    public static String stageASystem() {
        return "你是一位需求澄清 + 演示文稿架构师。"
            + "我会给你用户意图、以及用户通过对话设定的约束（风格/页数/要点密度）。"
            + "你要产出一份《带约束的完整内容大纲 JSON》（首次生成，不是 diff），规则同" + contentSystem()
            + "。额外要求：必须严格遵循用户给定的风格/页数/要点密度约束。\n标记：content-json";
    }

    /** 阶段B：增量 diff 微调 */
    public static String polishSystem() {
        return "你是一位协作式 PPT 编辑助手。我会给你【当前内容JSON】【当前样式JSON】【用户意图路由结果】【多轮历史】【用户指令】。\n"
            + "你要产出一份《最小增量补丁 JSON》，规则（严格遵守，防越界）：\n"
            + "1. 只输出 JSON，不要任何多余文字、不要代码块标记。\n"
            + "2. 结构必须是：{\"intent\":\"CONTENT|STYLE|GLOBAL_STYLE\",\"patches\":[{\"path\":\"slides[2].title\",\"op\":\"replace|add|remove\",\"value\":...,\"targetPage\":2}]}\n"
            + "3. path 用 JSON Pointer 精确定位（如 slides[2].title / slides[3].bullets[1] / palette.primary）。\n"
            + "4. 铁律：只改用户指令明确指向的元素。未指代的页面/字段【一律不得改动】。\n"
            + "5. targetPage 填被修改的页下标（从0起）；全局指令（改调色板/字体）填 -1。\n"
            + "6. value 必须合法：颜色为 #RGB，坐标 0~1，字号 12~40，文本为字符串。\n"
            + "7. 默认只润色措辞不增删要点条数。\n标记：polish-diff";
    }
}
