<template>
  <div class="app-wrapper">
    <!-- 顶部导航栏 -->
    <header class="navbar">
      <div class="navbar-inner">
        <!-- Logo -->
        <div class="logo" @click="$router.push('/')">
          <span class="logo-icon">🛒</span>
          <span class="logo-text">校园淘</span>
        </div>

        <!-- 主导航菜单 -->
        <el-menu
          class="nav-menu"
          mode="horizontal"
          :ellipsis="false"
          :default-active="activeMenu"
          router
          :background-color="'transparent'"
        >
          <el-menu-item index="/">首页</el-menu-item>
          <el-menu-item index="/publish">发布</el-menu-item>
          <el-menu-item v-if="userStore.isLogin" index="/orders">我的订单</el-menu-item>
          <el-menu-item index="/my">我的</el-menu-item>
        </el-menu>

        <!-- 右侧：登录状态 -->
        <div class="nav-right">
          <template v-if="userStore.isLogin">
            <el-dropdown @command="handleCommand">
              <span class="user-entry">
                <el-icon><User /></el-icon>
                <span class="user-name">{{ userStore.displayName || '用户' }}</span>
                <el-icon><ArrowDown /></el-icon>
              </span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="my">个人中心</el-dropdown-item>
                  <el-dropdown-item command="logout" divided>退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <template v-else>
            <el-button text @click="$router.push('/login')">登录</el-button>
            <el-button type="primary" @click="$router.push('/register')">注册</el-button>
          </template>
        </div>
      </div>
    </header>

    <!-- 路由出口 -->
    <main class="app-main">
      <router-view />
    </main>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox, ElMessage } from 'element-plus'
import { User, ArrowDown } from '@element-plus/icons-vue'
import { useUserStore } from '@/store/user'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

// 当前激活菜单项：仅在这三个主菜单路径下高亮
const activeMenu = computed(() => {
  const path = route.path
  if (path === '/' || path === '/publish' || path === '/my' || path === '/orders') {
    return path
  }
  return ''
})

function handleCommand(command) {
  if (command === 'my') {
    router.push('/my')
  } else if (command === 'logout') {
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
.app-wrapper {
  min-height: 100%;
  display: flex;
  flex-direction: column;
}

.navbar {
  position: sticky;
  top: 0;
  z-index: 1000;
  background-color: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.navbar-inner {
  max-width: 1180px;
  margin: 0 auto;
  height: 60px;
  padding: 0 16px;
  display: flex;
  align-items: center;
}

.logo {
  display: flex;
  align-items: center;
  cursor: pointer;
  user-select: none;
  margin-right: 24px;
}

.logo-icon {
  font-size: 24px;
  margin-right: 6px;
}

.logo-text {
  font-size: 20px;
  font-weight: 700;
  color: #409eff;
  letter-spacing: 1px;
}

.nav-menu {
  flex: 1;
  border-bottom: none !important;
}

.nav-right {
  display: flex;
  align-items: center;
  gap: 8px;
}

.user-entry {
  display: flex;
  align-items: center;
  gap: 4px;
  cursor: pointer;
  color: #303133;
  outline: none;
  padding: 0 4px;
}

.user-name {
  max-width: 120px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.app-main {
  flex: 1;
}
</style>
