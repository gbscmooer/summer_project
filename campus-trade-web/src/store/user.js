import { defineStore } from 'pinia'

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
    }
  },

  actions: {
    // 登录成功后写入 token + 用户信息，并持久化
    setLoginInfo({ token, userId, nickname, avatar, role, onboardingCompleted }) {
      this.token = token || ''
      this.userInfo = {
        userId,
        nickname,
        avatar,
        role: role == null ? 0 : Number(role),
        onboardingCompleted: onboardingCompleted == null ? 0 : Number(onboardingCompleted)
      }
      localStorage.setItem('token', this.token)
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
    },

    // 合并更新用户信息（如个人中心拉取到完整资料后）
    setUserInfo(info) {
      this.userInfo = { ...(this.userInfo || {}), ...info }
      if (this.userInfo.role != null) {
        this.userInfo.role = Number(this.userInfo.role)
      }
      localStorage.setItem('userInfo', JSON.stringify(this.userInfo))
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
