<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api'

const emit = defineEmits<{ (e: 'toast', msg: string, color?: string): void }>()

const templates = ref<any[]>([])
const selected = ref<string>('')
const importResult = ref<any>(null)
const fallbackShow = ref(false)
const importing = ref(false)

// 内置主题缩略图背景
const tplStyle: Record<string, string> = {
  'corporate': 'linear-gradient(135deg,#185fa5,#0c2b4d)',
  'sunset': 'linear-gradient(135deg,#ff9d66,#b8502e)',
  'fresh': 'linear-gradient(135deg,#2e8b57,#1c5340)',
  'mono': 'linear-gradient(135deg,#3a3f4d,#171c28)'
}

// 内置预设的颜色
const presetPalette: Record<string, any> = {
  corporate: { primary: '#185FA5', secondary: '#378ADD', accent: '#E6F1FB', titleText: '#0C2B4D', bodyText: '#20344D' },
  sunset: { primary: '#B8502E', secondary: '#FF9D66', accent: '#FFE4D1', titleText: '#7A2E14', bodyText: '#4A342A' },
  fresh: { primary: '#2E8B57', secondary: '#6BBF8A', accent: '#E3F3E8', titleText: '#1C5340', bodyText: '#27402F' },
  mono: { primary: '#1A1A1A', secondary: '#555555', accent: '#F2F2F2', titleText: '#000000', bodyText: '#333333' }
}

async function load() {
  const res: any = await api.listTemplates()
  if (res.success) {
    templates.value = res.data.templates || []
    if (res.data.templates?.length) selected.value = String(res.data.templates[0].id)
  }
}

function select(t: any) {
  if (t.sourceType === 'IMPORTED' && !t.recognized) {
    emit('toast', '该模板未识别到版式骨架，回退内置', '#f5b041')
    fallbackShow.value = true
    return
  }
  selected.value = String(t.id)
  fallbackShow.value = false
  emit('toast', '已套用骨架：' + t.name, '#ff9d66')
}

async function onFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file) return
  importing.value = true
  emit('toast', '正在解析该 .pptx 的版式骨架…', '#6ea8fe')
  try {
    const res: any = await api.importTemplate(file)
    if (res.success) {
      importResult.value = res.data
      if (res.data.recognized) {
        emit('toast', '版式骨架识别成功：' + res.data.template.name, '#58d68d')
        await load()
        selected.value = String(res.data.template.id)
        fallbackShow.value = false
      } else {
        emit('toast', '未识别到可用版式骨架 → 已回退内置预设', '#f5b041')
        fallbackShow.value = true
      }
    } else {
      emit('toast', res.message || '导入失败', '#e74c3c')
    }
  } catch (err: any) {
    emit('toast', '导入异常：' + (err?.message || err), '#e74c3c')
  } finally {
    importing.value = false
    input.value = ''
  }
}

onMounted(load)
</script>

<template>
  <div class="tpl-wrap">
    <div class="section-head" style="padding-left:0">
      <div><h2>选择幻灯片骨架</h2><p>内置预设可直接套用，或导入你自己的 .pptx 提取版式骨架 — 内容与你组织的规范对齐。</p></div>
    </div>

    <div class="tpl-grid">
      <div v-for="t in templates" :key="t.id" class="tpl-card"
           :class="{ sel: selected === String(t.id), mono: t.name.includes('极简') }"
           @click="select(t)">
        <div class="tpl-thumb" :style="{ background: tplStyle[themeKey(t.name)] || t.recognized ? 'linear-gradient(135deg,#175a3a,#0e3d28)' : '' }">
          <div class="thumb-slide">
            <div class="thumb-bar" :style="{ background: (presetPalette[themeKey(t.name)] || {}).primary || '#185fa5' }"></div>
            <div class="thumb-line"></div><div class="thumb-line"></div><div class="thumb-line"></div>
          </div>
        </div>
        <div class="tpl-info">
          <b>{{ t.name }}</b>
          <div class="row">
            <span class="pill blue">{{ t.sourceType === 'BUILTIN' ? '内置预设' : '已导入' }}</span>
            <span class="pill dim">16 : 9</span>
          </div>
          <div v-if="t.sourceType === 'IMPORTED' && t.recognized" class="row" style="margin-top:5px;font-size:11px;color:var(--ok)">✓ 版式骨架已识别</div>
          <div v-else-if="t.sourceType === 'IMPORTED' && !t.recognized" class="row" style="margin-top:5px;font-size:11px;color:var(--warn)">⚠ 未识别 · 回退内置</div>
        </div>
      </div>

      <!-- 导入卡片 -->
      <div class="tpl-card add-card">
        <label for="tplFile" class="tpl-thumb" style="display:grid;place-items:center;cursor:pointer;background:var(--bg1);min-height:100px">
          <input id="tplFile" type="file" accept=".pptx" style="display:none" @change="onFileChange" />
          <div style="text-align:center;color:var(--tx3)"><div style="font-size:26px;margin-bottom:4px;color:var(--brand)">＋</div>导入 .pptx<br><span style="font-size:11px">{{ importing ? '解析中…' : '提取版式骨架' }}</span></div>
        </label>
        <div class="tpl-info"><b>导入外部模板</b><div class="row"><span class="pill dim">提取骨架</span><span class="pill dim">识别失败回退</span></div></div>
      </div>
    </div>

    <!-- 识别失败回退横幅 -->
    <div v-if="fallbackShow" class="fallback-banner">
      <div style="flex:1">
        <b style="font-size:13.5px;color:var(--warn)">未识别到可用的版式骨架</b>
        <div style="font-size:12px;color:var(--tx3);margin-top:4px;line-height:1.6">该模板占位符 / 主题不规范（recognized=false）。已自动回退到内置预设，你的内容与新演示不受影响 — 不会报错崩溃。</div>
      </div>
      <button class="btn ghost sm" @click="fallbackShow=false">知道了</button>
    </div>
  </div>
</template>

<script lang="ts">
function themeKey(name: string): string {
  if (name.includes('橙色') || name.includes('暖橙')) return 'sunset'
  if (name.includes('翠绿')) return 'fresh'
  if (name.includes('极简') || name.includes('黑白')) return 'mono'
  return 'corporate'
}
export default { }
</script>

<style scoped>
.tpl-wrap{max-width:1000px;margin:0 auto;padding:30px}
.tpl-grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(200px,1fr));gap:14px;margin-top:16px}
.tpl-card{border:1px solid var(--line1);border-radius:var(--r-md);overflow:hidden;cursor:pointer;background:var(--bg2);transition:.2s;position:relative}
.tpl-card:hover{transform:translateY(-3px);box-shadow:var(--shadow);border-color:var(--line2)}
.tpl-card.sel{border-color:var(--signal);box-shadow:0 0 0 2px rgba(255,157,102,.35)}
.tpl-thumb{aspect-ratio:16/9;display:grid;place-items:center;padding:20px}
.thumb-slide{width:58%;aspect-ratio:16/9;background:rgba(255,255,255,.95);border-radius:4px;box-shadow:0 6px 18px rgba(0,0,0,.4);display:flex;flex-direction:column;padding:9%;gap:6%}
.thumb-bar{height:6%;border-radius:2px;background:#185fa5;opacity:.9}
.thumb-line{height:3.5%;border-radius:2px;background:rgba(32,52,77,.35)}
.thumb-line:nth-child(2){width:78%}.thumb-line:nth-child(3){width:52%}.thumb-line:nth-child(4){width:64%}
.tpl-info{padding:12px 14px;border-top:1px solid var(--line1)}
.tpl-info b{font-size:13px;font-weight:600}
.tpl-info .row{display:flex;justify-content:space-between;align-items:center;margin-top:3px}
.add-card{cursor:pointer}
.fallback-banner{display:flex;align-items:center;gap:16px;margin:18px auto 0;max-width:100%;padding:14px 16px;border:1px solid rgba(245,176,65,.5);border-radius:var(--r-md);background:rgba(245,176,65,.07)}
</style>
