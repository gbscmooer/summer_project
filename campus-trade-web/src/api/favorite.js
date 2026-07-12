import request from './request'

/** 切换收藏（需登录），返回 { favorited, favoriteCount } */
export function toggleFavorite(productId) {
  return request({
    url: `/product/${productId}/favorite/toggle`,
    method: 'post'
  })
}

/** 查询收藏状态（需登录），返回 { favorited, favoriteCount } */
export function getFavoriteStatus(productId) {
  return request({
    url: `/product/${productId}/favorite/status`,
    method: 'get'
  })
}

/** 我的收藏分页列表（需登录） */
export function listMyFavorites(params) {
  return request({
    url: '/product/favorites/mine',
    method: 'get',
    params
  })
}
