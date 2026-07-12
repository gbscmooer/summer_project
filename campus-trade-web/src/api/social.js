import request from './request'

// 好友 / 私信接口，路径前缀 /api/user

export function requestFriend({ peerUserId }) {
  return request({
    url: '/user/friends/request',
    method: 'post',
    data: { peerUserId }
  })
}

export function acceptFriendRequest(id) {
  return request({
    url: `/user/friends/request/${id}/accept`,
    method: 'post'
  })
}

export function rejectFriendRequest(id) {
  return request({
    url: `/user/friends/request/${id}/reject`,
    method: 'post'
  })
}

export function listFriendRequests() {
  return request({
    url: '/user/friends/requests',
    method: 'get'
  })
}

export function listFriends() {
  return request({
    url: '/user/friends/list',
    method: 'get'
  })
}

export function getFriendStatus(peerUserId) {
  return request({
    url: '/user/friends/status',
    method: 'get',
    params: { peerUserId }
  })
}

export function openConversation({ peerUserId }) {
  return request({
    url: '/user/conversations',
    method: 'post',
    data: { peerUserId }
  })
}

export function listConversations() {
  return request({
    url: '/user/conversations',
    method: 'get'
  })
}

export function listMessages(id, params) {
  return request({
    url: `/user/conversations/${id}/messages`,
    method: 'get',
    params
  })
}

export function sendMessage(id, { content }) {
  return request({
    url: `/user/conversations/${id}/messages`,
    method: 'post',
    data: { content }
  })
}

export function readConversation(id) {
  return request({
    url: `/user/conversations/${id}/read`,
    method: 'post'
  })
}

export function getMessageUnreadCount() {
  return request({
    url: '/user/messages/unread-count',
    method: 'get'
  })
}
