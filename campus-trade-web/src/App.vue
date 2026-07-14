<template>
  <!-- 登录/注册：无侧栏 -->
  <div v-if="isAuthPage" class="auth-layout">
    <router-view />
  </div>

  <!-- OpenAI Platform 主布局 -->
  <div
    v-else
    class="platform-layout"
    :class="{
      'sidebar-collapsed': sidebarCollapsed,
      'mobile-nav-open': mobileMenuOpen
    }"
  >
    <!-- 手机顶栏 -->
    <header class="mobile-topbar">
      <button type="button" class="mobile-icon-btn" aria-label="打开菜单" @click="openMobileMenu">
        <el-icon :size="20"><Menu /></el-icon>
      </button>
      <div class="mobile-brand" @click="$router.push('/')">
        <img src="/logo.png" alt="" class="mobile-brand-logo" />
        <span class="mobile-brand-name">校园集市</span>
      </div>
      <button
        type="button"
        class="mobile-icon-btn mobile-icon-btn--badge"
        aria-label="通知"
        @click="$router.push('/notifications')"
      >
        <el-icon :size="20"><Bell /></el-icon>
        <span v-if="unreadCount > 0" class="mobile-dot">{{ unreadCount > 99 ? '99+' : unreadCount }}</span>
      </button>
    </header>

    <div
      v-if="mobileMenuOpen"
      class="mobile-backdrop"
      @click="closeMobileMenu"
    />

    <aside
      class="sidebar"
      :class="{
        'sidebar--collapsed': sidebarCollapsed,
        'sidebar--mobile-open': mobileMenuOpen
      }"
    >
      <!-- 项目选择器 -->
      <div class="project-selector">
        <div class="project-icon">
          <img src="/logo.png" alt="" class="project-logo" />
        </div>
        <span class="project-name">校园集市</span>
        <button
          type="button"
          class="sidebar-toggle desktop-only"
          :title="sidebarCollapsed ? '展开侧栏' : '收起侧栏'"
          @click="toggleSidebar"
        >
          <el-icon>
            <Expand v-if="sidebarCollapsed" />
            <Fold v-else />
          </el-icon>
        </button>
        <button
          type="button"
          class="sidebar-toggle mobile-only"
          title="关闭菜单"
          @click="closeMobileMenu"
        >
          <el-icon><Close /></el-icon>
        </button>
      </div>

      <!-- 主导航 -->
      <nav class="sidebar-nav">
        <router-link
          v-for="item in navItems"
          :key="item.path"
          :to="item.path"
          class="nav-item"
          :class="{ active: isActive(item.path) }"
          @click="closeMobileMenu"
        >
          <el-icon class="nav-icon"><component :is="item.icon" /></el-icon>
          <span class="nav-label">{{ t(item.labelKey) }}</span>
          <el-badge
            v-if="item.path === '/notifications' && unreadCount > 0"
            :value="unreadCount"
            :max="99"
            class="nav-badge"
          />
          <el-badge
            v-else-if="item.path === '/messages' && messageUnreadCount > 0"
            :value="messageUnreadCount"
            :max="99"
            class="nav-badge"
          />
        </router-link>
      </nav>

      <!-- 底部引导卡片 -->
      <div v-if="!userStore.isLogin" class="sidebar-promo">
        <p class="promo-text">{{ t('common.promo') }}</p>
        <button class="promo-btn" @click="goLoginFromMobile">{{ t('common.login') }}</button>
      </div>

      <!-- 底部用户 -->
      <div class="sidebar-footer">
        <template v-if="userStore.isLogin">
          <div class="points-row" @click="goAndClose('/events')">
            <span class="points-label">{{ t('common.points') }}</span>
            <span class="points-value">{{ userStore.points }}</span>
          </div>
          <el-dropdown trigger="click" placement="top-start" @command="handleCommand">
            <div class="user-block">
              <div class="user-avatar">{{ avatarLetter }}</div>
              <div class="user-info">
                <span class="user-name">{{ userStore.displayName || '用户' }}</span>
                <span class="user-org">{{ userStore.roleLabel }}</span>
              </div>
            </div>
            <template #dropdown>
              <el-dropdown-menu>
                <div class="dropdown-email">{{ userStore.displayName }}</div>
                <el-dropdown-item command="my">{{ t('common.profile') }}</el-dropdown-item>
                <el-dropdown-item command="events">{{ t('nav.events') }}</el-dropdown-item>
                <el-dropdown-item v-if="userStore.canSendNotification" command="admin">{{ t('nav.admin') }}</el-dropdown-item>
                <el-dropdown-item command="logout" divided>{{ t('common.logout') }}</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </template>
        <template v-else>
          <div class="user-block guest" @click="goLoginFromMobile">
            <div class="user-avatar guest-avatar">?</div>
            <div class="user-info">
              <span class="user-name">{{ t('common.notLoggedIn') }}</span>
              <span class="user-org">{{ t('common.clickToLogin') }}</span>
            </div>
          </div>
        </template>
      </div>
    </aside>

    <main class="main-content" :class="{ 'main-content--bleed': route.meta.fullBleed }">
      <router-view />
    </main>

    <!-- 手机底部主导航 -->
    <nav class="mobile-tabbar" aria-label="底部导航">
      <router-link
        v-for="item in mobileTabItems"
        :key="item.path"
        :to="item.path"
        class="mobile-tab"
        :class="{ active: isActive(item.path) }"
      >
        <span class="mobile-tab-icon-wrap">
          <el-icon :size="20"><component :is="item.icon" /></el-icon>
          <span
            v-if="item.path === '/messages' && messageUnreadCount > 0"
            class="mobile-dot mobile-dot--tab"
          >{{ messageUnreadCount > 99 ? '99+' : messageUnreadCount }}</span>
        </span>
        <span class="mobile-tab-label">{{ t(item.labelKey) }}</span>
      </router-link>
    </nav>
  </div>
</template>

<script setup>
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import {
  ChatDotRound,
  Message,
  House,
  DataLine,
  EditPen,
  Shop,
  ShoppingCart,
  ShoppingBag,
  Bell,
  User,
  Setting,
  Tools,
  Trophy,
  Fold,
  Expand,
  Menu,
  Close
} from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'
import { getUnreadCount } from '@/api/notification'
import { getMessageUnreadCount } from '@/api/social'
import { useI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const unreadCount = ref(0)
const messageUnreadCount = ref(0)
let unreadTimer = null
const unreadRefreshEvent = 'campus:unread-count-refresh'
const messageUnreadRefreshEvent = 'campus:message-unread-refresh'

const SIDEBAR_KEY = 'campus-sidebar-collapsed'
const sidebarCollapsed = ref(
  typeof localStorage !== 'undefined' && localStorage.getItem(SIDEBAR_KEY) === '1'
)
const mobileMenuOpen = ref(false)

function toggleSidebar() {
  sidebarCollapsed.value = !sidebarCollapsed.value
  localStorage.setItem(SIDEBAR_KEY, sidebarCollapsed.value ? '1' : '0')
}

function openMobileMenu() {
  mobileMenuOpen.value = true
}

function closeMobileMenu() {
  mobileMenuOpen.value = false
}

function goAndClose(path) {
  closeMobileMenu()
  router.push(path)
}

function goLoginFromMobile() {
  closeMobileMenu()
  router.push('/login')
}

const isAuthPage = computed(() => ['/login', '/register', '/forgot-password', '/reset-password'].includes(route.path))

const navItems = computed(() => {
  const items = [
    { path: '/', labelKey: 'nav.home', icon: House },
    { path: '/topics', labelKey: 'nav.topics', icon: ChatDotRound },
    { path: '/marketplace', labelKey: 'nav.marketplace', icon: ShoppingBag }
  ]
  // 订单对所有登录用户可见（买家购买 / 卖家销售）；发布仅商家或管理员
  if (userStore.isLogin) {
    items.push({ path: '/orders', labelKey: 'nav.orders', icon: ShoppingCart })
  }
  if (userStore.isMerchant || userStore.isAdmin) {
    items.push({ path: '/merchant', labelKey: 'nav.merchant', icon: Shop })
    items.push({ path: '/publish', labelKey: 'nav.publish', icon: EditPen })
  }
  if (userStore.isLogin) {
    items.push({ path: '/messages', labelKey: 'nav.messages', icon: Message })
  }
  items.push(
    { path: '/notifications', labelKey: 'nav.notifications', icon: Bell },
    { path: '/activity', labelKey: 'nav.activity', icon: DataLine },
    { path: '/events', labelKey: 'nav.events', icon: Trophy },
    { path: '/my', labelKey: 'nav.profile', icon: User }
  )
  if (userStore.isAdmin || userStore.isOfficial) {
    // 管理员 / 特殊认证入口靠前，避免侧栏过长时被挤到下方
    items.splice(3, 0, { path: '/admin', labelKey: 'nav.admin', icon: Tools })
  }
  items.push({ path: '/settings', labelKey: 'nav.settings', icon: Setting })
  return items
})

const mobileTabItems = computed(() => {
  const tabs = [
    { path: '/', labelKey: 'nav.home', icon: House },
    { path: '/topics', labelKey: 'nav.topics', icon: ChatDotRound },
    { path: '/marketplace', labelKey: 'nav.marketplace', icon: ShoppingBag }
  ]
  if (userStore.isLogin) {
    tabs.push({ path: '/messages', labelKey: 'nav.messages', icon: Message })
  }
  tabs.push({ path: '/my', labelKey: 'nav.profile', icon: User })
  return tabs
})

const avatarLetter = computed(() => {
  const name = userStore.displayName || 'U'
  return name.charAt(0).toUpperCase()
})

function isActive(path) {
  if (path === '/') return route.path === '/'
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

async function refreshMessageUnreadCount() {
  if (!userStore.isLogin) {
    messageUnreadCount.value = 0
    return
  }
  try {
    const res = await getMessageUnreadCount()
    const data = res.data
    messageUnreadCount.value = Number(data?.unreadCount ?? data) || 0
  } catch {
    messageUnreadCount.value = 0
  }
}

function refreshAllUnreadBadges() {
  refreshUnreadCount()
  refreshMessageUnreadCount()
}

function startUnreadPolling() {
  stopUnreadPolling()
  refreshAllUnreadBadges()
  unreadTimer = setInterval(refreshAllUnreadBadges, 30000)
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
    if (loggedIn) {
      startUnreadPolling()
      userStore.refreshPoints()
    } else {
      stopUnreadPolling()
      unreadCount.value = 0
      messageUnreadCount.value = 0
    }
  },
  { immediate: true }
)

watch(
  () => route.path,
  (path) => {
    closeMobileMenu()
    if (path === '/notifications' || path === '/orders') refreshUnreadCount()
    if (path === '/messages') refreshMessageUnreadCount()
  }
)

onMounted(() => {
  window.addEventListener(unreadRefreshEvent, refreshUnreadCount)
  window.addEventListener(messageUnreadRefreshEvent, refreshMessageUnreadCount)
  if (userStore.isLogin) {
    startUnreadPolling()
    userStore.refreshProfile()
  }
})

onUnmounted(() => {
  window.removeEventListener(unreadRefreshEvent, refreshUnreadCount)
  window.removeEventListener(messageUnreadRefreshEvent, refreshMessageUnreadCount)
  stopUnreadPolling()
})

function handleCommand(command) {
  closeMobileMenu()
  if (command === 'my') router.push('/my')
  else if (command === 'events') router.push('/events')
  else if (command === 'admin') router.push('/admin')
  else if (command === 'logout') {
    ElMessageBox.confirm(t('settings.logoutConfirm'), t('settings.tip'), {
      confirmButtonText: t('settings.confirm'),
      cancelButtonText: t('settings.cancel'),
      type: 'warning'
    })
      .then(() => {
        userStore.logout()
        ElMessage.success(t('settings.logoutSuccess'))
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
  --oa-sidebar-width: 240px;
  --mobile-topbar-h: 52px;
  --mobile-tabbar-h: 56px;
  transition: --oa-sidebar-width 0.2s ease;
}

.platform-layout.sidebar-collapsed {
  --oa-sidebar-width: 68px;
}

.mobile-topbar,
.mobile-backdrop,
.mobile-tabbar,
.mobile-only {
  display: none;
}

.desktop-only {
  display: inline-flex;
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
  transition: width 0.2s ease, transform 0.22s ease;
}

.project-selector {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 14px;
  margin: 8px 8px 4px;
  border-radius: var(--oa-radius-sm);
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

.sidebar-toggle {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--oa-text-muted);
  cursor: pointer;
  flex-shrink: 0;
  transition: background 0.15s, color 0.15s;
}

.sidebar-toggle:hover {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
}

.sidebar-nav {
  flex: 1;
  overflow-y: auto;
  padding: 4px 8px 12px;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.nav-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 9px 12px;
  border-radius: var(--oa-radius-sm);
  color: var(--oa-text-secondary);
  font-size: 13.5px;
  font-weight: 450;
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
  border: none;
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
  color: var(--oa-on-primary);
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  transition: opacity 0.15s;
}

.promo-btn:hover {
  opacity: 0.88;
}

.sidebar-footer {
  padding: 8px;
  border-top: 1px solid var(--oa-border-subtle);
}

.points-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 6px 10px;
  margin-bottom: 4px;
  border-radius: var(--oa-radius-sm);
  cursor: pointer;
  transition: background 0.15s;
}

.points-row:hover {
  background: var(--oa-bg-hover);
}

.points-label {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.points-value {
  font-size: 13px;
  font-weight: 600;
  color: var(--oa-text);
  font-variant-numeric: tabular-nums;
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
  transition: margin-left 0.2s ease;
  width: 100%;
  max-width: 100%;
  overflow-x: hidden;
}

.main-content--bleed {
  padding-top: 0;
  background: var(--oa-bg-sidebar);
}

.auth-layout {
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--oa-bg);
}

/* Collapsed sidebar */
.sidebar--collapsed .project-name,
.sidebar--collapsed .nav-label,
.sidebar--collapsed .sidebar-promo,
.sidebar--collapsed .points-row,
.sidebar--collapsed .user-info,
.sidebar--collapsed .nav-badge {
  display: none;
}

.sidebar--collapsed .project-selector {
  justify-content: center;
  padding: 12px 8px;
  gap: 0;
  position: relative;
}

.sidebar--collapsed .project-icon {
  display: none;
}

.sidebar--collapsed .sidebar-toggle {
  margin: 0 auto;
}

.sidebar--collapsed .nav-item {
  justify-content: center;
  padding: 10px;
}

.sidebar--collapsed .nav-icon {
  margin: 0;
}

.sidebar--collapsed .user-block {
  justify-content: center;
  padding: 8px;
}

.sidebar--collapsed .sidebar-footer {
  display: flex;
  flex-direction: column;
  align-items: center;
}

/* ---- Mobile shell ---- */
@media (max-width: 768px) {
  .desktop-only {
    display: none !important;
  }

  .mobile-only {
    display: inline-flex;
  }

  .mobile-topbar {
    display: flex;
    align-items: center;
    gap: 8px;
    position: fixed;
    top: 0;
    left: 0;
    right: 0;
    height: calc(var(--mobile-topbar-h) + env(safe-area-inset-top, 0px));
    padding: env(safe-area-inset-top, 0px) 10px 0;
    background: var(--oa-bg-sidebar);
    border-bottom: 1px solid var(--oa-border-subtle);
    z-index: 90;
  }

  .mobile-icon-btn {
    position: relative;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 40px;
    height: 40px;
    border: none;
    border-radius: 10px;
    background: transparent;
    color: var(--oa-text);
    cursor: pointer;
    flex-shrink: 0;
  }

  .mobile-brand {
    flex: 1;
    display: flex;
    align-items: center;
    gap: 8px;
    min-width: 0;
    cursor: pointer;
  }

  .mobile-brand-logo {
    width: 24px;
    height: 24px;
    border-radius: 6px;
    object-fit: cover;
  }

  .mobile-brand-name {
    font-size: 15px;
    font-weight: 600;
    color: var(--oa-text);
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
  }

  .mobile-dot {
    position: absolute;
    top: 4px;
    right: 2px;
    min-width: 16px;
    height: 16px;
    padding: 0 4px;
    border-radius: 999px;
    background: #ef4444;
    color: #fff;
    font-size: 10px;
    line-height: 16px;
    text-align: center;
    font-weight: 600;
  }

  .mobile-dot--tab {
    top: -4px;
    right: -10px;
  }

  .mobile-backdrop {
    display: block;
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.42);
    z-index: 110;
  }

  .sidebar {
    width: min(82vw, 300px);
    max-width: 300px;
    transform: translateX(-105%);
    z-index: 120;
    border-right: 1px solid var(--oa-border-subtle);
    box-shadow: none;
    padding-bottom: env(safe-area-inset-bottom, 0px);
  }

  .sidebar--mobile-open {
    transform: translateX(0);
    box-shadow: 8px 0 28px rgba(0, 0, 0, 0.18);
  }

  /* 手机端忽略桌面折叠态，始终展示完整标签 */
  .sidebar--collapsed .project-name,
  .sidebar--collapsed .nav-label,
  .sidebar--collapsed .sidebar-promo,
  .sidebar--collapsed .points-row,
  .sidebar--collapsed .user-info,
  .sidebar--collapsed .nav-badge,
  .sidebar--collapsed .project-icon {
    display: initial;
  }

  .sidebar--collapsed .project-icon {
    display: block;
  }

  .sidebar--collapsed .project-selector {
    justify-content: flex-start;
    padding: 12px 14px;
    gap: 10px;
  }

  .sidebar--collapsed .nav-item {
    justify-content: flex-start;
    padding: 9px 12px;
  }

  .sidebar--collapsed .user-block {
    justify-content: flex-start;
    padding: 8px 10px;
  }

  .sidebar--collapsed .sidebar-footer {
    align-items: stretch;
  }

  .main-content {
    margin-left: 0;
    min-height: 100%;
    padding: calc(var(--mobile-topbar-h) + env(safe-area-inset-top, 0px) + 16px)
      16px
      calc(var(--mobile-tabbar-h) + env(safe-area-inset-bottom, 0px) + 20px);
  }

  .main-content--bleed {
    padding-left: 0;
    padding-right: 0;
  }

  .mobile-tabbar {
    display: flex;
    position: fixed;
    left: 0;
    right: 0;
    bottom: 0;
    height: calc(var(--mobile-tabbar-h) + env(safe-area-inset-bottom, 0px));
    padding: 0 4px env(safe-area-inset-bottom, 0px);
    background: var(--oa-bg-sidebar);
    border-top: 1px solid var(--oa-border-subtle);
    z-index: 90;
  }

  .mobile-tab {
    flex: 1;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    gap: 2px;
    color: var(--oa-text-muted);
    text-decoration: none;
    min-width: 0;
    padding: 6px 2px;
  }

  .mobile-tab.active {
    color: var(--oa-text);
  }

  .mobile-tab-icon-wrap {
    position: relative;
    display: inline-flex;
  }

  .mobile-tab-label {
    font-size: 10px;
    line-height: 1.2;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    max-width: 100%;
  }
}
</style>
