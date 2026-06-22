import { createApp } from 'vue'
import { createPinia } from 'pinia'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import * as ElementPlusIconsVue from '@element-plus/icons-vue'

import App from './App.vue'
import router from './router'
import './styles.css'

const app = createApp(App)

// 注意顺序：先装 Pinia，再装 Router。
// 因为 router 的全局守卫与 axios 拦截器都会用到 useUserStore()。
app.use(createPinia())
app.use(router)
app.use(ElementPlus)

// 全局注册 Element Plus 图标
for (const [name, component] of Object.entries(ElementPlusIconsVue)) {
  app.component(name, component)
}

app.mount('#app')
