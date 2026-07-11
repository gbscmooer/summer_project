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
