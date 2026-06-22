import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// ============================================================================
// Dev Proxy 说明
// ----------------------------------------------------------------------------
// 当前为【P3 单体阶段】：前端直连各微服务，后端 controller 路径不带 /api 前缀，
// 因此需要 rewrite 去掉 /api 再转发。
//   - /api/user    -> http://localhost:8081 (用户服务)
//   - /api/product -> http://localhost:8082 (商品服务)
//   - /api/order   -> http://localhost:8083 (订单服务，P3 暂未使用)
//
// 【P4 网关上线后】：把下面三条 proxy 整体替换为单条即可（网关自身
// StripPrefix 会去掉 /api，无需前端 rewrite）：
//   proxy: {
//     '/api': { target: 'http://localhost:8080', changeOrigin: true }
//   }
// ============================================================================
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      // @ 指向 src，配合各文件中的 '@/...' 导入
      '@': fileURLToPath(new URL('./src', import.meta.url))
    }
  },
  server: {
    port: 5173,
    proxy: {
      '/api/user': {
        target: 'http://localhost:8081',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/api/product': {
        target: 'http://localhost:8082',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      },
      '/api/order': {
        target: 'http://localhost:8083',
        changeOrigin: true,
        rewrite: (p) => p.replace(/^\/api/, '')
      }
    }
  }
})
