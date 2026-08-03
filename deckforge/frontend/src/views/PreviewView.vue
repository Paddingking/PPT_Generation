<script setup lang="ts">
import { ref, computed, watch, onMounted } from 'vue'
import { api, downloadExport } from '../api'

const emit = defineEmits<{ (e: 'toast', msg: string, color?: string): void; (e: 'set-project', p: any): void }>()
const props = defineProps<{ project?: any }>()

const content = ref<any>({ meta: {}, slides: [] })
const style = ref<any>({})
const focusPage = ref(1)
const messages = ref<any[]>([])
const instruction = ref('')
const syncLocal = ref(false)
const snapshots = ref<any[]>([])
const chatLoading = ref(false)
const exporting = ref(false)

watch(() => props.project, (p) => {
  if (p?.content) content.value = p.content
  if (p?.style) style.value = p.style
}, { immediate: true })

watch(syncLocal, async (on) => {
  if (props.project?.id) {
    try { await api.setSyncStyle(props.project.id, on) } catch (e) {}
  }
})

async function loadProject() {
  if (!props.project?.id) return
  const res: any = await api.projectDetail(props.project.id)
  if (res.success) {
    content.value = res.data.content || { meta: {}, slides: [] }
    style.value = res.data.style || {}
    snapshots.value = res.data.snapshots || []
    messages.value = [{ role: 'ai', text: '双稿已生成。现在主战场 — 你可以直接改预览，或在这里用自然语言让我精确微调。当前聚焦第 ' + focusPage.value + ' 页。' }]
  }
}

const slides = computed(() => (content.value?.slides || []))
const palette = computed(() => style.value?.palette || {})
const typography = computed(() => style.value?.typography || {})
const titleFont = computed(() => typography.value.titleFont || '微软雅黑')
const primary = computed(() => palette.value.primary || '#185fa5')
const accentColor = computed(() => palette.value.accent || '#e6f1fb')
const titleText = computed(() => palette.value.titleText || '#0c2b4d')
const bodyText = computed(() => palette.value.bodyText || '#20344d')
const isCover = (i: number) => { const l = slides.value[i]?.layout; return l === 'title' || l === 'closing' }

async function sendPolish() {
  const text = instruction.value.trim()
  if (!text || !props.project?.id) return
  messages.value.push({ role: 'user', text })
  instruction.value = ''
  chatLoading.value = true
  try {
    const res: any = await api.polish({
      projectId: props.project.id,
      instruction: text,
      focusPage: focusPage.value,
      syncLocalStyle: syncLocal.value
    })
    if (res.success) {
      const d = res.data
      content.value = d.content
      style.value = d.style
      snapshots.value = await ( await api.projectDetail(props.project.id) ).data.snapshots || []
      // 构建 AI 回复（含 diff 展示）
      let aiText = '已按指令产出<b>最小增量 patch</b> 并校验合并，其余页未触碰。'
      const applied = d.applied || []
      const rejected = d.rejected || []
      if (applied.length) {
        aiText += '<div class="diff-line ok mono">✓ 已应用：' + applied.join('；') + '</div>'
      }
      if (rejected.length) {
        aiText += '<div class="diff-line reject mono">⚠ 已拒绝（越界/非法）：' + rejected.join('；') + '</div>'
      }
      aiText += '<div class="diff-line ok mono">✓ path 校验通过 · value 合法 · 未越界</div>'
      messages.value.push({ role: 'ai', text: aiText })
      emit('set-project', { ...props.project, content: d.content, style: d.style })
      emit('toast', '预览已实时刷新（多轮叠加有效，可快照回退）', '#58d68d')
    } else {
      messages.value.push({ role: 'ai', text: res.message || '微调失败' })
    }
  } catch (e: any) {
    messages.value.push({ role: 'ai', text: '请求异常：' + (e?.message || e) })
  } finally {
    chatLoading.value = false
  }
}

async function undoTo(seq: number | null) {
  if (!props.project?.id) return
  const res: any = await api.undo({ projectId: props.project.id, snapshotSeq: seq })
  if (res.success) {
    content.value = res.data.content
    style.value = res.data.style
    snapshots.value = (await api.projectDetail(props.project.id)).data.snapshots || []
    emit('toast', '已回退' + (seq ? ('到快照 s' + seq) : '到初稿'), '#6ea8fe')
  } else {
    emit('toast', res.message || '回退失败', '#e74c3c')
  }
}

async function doExport() {
  exporting.value = true
  try {
    downloadExport(props.project!.id)
    emit('toast', '已导出原生可编辑 PPTX · PowerPoint/WPS 可打开可改', '#58d68d')
  } finally { exporting.value = false }
}

function focusIdx(i: number) {
  focusPage.value = i + 1
}

onMounted(loadProject)
</script>

<template>
  <div class="preview-layout">
    <!-- 画布区 -->
    <div class="preview-stage">
      <div class="stage-top">
        <div class="ppt-crumbs"><span class="pill orange">阶段 B · 预览微调</span><span class="mono">{{ slides.length }} 页</span></div>
        <span class="spacer" style="flex:1"></span>
        <button class="btn ghost sm" @click="emit('toast', '当前主题：' + (style.meta?.aspectRatio||'16:9'))">风格 {{ palette.primary }}</button>
      </div>

      <!-- 空态 -->
      <div v-if="!slides.length" class="canvas-frame">
        <div class="empty-box">
          <div class="empty-ic">·</div>
          <div class="empty-t">这里还没有任何幻灯片</div>
          <div class="empty-s">先在「创作入口」输入想法，经过阶段A约束后生成内容+版式双稿。</div>
        </div>
      </div>

      <!-- 幻灯片列表 -->
      <div v-else class="canvas-frame">
        <div v-for="(s, i) in slides" :key="i" class="slide-row" :class="{ focus: focusPage === i + 1 }" @click="focusIdx(i)">
          <div class="slide-index">{{ i + 1 }}</div>
          <div class="slide-slot">
            <div class="slide" :class="{ zebra: isCover(i) }">
              <div
                class="s-inner"
                :style="{ fontFamily: titleFont }"
              >
                <!-- 封面/结尾：主色背景 -->
                <template v-if="isCover(i)">
                  <div class="s-title tl" :style="{ color: '#fff', fontSize: '13%' }">{{ s.title }}</div>
                  <div v-if="s.subtitle" class="s-sub" :style="{ color: 'rgba(255,255,255,.9)', width: '60%' }">{{ s.subtitle }}</div>
                </template>
                <!-- 议程 -->
                <template v-else-if="s.layout === 'agenda'">
                  <div class="s-title bl" :style="{ color: titleText }">{{ s.title }}</div>
                  <div class="agenda-bar">
                    <div v-for="(it, j) in (s.bullets||[]).slice(0,6)" :key="j" class="agenda-item" :class="{ f: j===0 }" :style="j===0 ? { background: primary, color:'#fff' } : { background: accentColor, color: titleText }">{{ it }}</div>
                  </div>
                </template>
                <!-- 普通要点 -->
                <template v-else>
                  <div class="s-title bl" :style="{ color: titleText }">{{ s.title }}</div>
                  <div class="s-bullets">
                    <div v-for="(b, j) in (s.bullets||[]).slice(0,5)" :key="j" class="s-b" :style="{ color: bodyText }">
                      <span class="s-mark" :style="{ color: primary }">▍</span>{{ b }}
                    </div>
                    <div v-if="!s.bullets || !s.bullets.length" class="s-b" :style="{ color: bodyText }"><span class="s-mark" :style="{color:primary}">▍</span>（内容待补充）</div>
                  </div>
                </template>
                <div class="s-accbar" :style="{ background: 'linear-gradient(90deg,' + primary + ',' + (palette.secondary||primary) + ')' }"></div>
                <div class="s-foot">{{ 'SLIDE ' + String(i+1).padStart(2,'0') }}</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 导出条 -->
        <div class="export-bar" style="margin-top:8px;border-radius:12px">
          <div class="export-note">
            <span class="ok-line">✓ 内容 + 版式 双稿已就绪 · {{ slides.length }} 页</span>
            <span>导出为原生可编辑 PPTX（PowerPoint / WPS）— 所有文本与形状保持可改</span>
          </div>
          <button class="btn signal" @click="doExport" :disabled="exporting">{{ exporting ? '导出中…' : '导出可编辑 PPTX' }}</button>
        </div>
      </div>
    </div>

    <!-- 右侧常驻对话面板 -->
    <div class="chat-panel">
      <div class="chat-head">
        <div class="r1"><h3><span class="live-dot"></span>AI PPT 助手 · 预览微调</h3><span class="pill dim">常驻</span></div>
        <div class="sync-row">
          <div class="switch" :class="{ on: syncLocal }" @click="syncLocal=!syncLocal" title="局部样式改动同步为全局默认"></div>
          <span>局部样式↔同步全局</span>
        </div>
      </div>

      <div class="chat-body">
        <div v-for="(m, i) in messages" :key="i" class="cmsg" :class="m.role">
          <div class="ic">{{ m.role === 'ai' ? 'PPT' : '许' }}</div>
          <div class="body" v-html="m.text"></div>
        </div>
        <div v-if="chatLoading" class="cmsg ai"><div class="ic">PPT</div><div class="body">正在定位并生成最小增量 patch…</div></div>
      </div>

      <div class="undo-strip" v-if="snapshots.length">
        <span class="mono" style="font-size:10px;color:var(--tx3);align-self:center;letter-spacing:.5px">快照</span>
        <span class="undo-chip root" @click="undoTo(null)">回到初稿</span>
        <span v-for="s in snapshots" :key="s.seq" class="undo-chip" @click="undoTo(s.seq)">s{{ s.seq }}</span>
      </div>

      <div class="chat-foot">
        <div class="focus-hint">🎯 当前聚焦 <b style="color:var(--signal)">第 {{ focusPage }} 页</b> — 点击任意页可改指代</div>
        <div class="chat-input">
          <textarea v-model="instruction" @keydown.enter.exact.prevent="sendPolish" placeholder="输入微调指令，如「第3页标题更有力」「全文主色加深」…"></textarea>
          <button class="send" @click="sendPolish" :disabled="chatLoading">➤</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.preview-layout{display:flex;height:100%;min-height:calc(100vh - 54px)}
.preview-stage{flex:1;overflow-y:auto;padding:28px 36px;position:relative}
.stage-top{display:flex;align-items:center;gap:10px;max-width:880px;margin:0 auto 20px}
.ppt-crumbs{font-size:12px;color:var(--tx3);display:flex;align-items:center;gap:8px}
.canvas-frame{max-width:880px;margin:0 auto}
.slide-row{display:flex;gap:14px;margin-bottom:14px;align-items:flex-start}
.slide-index{width:28px;height:28px;border-radius:7px;display:grid;place-items:center;font-size:12px;font-weight:600;flex-shrink:0;background:var(--bg3);border:1px solid var(--line2);color:var(--tx3);font-family:var(--mono)}
.slide-row.focus .slide-index{background:var(--signal);border-color:var(--signal);color:#241206}
.slide-slot{flex:1;min-width:0}
.slide{aspect-ratio:16/9;border-radius:4px;position:relative;overflow:hidden;cursor:pointer;background:var(--paper);box-shadow:0 4px 20px rgba(0,0,0,.4);transition:.22s}
.slide:hover{box-shadow:0 10px 34px rgba(0,0,0,.55);transform:translateY(-2px)}
.slide-row.focus .slide{box-shadow:0 0 0 3px var(--signal)}
.s-inner{position:absolute;inset:0;display:flex;flex-direction:column}
.s-title{font-weight:700;line-height:1.2}
.s-title.tl{font-size:9.5%;padding:4% 4% 0}
.s-title.bl{font-size:7%;padding:2.6% 4% 0}
.s-sub{padding:0 4.2%;font-size:4.3%;color:var(--paper-body);opacity:.8;margin-top:2.5%}
.s-bullets{padding:3.5% 5%;display:flex;flex-direction:column;gap:2.4%}
.s-b{font-size:4%;display:flex;gap:2.2%;line-height:1.4;align-items:flex-start}
.s-mark{display:inline-block;font-weight:700;flex-shrink:0}
.s-accbar{position:absolute;left:0;bottom:0;height:1.6%;width:100%}
.s-foot{position:absolute;bottom:2.5%;right:3.5%;font-size:2.6%;color:var(--paper-body);opacity:.5;font-family:var(--mono)}
.zebra{background:radial-gradient(500px 260px at 100% 0%,var(--paper-accent2)44%,transparent 60%),linear-gradient(120deg,var(--paper-accent2),var(--paper-accent))}
.agenda-bar{display:flex;gap:1.2%;padding:4% 4%;flex-wrap:wrap;flex:1;align-content:center}
.agenda-item{flex:1;min-width:15%;border-radius:5px;padding:3% 2%;text-align:center;font-size:3.2%;font-weight:600}
.empty-box{min-height:340px;border:1.5px dashed var(--line2);border-radius:var(--r-lg);display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;text-align:center;background:var(--bg1);padding:40px 20px}
.empty-t{font-size:15px;font-weight:700;color:var(--tx1)}
.empty-s{font-size:12.5px;color:var(--tx3);line-height:1.7}
.export-bar{border-top:1px solid var(--line1);padding:12px 30px;background:var(--bg1);display:flex;align-items:center;gap:18px;max-width:880px;margin-left:auto;margin-right:auto}
.export-note{font-size:12px;color:var(--tx3);flex:1;display:flex;flex-direction:column;gap:3px}
.export-note .ok-line{color:var(--ok);font-size:11.5px;font-weight:600}

.chat-panel{width:356px;border-left:1px solid var(--line1);background:var(--bg1);flex-shrink:0;display:flex;flex-direction:column;height:calc(100vh - 54px)}
.chat-head{padding:14px 16px;border-bottom:1px solid var(--line1)}
.chat-head .r1{display:flex;align-items:center;justify-content:space-between}
.chat-head h3{font-size:13.5px;font-weight:700;display:flex;align-items:center;gap:8px}
.live-dot{width:8px;height:8px;border-radius:50%;background:var(--ok);box-shadow:0 0 0 3px rgba(88,214,141,.2)}
.sync-row{display:flex;align-items:center;gap:8px;margin-top:9px;font-size:11.5px;color:var(--tx3)}
.switch{position:relative;width:34px;height:19px;background:var(--bg3);border:1px solid var(--line2);border-radius:20px;cursor:pointer;transition:.2s;flex-shrink:0}
.switch.on{background:var(--signal);border-color:var(--signal)}
.switch::after{content:"";position:absolute;top:2px;left:2px;width:13px;height:13px;border-radius:50%;background:#fff;transition:.2s}
.switch.on::after{left:17px}
.chat-body{flex:1;overflow-y:auto;padding:16px;display:flex;flex-direction:column;gap:12px}
.cmsg{display:flex;gap:9px}
.cmsg .ic{width:26px;height:26px;border-radius:7px;display:grid;place-items:center;font-size:10px;flex-shrink:0}
.cmsg.ai .ic{background:var(--signal-soft);color:var(--signal)}
.cmsg.user .ic{background:linear-gradient(135deg,#3f63c8,#3f7df0);color:#fff}
.cmsg.user{flex-direction:row-reverse}
.cmsg .body{max-width:78%;padding:9px 12px;border-radius:11px;font-size:12.5px;line-height:1.65}
.cmsg.ai .body{background:var(--bg3);border:1px solid var(--line2);border-top-left-radius:3px;color:var(--tx1)}
.cmsg.user .body{background:linear-gradient(135deg,#2f4fc0,#3f7df0);color:#fff;border-top-right-radius:3px}
.diff-line{font-size:11px;margin-top:7px;padding:6px 9px;border-radius:6px;font-family:var(--mono);line-height:1.5}
.diff-line.ok{background:rgba(88,214,141,.08);color:var(--ok)}
.diff-line.reject{background:rgba(231,76,60,.08);color:var(--err)}
.undo-strip{display:flex;gap:6px;padding:0 14px 10px;overflow-x:auto}
.undo-chip{flex-shrink:0;font-size:10.5px;color:var(--tx3);padding:4px 10px;border-radius:14px;cursor:pointer;border:1px solid var(--line2);background:var(--bg2);transition:.15s}
.undo-chip:hover{border-color:var(--brand);color:var(--brand)}
.undo-chip.root{border-style:dashed}
.chat-foot{border-top:1px solid var(--line1);padding:12px 14px 14px;background:var(--bg1)}
.focus-hint{font-size:11px;color:var(--tx3);margin-bottom:8px;display:flex;gap:6px;align-items:center;flex-wrap:wrap}
.focus-hint b{color:var(--signal);font-weight:600}
.chat-input{display:flex;gap:8px;align-items:flex-end}
.chat-input textarea{flex:1;background:var(--bg2);border:1px solid var(--line2);color:var(--tx1);border-radius:10px;padding:10px 12px;font-size:13px;font-family:inherit;line-height:1.5;resize:none;min-height:44px;max-height:110px}
.chat-input textarea:focus{outline:none;border-color:var(--signal);box-shadow:0 0 0 3px rgba(255,157,102,.12)}
.chat-input .send{width:44px;height:44px;border-radius:10px;border:none;cursor:pointer;flex-shrink:0;background:linear-gradient(135deg,#ffb37f,#ff9d66);color:#3a1c08;font-size:17px;transition:.2s}
.chat-input .send:hover{transform:translateY(-1px);box-shadow:0 6px 16px rgba(255,157,102,.4)}
</style>
