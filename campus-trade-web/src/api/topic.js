import request from './request'

export function listTopicPosts(params) {
  return request({
    url: '/topic/posts/list',
    method: 'get',
    params
  })
}

export function getTopicPost(id) {
  return request({
    url: `/topic/posts/${id}`,
    method: 'get'
  })
}

export function createTopicPost(data) {
  return request({
    url: '/topic/posts',
    method: 'post',
    data
  })
}

export function deleteTopicPost(id) {
  return request({
    url: `/topic/posts/${id}/delete`,
    method: 'post'
  })
}

export function listPostsByUser(userId, params) {
  return request({
    url: `/topic/posts/by-user/${userId}`,
    method: 'get',
    params
  })
}
