<script setup lang="ts">
import { ref, onMounted } from 'vue'
import EntryView from './views/EntryView.vue'
import LibraryView from './views/LibraryView.vue'
import PreviewView from './views/PreviewView.vue'
import SettingsView from './views/SettingsView.vue'
import { api } from './api'

type ViewName = 'entry' | 'library' | 'preview' | 'settings'

const current = ref<ViewName>('entry')
const generated = ref(false)
const currentProject = ref<any>(null)   // { id, name, slidesCount }
const toastMsg = ref('')
const toastColor = ref('#58d68d')
const toastShow = ref(false)
let toastTimer: any = null

function toast(msg: string, color: string = '#58d68d') {
  toastMsg.value = msg
  toastColor.value = color
  toastShow.value = true
  clearTimeout(toastTimer)
  toastTimer = setTimeout(() => (toastShow.value = false), 2600)
}

// 供各视图设置当前项目
function setProject(project: any) {
  currentProject.value = project
  if (project && project.slidesCount) {
    generated.value = true
  }
}

function goView(name: ViewName) {
  current.value = name
}

function exportCurrent() {
  if (!currentProject.value?.id) { toast('请先生成内容再导出', '#f5b041'); return }
  api.projectDetail(currentProject.value.id).then((res: any) => {
    const d = res.data || {}
    if (!d.content?.slides?.length) { toast('大纲为空，无法导出', '#f5b041'); return }
    window.open('/api/projects/' + currentProject.value.id + '/export', '_blank')
    toast('已导出原生可编辑 PPTX ✓', '#58d68d')
  })
}

defineExpose({ toast, setProject })

onMounted(() => {
  toast('欢迎使用 DeckForge · PPT 工作台 — 从一句话到一页页可编辑 PPT')
})
</script>

<template>
  <div>
    <!-- 顶栏 -->
    <header class="topbar">
      <div class="brand">
        <div class="logo">D</div>
        <span>DeckForge<small>PPT 工作台</small></span>
      </div>
      <nav class="top-nav">
        <a :class="{ active: current === 'entry' }" @click="goView('entry')">创作入口</a>
        <a :class="{ active: current === 'library' }" @click="goView('library')">模板库</a>
        <a :class="{ active: current === 'preview' }" @click="goView('preview')">预览编辑</a>
        <a :class="{ active: current === 'settings' }" @click="goView('settings')">设置</a>
      </nav>
      <div class="spacer"></div>
      <div class="prj-badge">
        <template v-if="currentProject">项目 · <b>{{ currentProject.name }}</b> · {{ currentProject.slidesCount }} 页</template>
        <template v-else>尚未创建项目</template>
      </div>
      <div class="avatar">许</div>
    </header>

    <!-- 工作区 -->
    <div class="workspace">
      <!-- 左侧流程栏 -->
      <aside class="stage-rail">
        <div class="rail-title">制作流程</div>
        <div class="rail-step" :class="{ done: current !== 'entry' && generated, active: current === 'entry' }" @click="goView('entry')">
          <div class="step-num">1</div><div class="step-txt"><b>输入与约束</b><span>阶段A · 生成前</span></div>
        </div>
        <div class="rail-step" :class="{ done: current !== 'library', active: current === 'library' }" @click="goView('library')">
          <div class="step-num">2</div><div class="step-txt"><b>套用模板</b><span>内置 / 导入</span></div>
        </div>
        <div class="rail-step" :class="{ done: generated, active: current === 'preview' }" @click="goView('preview')">
          <div class="step-num">3</div><div class="step-txt"><b>预览与微调</b><span>阶段B · 常驻</span></div>
        </div>
        <div class="rail-step" :class="{ active: current === 'settings' }" @click="goView('settings')">
          <div class="step-num">4</div><div class="step-txt"><b>设置</b><span>LLM 配置</span></div>
        </div>
        <div class="rail-note">内容 JSON ⟷ 样式 JSON<br>双通道 · diff 增量 · 快照撤销</div>
      </aside>

      <!-- 主内容 -->
      <main class="main">
        <EntryView v-if="current === 'entry'" @created="(p) => { setProject(p); goView('preview') }" @toast="toast"/>
        <LibraryView v-else-if="current === 'library'" @toast="toast"/>
        <PreviewView v-else-if="current === 'preview'" :project="currentProject" @set-project="setProject" @toast="toast"/>
        <SettingsView v-else @toast="toast"/>
      </main>
    </div>

    <!-- toast -->
    <div class="toast" :class="{ show: toastShow }">
      <span class="dot" :style="{ background: toastColor }"></span>
      <span>{{ toastMsg }}</span>
    </div>
  </div>
</template>
