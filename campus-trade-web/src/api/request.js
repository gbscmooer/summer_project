import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import router from '@/router'

// 创建 axios 实例：统一走 /api 前缀，由 Vite dev proxy（或 P4 网关）转发
const request = axios.create({
  baseURL: '/api',
  timeout: 10000
})

// ----------------------------------------------------------------------------
// 请求拦截器：自动注入 Token
// ----------------------------------------------------------------------------
request.interceptors.request.use(
  (config) => {
    // 优先从 Pinia 取，兜底从 localStorage 取（避免 store 尚未初始化的边界情况）
    let token = ''
    try {
      token = useUserStore().token
    } catch (e) {
      token = ''
    }
    if (!token) {
      token = localStorage.getItem('token') || ''
    }
    if (token) {
      config.headers['Authorization'] = `Bearer ${token}`
    }
    return config
  },
  (error) => Promise.reject(error)
)

// ----------------------------------------------------------------------------
// 响应拦截器：统一处理后端信封 { code, message, data }
// ----------------------------------------------------------------------------
request.interceptors.response.use(
  (response) => {
    const res = response.data
    // 业务成功：返回完整信封，方便调用方同时取 message 与 data
    if (res.code === 200) {
      return res
    }
    // 401：登录态失效，清理并跳登录页
    if (res.code === 401) {
      handleUnauthorized()
      return Promise.reject(new Error(res.message || '登录已过期'))
    }
    // 其它业务错误：弹出后端 message
    ElMessage.error(res.message || '请求失败')
    return Promise.reject(new Error(res.message || '请求失败'))
  },
  (error) => {
    // HTTP 层错误
    const status = error.response && error.response.status
    if (status === 401) {
      handleUnauthorized()
      return Promise.reject(error)
    }
    // 优先展示后端返回的 message，否则展示通用网络错误
    const msg =
      (error.response && error.response.data && error.response.data.message) ||
      error.message ||
      '网络异常，请稍后重试'
    ElMessage.error(msg)
    return Promise.reject(error)
  }
)

// 统一处理未授权：清 token + 提示 + 跳登录
function handleUnauthorized() {
  try {
    useUserStore().logout()
  } catch (e) {
    localStorage.removeItem('token')
    localStorage.removeItem('userInfo')
  }
  ElMessage.error('登录已过期，请重新登录')
  // 避免在登录页重复跳转
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

export default request
