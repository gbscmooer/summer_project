import request from './request'

// 管理员读取 AI 配置（Key 已脱敏）
export function getAiAdminConfig() {
  return request({
    url: '/admin/ai-config',
    method: 'get'
  })
}

// 管理员保存 AI 配置；apiKey 传空字符串表示保留原 Key
export function saveAiAdminConfig(data) {
  return request({
    url: '/admin/ai-config',
    method: 'post',
    data
  })
}

// 探测 AI API 连通性
export function probeAiAdminConfig() {
  return request({
    url: '/admin/ai-config/probe',
    method: 'post'
  })
}

// 管理员发送系统通知（全员或指定用户）；特殊认证角色亦可调用
export function sendAdminNotification(data) {
  return request({
    url: '/order/admin/notification/broadcast',
    method: 'post',
    data
  })
}

// 管理员：待审核商家申请列表
export function listMerchantApplications() {
  return request({
    url: '/user/admin/merchant/applications',
    method: 'get'
  })
}

// 管理员：通过商家申请
export function approveMerchantApplication(id, data = {}) {
  return request({
    url: `/user/admin/merchant/applications/${id}/approve`,
    method: 'post',
    data
  })
}

// 管理员：拒绝商家申请
export function rejectMerchantApplication(id, data = {}) {
  return request({
    url: `/user/admin/merchant/applications/${id}/reject`,
    method: 'post',
    data
  })
}

// 管理员：待审核特殊认证申请列表
export function listSpecialCertApplications() {
  return request({
    url: '/user/admin/special-cert/applications',
    method: 'get'
  })
}

// 管理员：通过特殊认证申请
export function approveSpecialCertApplication(id, data = {}) {
  return request({
    url: `/user/admin/special-cert/applications/${id}/approve`,
    method: 'post',
    data
  })
}

// 管理员：拒绝特殊认证申请
export function rejectSpecialCertApplication(id, data = {}) {
  return request({
    url: `/user/admin/special-cert/applications/${id}/reject`,
    method: 'post',
    data
  })
}

// 管理员：用户列表（分页）
export function listAdminUsers(params) {
  return request({
    url: '/user/admin/users/list',
    method: 'get',
    params
  })
}

// 管理员：封禁用户（reason 必填，durationDays 0 表示永久）
export function banUser(userId, data) {
  return request({
    url: `/user/admin/users/${userId}/ban`,
    method: 'post',
    data
  })
}

// 管理员：解封用户
export function unbanUser(userId) {
  return request({
    url: `/user/admin/users/${userId}/unban`,
    method: 'post'
  })
}

// 管理员：更新用户细粒度权限
export function updateUserPermissions(userId, data) {
  return request({
    url: `/user/admin/users/${userId}/permissions`,
    method: 'put',
    data
  })
}
