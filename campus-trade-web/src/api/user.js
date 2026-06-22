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
    method: 'put',
    data
  })
}
