import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'

// ============================================================================
// Dev Proxy 说明
// ----------------------------------------------------------------------------
// 【P4 网关上线后，已切换到网关】：前端不再直连各微服务，所有 /api 请求统一
// 转发到 API 网关（8080）。网关自身的 StripPrefix=1 会去掉 /api 前缀再路由到
// 对应服务，因此前端无需 rewrite。
//   /api/** -> http://localhost:8080 (campus-gateway)
//             ├─ /api/user/**    -> campus-user (8081)
//             ├─ /api/product/** -> campus-product (8082)
//             └─ /api/order/**   -> campus-order (8083)
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
      '/api': { target: 'http://localhost:8080', changeOrigin: true }
    }
  }
})
