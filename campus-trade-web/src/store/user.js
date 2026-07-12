import { defineStore } from 'pinia'
import { getPoints } from '@/api/points'
import { getUserInfo } from '@/api/user'

// 从 localStorage 安全读取已持久化的用户信息
function loadUserInfo() {
  try {
    const raw = localStorage.getItem('userInfo')
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

export const useUserStore = defineStore('user', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    userInfo: loadUserInfo()
  }),

  getters: {
    // 是否已登录
    isLogin: (state) => !!state.token,
    // 0-个人账户 1-管理员 2-商家
    role: (state) => (state.userInfo && state.userInfo.role != null ? Number(state.userInfo.role) : 0),
    isAdmin: (state) => {
      const role = state.userInfo && state.userInfo.role != null ? Number(state.userInfo.role) : 0
      return role === 1
    },
    isMerchant: (state) => {
      const role = state.userInfo && state.userInfo.role != null ? Number(state.userInfo.role) : 0
      return role === 2
    },
    isPersonal: (state) => {
      const role = state.userInfo && state.userInfo.role != null ? Number(state.userInfo.role) : 0
      return role === 0
    },
    canPublish: (state) => {
      const role = state.userInfo && state.userInfo.role != null ? Number(state.userInfo.role) : 0
      return role === 1 || role === 2
    },
    roleLabel: (state) => {
      const role = state.userInfo && state.userInfo.role != null ? Number(state.userInfo.role) : 0
      if (role === 1) return '管理员'
      if (role === 2) return '商家'
      return '个人账户'
    },
    // 顶部导航展示用昵称，兜底用户名
    displayName: (state) => {
      if (!state.userInfo) return ''
      return state.userInfo.nickname || state.userInfo.username || ''
    },
    points: (state) => {
      if (!state.userInfo || state.userInfo.points == null) return 0
      const n = Number(state.userInfo.points)
      return Number.isFinite(n) ? n : 0
    }
  },

  actions: {
    // 登录成功后写入 token + 用户信息，并持久化
    setLoginInfo({ token, userId, nickname, avatar, role, onboardingCompleted, points }) {
      this.token = token || ''
      this.userInfo = {
        userId,
        nickname,
        avatar,
        role: role == null ? 0 : Number(role),
        onboardingCompleted: onboardingCompleted == null ? 0 : Number(onboardingCompleted),
        points: points == null ? 0 : Number(points)
      }
      localStorage.setItem('token', this.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },

    // 合并更新用户信息（如个人中心拉取到完整资料后）；忽略 undefined，避免冲掉 role
    setUserInfo(info) {
      if (!info || typeof info !== 'object') return
      const next = { ...(this.userInfo || {}) }
      for (const [key, value] of Object.entries(info)) {
        if (value !== undefined) next[key] = value
      }
      if (next.role != null) next.role = Number(next.role)
      if (next.points != null) next.points = Number(next.points)
      this.userInfo = next
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },

    async refreshProfile() {
      if (!this.token) return null
      try {
        const res = await getUserInfo()
        if (res.data) this.setUserInfo(res.data)
        return this.userInfo
      } catch {
        return this.userInfo
      }
    },

    async refreshPoints() {
      if (!this.token) return 0
      try {
        const res = await getPoints()
        const pts = Number(res.data?.points ?? res.data ?? 0)
        this.setUserInfo({ points: Number.isFinite(pts) ? pts : 0 })
        return this.points
      } catch {
        return this.points
      }
    },

    // 退出登录：清理内存与持久化
    logout() {
      this.token = ''
      this.userInfo = null
      localStorage.removeItem('token')
      localStorage.removeItem('userInfo')
    }
  }
})
