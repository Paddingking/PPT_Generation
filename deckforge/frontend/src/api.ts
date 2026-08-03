import axios from 'axios'

const http = axios.create({
  baseURL: '/api',
  timeout: 120000
})

export const api = {
  health: () => http.get('/health').then(r => r.data),

  // 项目
  generate: (payload) => http.post('/projects/generate', payload).then(r => r.data),
  projectDetail: (id) => http.get('/projects/' + id).then(r => r.data),
  projectList: () => http.get('/projects').then(r => r.data),

  // 模板
  listTemplates: () => http.get('/templates').then(r => r.data),
  importTemplate: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/templates/import', fd, { headers: { 'Content-Type': 'multipart/form-data' }, timeout: 60000 }).then(r => r.data)
  },

  // 上传输入文件
  uploadInput: (file) => {
    const fd = new FormData()
    fd.append('file', file)
    return http.post('/upload/input', fd, { headers: { 'Content-Type': 'multipart/form-data' } }).then(r => r.data)
  },

  // 对话
  constrain: (payload) => http.post('/chat/constrain', payload).then(r => r.data),
  polish: (payload) => http.post('/chat/polish', payload).then(r => r.data),
  setSyncStyle: (projectId, on) => http.post(`/chat/sync-style?projectId=${projectId}&on=${on}`).then(r => r.data),
  undo: (payload) => http.post('/chat/undo', payload).then(r => r.data),
  history: (projectId) => http.get('/chat/history/' + projectId).then(r => r.data),

  // 配置
  configList: () => http.get('/config/llm').then(r => r.data),
  configSave: (payload) => http.post('/config/llm', payload).then(r => r.data),
  configTest: (payload) => http.post('/config/llm/test', payload).then(r => r.data),
  configActivate: (id) => http.post(`/config/llm/activate?id=${id}`).then(r => r.data),
  configActive: () => http.get('/config/llm/active').then(r => r.data),

  // 导出
  exportUrl: (id) => `/api/projects/${id}/export`
}

// 导出下载
export function downloadExport(id) {
  window.open(api.exportUrl(id), '_blank')
}
