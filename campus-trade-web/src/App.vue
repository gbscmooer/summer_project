<template>
  <!-- 登录/注册：无侧栏 -->
  <div v-if="isAuthPage" class="auth-layout">
    <router-view />
  </div>

  <!-- OpenAI Platform 主布局 -->
  <div v-else class="platform-layout">
    <aside class="sidebar">
      <!-- 项目选择器 -->
      <div class="project-selector">
        <div class="project-icon">
          <img src="/logo.png" alt="" class="project-logo" />
        </div>
        <span class="project-name">校园淘</span>
        <el-icon class="project-chevron"><ArrowDown /></el-icon>
      </div>

      <!-- 主导航 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ item.label }}</span>
          <el-badge
            v-if="item.path === '/notifications' && unreadCount > 0"
            :value="unreadCount"
            :max="99"
            class="nav-badge"
          />
        </router-link>
      </nav>

      <!-- 底部引导卡片 -->
      <div v-if="!userStore.isLogin" class="sidebar-promo">
        <p class="promo-text">登录后即可发布闲置、下单交易</p>
        <button class="promo-btn" @click="$router.push('/login')">去登录</button>
      </div>

      <!-- 底部用户 -->
      <div class="sidebar-footer">
        <template v-if="userStore.isLogin">
          <el-dropdown trigger="click" placement="top-start" @command="handleCommand">
            <div class="user-block">
              <div class="user-avatar">{{ avatarLetter }}</div>
              <div class="user-info">
                <span class="user-name">{{ userStore.displayName || '用户' }}</span>
                <span class="user-org">Personal · Organization</span>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="dropdown-email">{{ userStore.displayName }}</div>
                <el-dropdown-item command="my">个人中心</el-dropdown-item>
                <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <div class="user-block guest" @click="$router.push('/login')">
            <div class="user-avatar guest-avatar">?</div>
            <div class="user-info">
              <span class="user-name">未登录</span>
              <span class="user-org">点击登录</span>
            </div>
          </div>
        </template>
      </div>
    </aside>

    <main class="main-content">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  House,
  EditPen,
  ShoppingCart,
  Bell,
  User,
  Setting,
  ArrowDown
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getUnreadCount } from '@/api/notification'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const unreadCount = ref(0)
let unreadTimer = null

const isAuthPage = computed(() => ['/login', '/register'].includes(route.path))

const navItems = computed(() => {
  const items = [
    { path: '/', label: 'Home', icon: House },
    { path: '/publish', label: 'Publish', icon: EditPen },
    { path: '/orders', label: 'Orders', icon: ShoppingCart },
    { path: '/notifications', label: 'Notifications', icon: Bell },
    { path: '/my', label: 'Profile', icon: User },
    { path: '/settings', label: 'Settings', icon: Setting }
  ]
  return items
})

const avatarLetter = computed(() => {
  const name = userStore.displayName || 'U'
  return name.charAt(0).toUpperCase()
})

function isActive(path) {
  if (path === '/') return route.path === '/'
  if (path === '/settings') return false
  return route.path.startsWith(path)
}

async function refreshUnreadCount() {
  if (!userStore.isLogin) {
    unreadCount.value = 0
    return
  }
  try {
    const res = await getUnreadCount()
    unreadCount.value = Number(res.data) || 0
  } catch {
    unreadCount.value = 0
  }
}

function startUnreadPolling() {
  stopUnreadPolling()
  refreshUnreadCount()
  unreadTimer = setInterval(refreshUnreadCount, 30000)
}

function stopUnreadPolling() {
  if (unreadTimer) {
    clearInterval(unreadTimer)
    unreadTimer = null
  }
}

watch(
  () => userStore.isLogin,
  (loggedIn) => {
    if (loggedIn) startUnreadPolling()
    else {
      stopUnreadPolling()
      unreadCount.value = 0
    }
  },
  { immediate: true }
)

watch(
  () => route.path,
  (path) => {
    if (path === '/notifications' || path === '/orders') refreshUnreadCount()
    if (path === '/settings') router.replace('/my')
  }
)

onMounted(() => {
  if (userStore.isLogin) startUnreadPolling()
})

onUnmounted(stopUnreadPolling)

function handleCommand(command) {
  if (command === 'my') router.push('/my')
  else if (command === 'logout') {
    ElMessageBox.confirm('确定要退出登录吗？', '提示', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning'
    })
      .then(() => {
        userStore.logout()
        ElMessage.success('已退出登录')
        router.push('/login')
      })
      .catch(() => {})
  }
}
</script>

<style scoped>
.platform-layout {
  display: flex;
  min-height: 100%;
  background: var(--oa-bg);
}

/* ---- Sidebar ---- */
.sidebar {
  width: var(--oa-sidebar-width);
  min-height: 100vh;
  background: var(--oa-bg-sidebar);
  border-right: 1px solid var(--oa-border-subtle);
  display: flex;
  flex-direction: column;
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  z-index: 100;
}

.project-selector {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin: 8px 8px 4px;
  border-radius: var(--oa-radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.project-selector:hover {
  background: var(--oa-bg-hover);
}

.project-icon {
  width: 28px;
  height: 28px;
  border-radius: 6px;
  overflow: hidden;
  flex-shrink: 0;
}

.project-logo {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.project-name {
  flex: 1;
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-chevron {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.sidebar-nav {
  flex: 1;
  padding: 4px 8px;
  overflow-y: auto;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  margin-bottom: 2px;
  border-radius: var(--oa-radius-sm);
  color: var(--oa-text-secondary);
  font-size: 14px;
  transition: background 0.15s, color 0.15s;
  position: relative;
}

.nav-item:hover {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
}

.nav-item.active {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
}

.nav-icon {
  font-size: 16px;
  flex-shrink: 0;
}

.nav-label {
  flex: 1;
}

.nav-badge :deep(.el-badge__content) {
  background: #ef4444;
  border: none;
  font-size: 10px;
  height: 16px;
  line-height: 16px;
  padding: 0 5px;
}

.sidebar-promo {
  margin: 8px 12px;
  padding: 12px;
  background: var(--oa-bg-elevated);
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius-sm);
}

.promo-text {
  font-size: 12px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
  margin-bottom: 10px;
}

.promo-btn {
  width: 100%;
  padding: 6px 12px;
  background: var(--oa-text);
  color: #000;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: background 0.15s;
}

.promo-btn:hover {
  background: #d9d9d9;
}

.sidebar-footer {
  padding: 8px;
  border-top: 1px solid var(--oa-border-subtle);
}

.user-block {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px 10px;
  border-radius: var(--oa-radius-sm);
  cursor: pointer;
  transition: background 0.15s;
  width: 100%;
}

.user-block:hover {
  background: var(--oa-bg-hover);
}

.user-avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  background: #555;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 600;
  flex-shrink: 0;
}

.guest-avatar {
  background: #444;
  color: var(--oa-text-secondary);
}

.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
}

.user-name {
  font-size: 13px;
  font-weight: 500;
  color: var(--oa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-org {
  font-size: 11px;
  color: var(--oa-text-muted);
}

.dropdown-email {
  padding: 8px 16px 4px;
  font-size: 13px;
  color: var(--oa-text-secondary);
}

/* ---- Main ---- */
.main-content {
  flex: 1;
  margin-left: var(--oa-sidebar-width);
  min-height: 100vh;
  padding: 32px 40px 48px;
}

.auth-layout {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--oa-bg);
}
</style>
