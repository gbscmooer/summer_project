import request from './request'

// 通知相关接口，路径前缀 /api/order/notification

export function getNotificationList(params) {
  return request({
    url: '/order/notification/list',
    method: 'get',
    params
  })
}

export function getUnreadCount() {
  return request({
    url: '/order/notification/unread-count',
    method: 'get'
  })
}

export function markNotificationRead(id) {
  return request({
    url: `/order/notification/${id}/read`,
    method: 'post'
  })
}

export function markAllNotificationsRead() {
  return request({
    url: '/order/notification/read-all',
    method: 'post'
  })
}
