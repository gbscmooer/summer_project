import request from './request'

export function listTopicPosts(params) {
  return request({
    url: '/topic/posts/list',
    method: 'get',
    params
  })
}

export function listTrendingTopics(params) {
  return request({
    url: '/topic/posts/trending',
    method: 'get',
    params
  })
}

export function getFeed(params) {
  return request({
    url: '/topic/posts/feed',
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

export function upvoteTopicPost(id) {
  return request({
    url: `/topic/posts/${id}/upvote`,
    method: 'post'
  })
}

export function tipTopicPost(id, data) {
  return request({
    url: `/topic/posts/${id}/tip`,
    method: 'post',
    data
  })
}

export function listTopicComments(postId) {
  return request({
    url: `/topic/posts/${postId}/comments`,
    method: 'get'
  })
}

export function createTopicComment(postId, data) {
  return request({
    url: `/topic/posts/${postId}/comments`,
    method: 'post',
    data
  })
}

export function upvoteTopicComment(commentId) {
  return request({
    url: `/topic/comments/${commentId}/upvote`,
    method: 'post'
  })
}
