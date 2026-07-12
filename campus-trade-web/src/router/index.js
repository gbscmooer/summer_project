import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/store/user'

const routes = [
  {
    path: '/',
    name: 'Home',
    component: () => import('@/views/Home.vue'),
    meta: { title: '首页' }
  },
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/Login.vue'),
    meta: { title: '登录' }
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/Register.vue'),
    meta: { title: '注册' }
  },
  {
    path: '/product/:id',
    name: 'ProductDetail',
    component: () => import('@/views/ProductDetail.vue'),
    meta: { title: '商品详情' }
  },
  {
    path: '/topics',
    name: 'Topics',
    component: () => import('@/views/Topics.vue'),
    meta: { title: '话题' }
  },
  {
    path: '/topics/:id',
    name: 'TopicDetail',
    component: () => import('@/views/TopicDetail.vue'),
    meta: { title: '帖子详情' }
  },
  {
    path: '/publish',
    name: 'Publish',
    component: () => import('@/views/Publish.vue'),
    // 需要登录
    meta: { title: '发布商品', requiresAuth: true }
  },
  {
    path: '/ai-shopping',
    name: 'AiShopping',
    component: () => import('@/views/AiShopping.vue'),
    meta: { title: 'AI 帮我找', requiresAuth: true }
  },
  {
    path: '/my',
    name: 'My',
    component: () => import('@/views/My.vue'),
    // 需要登录
    meta: { title: '我的', requiresAuth: true }
  },
  {
    path: '/activity',
    name: 'Activity',
    component: () => import('@/views/Activity.vue'),
    meta: { title: '我的动态', requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('@/views/Orders.vue'),
    // 需要登录
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { title: '消息通知', requiresAuth: true }
  },
  {
    path: '/settings',
    name: 'Settings',
    component: () => import('@/views/Settings.vue'),
    meta: { title: '设置', requiresAuth: true }
  },
  {
    path: '/admin',
    name: 'Admin',
    component: () => import('@/views/Admin.vue'),
    meta: { title: '管理后台', requiresAuth: true, requiresAdmin: true }
  },
  // 兜底：未匹配路由回首页
  {
    path: '/:pathMatch(.*)*',
    redirect: '/'
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// ----------------------------------------------------------------------------
// 全局前置守卫：需登录页面在未登录时跳转 /login，并携带 redirect 便于登录后回跳
// ----------------------------------------------------------------------------
router.beforeEach((to, from, next) => {
  // 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = `校园集市 · ${to.meta.title}`
  }

  const userStore = useUserStore()
  if (to.meta && to.meta.requiresAuth && !userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
  } else if (to.meta && to.meta.requiresAdmin && !userStore.isAdmin) {
    next({ path: '/' })
  } else {
    next()
  }
})

export default router
