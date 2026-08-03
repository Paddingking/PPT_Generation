<script setup lang="ts">
import { ref } from 'vue'
import { api } from '../api'

const emit = defineEmits<{ (e: 'created', project: any): void; (e: 'toast', msg: string, color?: string): void }>()

const intent = ref('帮我做一份「AI 数字化转型」的汇报 PPT，面向高管决策层，10 页内，重点是落地路线、投资回报和风险控制，别太花哨。')
const style = ref('corporate')
const pageCount = ref(10)
const density = ref('平衡')
const loading = ref(false)

const quickPrompts = [
  { label: '周会复盘', fill: '请把这段复盘做成 6 页周会 PPT，逻辑清晰的清单式表达。' },
  { label: '产品发布', fill: '做一份 12 页新产品发布会宣讲稿，煽动性强、故事化，科技感版式。' },
  { label: '数据月报', fill: '为我的团队周报生成 8 页月报，数据导向，可用表格呈现。' }
]

function fillQuick(p: { fill: string }) {
  intent.value = p.fill
  emit('toast', '已填入示例意图')
}

async function generate(skipStageA = false) {
  if (!intent.value.trim()) { emit('toast', '请输入演示意图', '#f5b041'); return }
  loading.value = true
  emit('toast', '双通道生成中（内容 + 样式）…', '#6ea8fe')
  try {
    const res: any = await api.generate({
      intent: intent.value,
      style: style.value,
      pageCount: pageCount.value,
      density: density.value,
      extraConstraint: '',
      skipStageA
    })
    if (res.success) {
      const d = res.data
      emit('created', {
        id: d.projectId,
        name: (res.data.content?.meta?.title || '未命名').slice(0, 30),
        slidesCount: (d.content?.slides?.length || 0),
        content: d.content,
        style: d.style
      })
      emit('toast', '内容 + 版式双稿已生成 ✓', '#58d68d')
    } else {
      emit('toast', res.message || '生成失败', '#e74c3c')
    }
  } catch (e: any) {
    console.error(e)
    emit('toast', '后端未启动或请求失败：' + (e?.message || e), '#e74c3c')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="entry-wrap">
    <div class="entry-hero">
      <h1>把一句话，做成一页页<br><em>可编辑的 PPT</em></h1>
      <p>输入原始想法 → 与 AI 约定风格与骨架 → 一键生成内容 + 版式双稿 → 预览微调 → 导出原生可编辑 PPTX。</p>
    </div>

    <div class="card input-card">
      <textarea class="textinput" v-model="intent" placeholder="描述你的演示意图…"></textarea>
      <div class="input-meta">
        <label class="dropzone" for="inputFile">
          <input id="inputFile" type="file" accept=".docx,.md,.txt" style="display:none" />
          上传文件
          <span class="muted">.docx / .md / .txt</span>
        </label>
        <span class="spacer" style="flex:1"></span>
        <button class="btn primary" @click="generate(false)" :disabled="loading">{{ loading ? '生成中…' : '开始制作' }}</button>
      </div>
      <div class="quick-prompts">
        <span class="muted" style="font-size:12px;align-self:center">快速开始：</span>
        <button class="qp" v-for="p in quickPrompts" :key="p.label" @click="fillQuick(p)">{{ p.label }}</button>
      </div>
    </div>

    <!-- 阶段A 约束向导 -->
    <div class="card wizard-card">
      <div class="wizard-head">
        <span class="pill orange">阶段 A · 生成前约束对话</span>
        <h3>先聊几句，把「制作要求」定下来</h3>
      </div>
      <div class="wizard-bubbles">
        <div class="bubble ai"><div class="who">PPT 助手</div>在落笔前，先定三点基调（都可跳过，直接按我的建议来）</div>
        <div class="bubble ai">
          <div class="who">PPT 助手</div>① 先定<b>视觉风格</b>，你想要？
          <div class="constraint-chips">
            <span class="chip" :class="{ sel: style === 'corporate' }" @click="style='corporate'"><span class="dot"></span>商务蓝 · 稳重</span>
            <span class="chip" :class="{ sel: style === 'sunset' }" @click="style='sunset'"><span class="dot"></span>暖橙 · 进取</span>
            <span class="chip" :class="{ sel: style === 'fresh' }" @click="style='fresh'"><span class="dot"></span>翠绿 · 生机</span>
            <span class="chip" :class="{ sel: style === 'mono' }" @click="style='mono'"><span class="dot"></span>黑白 · 极简</span>
          </div>
        </div>
        <div class="bubble ai">
          <div class="who">PPT 助手</div>② <b>页数 & 要点密度</b>？
          <div class="constraint-chips">
            <span class="chip" :class="{ sel: pageCount === 8 }" @click="pageCount=8"><span class="dot"></span>8 页 · 精炼</span>
            <span class="chip" :class="{ sel: pageCount === 10 }" @click="pageCount=10"><span class="dot"></span>10 页 · 平衡</span>
            <span class="chip" :class="{ sel: pageCount === 12 }" @click="pageCount=12"><span class="dot"></span>12 页 · 详尽</span>
          </div>
        </div>
        <div class="bubble ai"><div class="who">PPT 助手</div>③ 内容基调：侧重「落地路线」还是「投资回报」？可在下方输入框补充，或直接说「按建议来」。</div>
      </div>
      <div class="chat-inputbar">
        <input class="textinput stageA-input" v-model="density" placeholder="补充约束，例如：按落地方向讲，数据多点…" />
        <button class="btn signal" @click="generate(false)" :disabled="loading">按约束生成双稿</button>
      </div>
    </div>

    <div style="display:flex;gap:10px;justify-content:flex-end;margin-top:14px">
      <button class="btn ghost" @click="generate(true)" :disabled="loading">跳过约束 · 直接生成</button>
    </div>
  </div>
</template>

<style scoped>
.entry-wrap{max-width:920px;margin:0 auto;padding:34px 30px 60px}
.entry-hero{margin-bottom:26px}
.entry-hero h1{font-size:27px;font-weight:800;letter-spacing:-.5px;line-height:1.3}
.entry-hero h1 em{font-style:normal;background:linear-gradient(120deg,var(--signal),#ffc190);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}
.entry-hero p{color:var(--tx3);margin-top:8px;font-size:13.5px}
.input-card{padding:20px;margin-bottom:16px}
.textinput{
  width:100%;min-height:128px;resize:vertical;background:var(--bg1);color:var(--tx1);
  border:1px solid var(--line2);border-radius:var(--r-md);padding:14px 16px;font-size:14px;
  font-family:inherit;line-height:1.7;transition:.2s
}
.textinput:focus{outline:none;border-color:var(--brand);box-shadow:0 0 0 3px rgba(110,168,254,.15)}
.stageA-input{min-height:44px;flex:1}
.input-meta{display:flex;align-items:center;gap:10px;margin-top:12px;flex-wrap:wrap}
.dropzone{
  display:flex;align-items:center;gap:9px;flex:1;min-width:200px;padding:8px 12px;
  border:1px dashed var(--line2);border-radius:var(--r-md);cursor:pointer;transition:.2s;font-size:12.5px;color:var(--tx3)
}
.dropzone:hover{border-color:var(--brand);background:var(--bg1);color:var(--tx2)}
.quick-prompts{display:flex;gap:8px;flex-wrap:wrap;margin-top:14px}
.qp{font-size:12px;padding:6px 13px;border-radius:20px;cursor:pointer;transition:.18s;background:var(--bg3);border:1px solid var(--line2);color:var(--tx2);font-family:inherit}
.qp:hover{background:var(--brand-soft);border-color:var(--brand);color:var(--brand)}
.wizard-card{overflow:hidden}
.wizard-head{padding:18px 20px;border-bottom:1px solid var(--line1);display:flex;align-items:center;gap:12px}
.wizard-head h3{font-size:14.5px;font-weight:700}
.wizard-bubbles{display:flex;flex-direction:column;gap:12px;padding:20px}
.bubble{max-width:82%;padding:11px 15px;border-radius:12px;font-size:13px;line-height:1.7;position:relative}
.bubble.ai{background:var(--bg3);color:var(--tx1);border:1px solid var(--line2);border-top-left-radius:3px}
.bubble.user{align-self:flex-end;background:linear-gradient(135deg,#3f63c8,#3f7df0);color:#fff;border-top-right-radius:3px}
.bubble .who{font-size:10px;color:var(--tx3);margin-bottom:4px;letter-spacing:.5px;display:flex;align-items:center;gap:6px}
.constraint-chips{display:flex;flex-wrap:wrap;gap:8px;margin-top:10px}
.constraint-chips .chip{display:flex;align-items:center;gap:6px;font-size:12px;padding:6px 12px;border-radius:20px;cursor:pointer;background:var(--bg3);border:1px solid var(--line2);color:var(--tx2);transition:.18s}
.constraint-chips .chip:hover{border-color:var(--signal);color:var(--signal)}
.constraint-chips .chip.sel{background:var(--signal-soft);border-color:var(--signal);color:var(--signal)}
.constraint-chips .chip .dot{width:7px;height:7px;border-radius:50%;background:var(--tx3)}
.constraint-chips .chip.sel .dot{background:var(--signal)}
.chat-inputbar{display:flex;gap:10px;padding:14px 20px;border-top:1px solid var(--line1);background:var(--bg1);align-items:center}
</style>
