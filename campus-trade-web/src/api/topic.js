import request from './request'

// 话题帖子列表（无需登录）
export function listTopicPosts(params) {
  return request({
    url: '/topic/posts/list',
    method: 'get',
    params
  })
}

// 话题帖子详情（无需登录）
export function getTopicPost(id) {
  return request({
    url: `/topic/posts/${id}`,
    method: 'get'
  })
}

// 发布帖子（需登录）
export function createTopicPost(data) {
  return request({
    url: '/topic/posts',
    method: 'post',
    data
  })
}

// 删除自己的帖子（需登录）
export function deleteTopicPost(id) {
  return request({
    url: `/topic/posts/${id}/delete`,
    method: 'post'
  })
}
