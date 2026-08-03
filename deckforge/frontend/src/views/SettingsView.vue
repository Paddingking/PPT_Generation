<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { api } from '../api'

const emit = defineEmits<{ (e: 'toast', msg: string, color?: string): void }>()

const configs = ref<any[]>([])
const name = ref('我的主力 · 默认')
const protocol = ref('OPENAI_COMPAT')
const baseUrl = ref('https://api.deepseek.com/v1')
const apiKey = ref('')
const model = ref('deepseek-chat')
const testing = ref(false)
const activeInfo = ref<any>(null)
const contentPolish = ref(true)
const formatOptimize = ref(true)
const chineseFont = ref('微软雅黑')

async function load() {
  try {
    const res: any = await api.configList()
    if (res.success) {
      configs.value = res.data.configs || []
      activeInfo.value = res.data.active || null
      if (res.data.active?.present && res.data.active.baseUrl) {
        baseUrl.value = res.data.active.baseUrl
        protocol.value = res.data.active.protocol === 'ANTHROPIC_COMPAT' ? 'ANTHROPIC_COMPAT' : 'OPENAI_COMPAT'
        model.value = res.data.active.model || model.value
      }
    }
  } catch (e) {}
}

async function save() {
  if (!baseUrl.value.trim()) { emit('toast', '请填写 Base URL', '#f5b041'); return }
  const res: any = await api.configSave({
    name: name.value, protocol: protocol.value, baseUrl: baseUrl.value,
    apiKey: apiKey.value, model: model.value, active: true
  })
  if (res.success) {
    emit('toast', res.data?.message || '配置已保存 ✓', '#58d68d')
    await load()
  } else emit('toast', res.message, '#e74c3c')
}

async function testConn() {
  testing.value = true
  // 先临时保存以便测试（用当前表单但标记为不覆盖激活）
  try {
    const saved: any = await api.configSave({
      name: '测试连接临时', protocol: protocol.value, baseUrl: baseUrl.value,
      apiKey: apiKey.value, model: model.value, active: true
    })
    const res: any = await api.configTest({ id: saved.data?.id })
    if (res.success) emit('toast', '连接成功 ✓', '#58d68d')
    else emit('toast', '连接失败：' + res.message, '#e74c3c')
    await load()
  } catch (e: any) {
    emit('toast', '测试异常：' + (e?.message || e), '#e74c3c')
  } finally { testing.value = false }
}

onMounted(load)
</script>

<template>
  <div class="set-wrap">
    <div class="section-head" style="padding-left:0">
      <div><h2>LLM 供应商 · 本地配置</h2><p>支持 OpenAI 兼容 + Anthropic 兼容双协议，baseUrl 可自由配置 — 切换供应商零改码。</p></div>
    </div>

    <div class="card set-card">
      <h3>连接配置</h3>
      <div class="desc">内容与样式均发送至你配置的 LLM 供应商（mock 模式可不配 Key 直接演示全链路）。</div>
      <div v-if="activeInfo?.present" class="p-pill">
        <span class="pill blue">当前激活</span>
        <span class="muted" style="margin-left:6px">{{ activeInfo.name }} · {{ activeInfo.protocol }} · <span class="mono">{{ activeInfo.baseUrl }}</span> · key {{ activeInfo.apiKeyMasked }}</span>
      </div>
      <div v-else class="p-pill"><span class="pill orange">Mock 模式</span> <span class="muted" style="margin-left:6px">未配置真实 LLM，正在使用内置演示 Provider（无需 Key 即可全流程演示）</span></div>

      <div class="field">
        <label>协议兼容</label>
        <div class="proto-tabs">
          <button :class="{ on: protocol === 'OPENAI_COMPAT' }" @click="protocol='OPENAI_COMPAT'">OpenAI 兼容</button>
          <button :class="{ on: protocol === 'ANTHROPIC_COMPAT' }" @click="protocol='ANTHROPIC_COMPAT'">Anthropic 兼容</button>
        </div>
      </div>
      <div class="field"><label>配置名称</label><input class="inp" v-model="name" /></div>
      <div class="field"><label>Base URL</label><input class="inp mono" v-model="baseUrl" placeholder="https://…" /></div>
      <div class="field"><label>API Key <span class="muted" style="font-weight:400">（mock 模式可留空）</span></label><input class="inp mono" type="password" v-model="apiKey" placeholder="sk-…" /></div>
      <div class="field"><label>模型</label><input class="inp" v-model="model" placeholder="model-id" /></div>
      <div style="display:flex;gap:10px;margin-top:6px">
        <button class="btn ghost sm" @click="testConn" :disabled="testing">{{ testing ? '测试中…' : '测试连接' }}</button>
        <button class="btn primary sm" @click="save">保存配置</button>
      </div>
    </div>

    <div class="card set-card">
      <h3>生成偏好（默认）</h3>
      <div class="desc">控制双通道 LLM 的默认行为。</div>
      <div style="display:flex;flex-direction:column;gap:13px">
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div><b style="font-size:13px">内容润色</b><div class="muted" style="font-size:11.5px;margin-top:2px">默认只润色措辞，不增删要点条数</div></div>
          <div class="switch" :class="{ on: contentPolish }" @click="contentPolish=!contentPolish"></div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div><b style="font-size:13px">格式优化（设计决策）</b><div class="muted" style="font-size:11.5px;margin-top:2px">LLM 产出结构化设计决策 JSON，非法值回退骨架</div></div>
          <div class="switch" :class="{ on: formatOptimize }" @click="formatOptimize=!formatOptimize"></div>
        </div>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <div><b style="font-size:13px">默认中文字体</b><div class="muted" style="font-size:11.5px;margin-top:2px">微软雅黑 / 宋体 回退 · 防乱码</div></div>
          <div class="seg">
            <button :class="{ on: chineseFont === '微软雅黑' }" @click="chineseFont='微软雅黑'">微软雅黑</button>
            <button :class="{ on: chineseFont === '宋体' }" @click="chineseFont='宋体'">宋体</button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.set-wrap{max-width:680px;margin:0 auto;padding:34px 24px 60px}
.set-card{padding:22px;margin-bottom:16px}
.set-card h3{font-size:15px;font-weight:700;margin-bottom:4px;display:flex;align-items:center;gap:8px}
.set-card .desc{font-size:12.5px;color:var(--tx3);margin-bottom:18px;line-height:1.6}
.p-pill{margin-bottom:16px;display:flex;align-items:center;gap:4px;flex-wrap:wrap}
.field{margin-bottom:14px}
.field label{display:block;font-size:12px;color:var(--tx2);margin-bottom:6px;font-weight:600}
.proto-tabs{display:flex;gap:8px;margin-bottom:14px}
.proto-tabs button{padding:7px 14px;border-radius:8px;cursor:pointer;transition:.18s;background:var(--bg3);border:1px solid var(--line2);color:var(--tx2);font-size:12.5px;font-family:inherit}
.proto-tabs button.on{background:var(--brand-soft);border-color:var(--brand);color:var(--brand)}
.inp{width:100%;background:var(--bg1);border:1px solid var(--line2);color:var(--tx1);border-radius:var(--r-sm);padding:9px 12px;font-size:13px;font-family:inherit;transition:.15s}
.inp:focus{outline:none;border-color:var(--brand)}
.inp.mono{font-family:var(--mono);font-size:12px}
.switch{position:relative;width:34px;height:19px;background:var(--bg3);border:1px solid var(--line2);border-radius:20px;cursor:pointer;transition:.2s;flex-shrink:0}
.switch.on{background:var(--signal);border-color:var(--signal)}
.switch::after{content:"";position:absolute;top:2px;left:2px;width:13px;height:13px;border-radius:50%;background:#fff;transition:.2s}
.switch.on::after{left:17px}
.seg{display:flex;background:var(--bg2);border:1px solid var(--line2);border-radius:8px;overflow:hidden}
.seg button{background:none;border:none;color:var(--tx3);font-size:12px;padding:7px 14px;cursor:pointer;font-family:inherit;transition:.15s}
.seg button:hover{color:var(--tx1)}
.seg button.on{background:var(--bg3);color:var(--tx1)}
</style>
