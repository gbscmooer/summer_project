import request from './request'

// 用户相关接口封装，路径前缀 /api/user

// 注册：body { username, password, nickname, phone } -> data { userId }
export function register(data) {
  return request({
    url: '/user/register',
    method: 'post',
    data
  })
}

// 登录：body { username, password } -> data { token, userId, nickname, avatar }
export function login(data) {
  return request({
    url: '/user/login',
    method: 'post',
    data
  })
}

// 获取当前用户信息（需登录）-> data { userId, username, nickname, avatar, phone, createTime }
export function getUserInfo() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

// 更新用户信息（需登录）：body { nickname, avatar, phone } -> data null
export function updateUserInfo(data) {
  return request({
    url: '/user/info',
    method: 'post',
    data
  })
}

// 新手教程状态（需登录）
export function getOnboardingStatus() {
  return request({
    url: '/user/onboarding',
    method: 'get'
  })
}

// 标记新手教程步骤（browse / ai / notify / profile）
export function markOnboardingStep(step) {
  return request({
    url: '/user/onboarding/step',
    method: 'post',
    data: { step }
  })
}

// 完成/关闭新手教程
export function completeOnboarding() {
  return request({
    url: '/user/onboarding/complete',
    method: 'post'
  })
}

// 申请成为商家
export function applyMerchant(data) {
  return request({
    url: '/user/merchant/apply',
    method: 'post',
    data
  })
}

// 查询我的商家申请状态
export function getMyMerchantApplication() {
  return request({
    url: '/user/merchant/application',
    method: 'get'
  })
}
