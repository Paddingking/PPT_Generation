import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [vue()],
  server: {
    port: 5173,
    host: true,
    proxy: {
      // 前端开发时把 /api 代理到后端 8090
      '/api': {
        target: 'http://localhost:8090',
        changeOrigin: true
      }
    }
  }
})
