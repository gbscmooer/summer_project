import request from './request'

// 获取公开用户主页（无需登录；登录时返回是否已关注）
export function getPublicProfile(userId) {
  return request({
    url: `/user/profile/${userId}`,
    method: 'get'
  })
}

// 关注用户（需登录）
export function followUser(userId) {
  return request({
    url: `/user/follow/${userId}`,
    method: 'post'
  })
}

// 取消关注（需登录）
export function unfollowUser(userId) {
  return request({
    url: `/user/follow/${userId}/unfollow`,
    method: 'post'
  })
}
