import { createRouter, createWebHistory } from 'vue-router'
import { ElMessage } from 'element-plus'
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
    path: '/forgot-password',
    name: 'ForgotPassword',
    component: () => import('@/views/ForgotPassword.vue'),
    meta: { title: '忘记密码' }
  },
  {
    path: '/reset-password',
    name: 'ResetPassword',
    component: () => import('@/views/ResetPassword.vue'),
    meta: { title: '重置密码' }
  },
  {
    path: '/product/:id',
    name: 'ProductDetail',
    component: () => import('@/views/ProductDetail.vue'),
    meta: { title: '商品详情' }
  },
  {
    path: '/marketplace',
    name: 'Marketplace',
    component: () => import('@/views/Marketplace.vue'),
    meta: { title: '积分商城' }
  },
  {
    path: '/topics',
    name: 'Topics',
    component: () => import('@/views/Topics.vue'),
    meta: { title: '话题' }
  },
  {
    path: '/topics/create',
    name: 'TopicCreate',
    component: () => import('@/views/TopicCreate.vue'),
    meta: { title: '发帖', requiresAuth: true }
  },
  {
    path: '/topics/:id',
    name: 'TopicDetail',
    component: () => import('@/views/TopicDetail.vue'),
    meta: { title: '帖子详情' }
  },
  {
    path: '/users/:id',
    name: 'UserProfile',
    component: () => import('@/views/UserProfile.vue'),
    meta: { title: '用户主页', fullBleed: true }
  },
  {
    path: '/publish',
    name: 'Publish',
    component: () => import('@/views/Publish.vue'),
    meta: { title: '发布商品', requiresAuth: true, requiresMerchant: true }
  },
  {
    path: '/merchant',
    name: 'Merchant',
    component: () => import('@/views/Merchant.vue'),
    meta: { title: '商家中心', requiresAuth: true, requiresMerchant: true }
  },
  {
    path: '/ai-shopping',
    redirect: '/?mode=ai'
  },
  {
    path: '/my',
    name: 'My',
    component: () => import('@/views/My.vue'),
    meta: { title: '个人主页', requiresAuth: true, fullBleed: true }
  },
  {
    path: '/activity',
    name: 'Activity',
    component: () => import('@/views/Activity.vue'),
    meta: { title: '我的动态', requiresAuth: true }
  },
  {
    path: '/favorites',
    name: 'Favorites',
    component: () => import('@/views/Favorites.vue'),
    meta: { title: '我的收藏', requiresAuth: true }
  },
  {
    path: '/events',
    name: 'Events',
    component: () => import('@/views/Events.vue'),
    meta: { title: '活动', requiresAuth: true }
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('@/views/Orders.vue'),
    meta: { title: '我的订单', requiresAuth: true }
  },
  {
    path: '/notifications',
    name: 'Notifications',
    component: () => import('@/views/Notifications.vue'),
    meta: { title: '消息通知', requiresAuth: true }
  },
  {
    path: '/messages',
    name: 'Messages',
    component: () => import('@/views/Messages.vue'),
    meta: { title: '私信', requiresAuth: true }
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
    meta: { title: '管理后台', requiresAuth: true, requiresNotifyAccess: true }
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
router.beforeEach(async (to, from, next) => {
  // 设置页面标题
  if (to.meta && to.meta.title) {
    document.title = `校园集市 · ${to.meta.title}`
  }

  const userStore = useUserStore()
  if (to.meta && to.meta.requiresAuth && !userStore.isLogin) {
    next({ path: '/login', query: { redirect: to.fullPath } })
    return
  }
  if (to.meta && to.meta.requiresNotifyAccess) {
    if (userStore.isLogin && !userStore.canSendNotification) {
      await userStore.refreshProfile()
    }
    if (!userStore.canSendNotification) {
      ElMessage.warning('需要管理员或特殊认证权限才能访问')
      next({ path: '/' })
      return
    }
  }
  if (to.meta && to.meta.requiresMerchant) {
    if (userStore.isLogin && !userStore.isMerchant && !userStore.isAdmin) {
      await userStore.refreshProfile()
    }
    if (!userStore.isMerchant && !userStore.isAdmin) {
      ElMessage.warning('需要商家认证后才能访问，请先申请成为商家')
      next({ path: '/events' })
      return
    }
  }
  next()
})

export default router
