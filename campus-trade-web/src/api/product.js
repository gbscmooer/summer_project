import request from './request'

// 商品相关接口封装，路径前缀 /api/product

// 发布商品（需登录）：body { title, description, price, images, category, stock }
// 注意：images 为逗号分隔的 URL 字符串。返回 data { productId }
export function createProduct(data) {
  return request({
    url: '/product',
    method: 'post',
    data
  })
}

// 更新商品（需登录）：body 同发布 -> data null
export function updateProduct(id, data) {
  return request({
    url: `/product/${id}/update`,
    method: 'post',
    data
  })
}

// 删除/逻辑下架商品（需登录）-> data null
export function deleteProduct(id) {
  return request({
    url: `/product/${id}/delete`,
    method: 'post'
  })
}

// 商品详情（无需登录）
// -> data { productId, title, description, price, images:[], category,
//           sellerId, sellerNickname, status, stock, viewCount, createTime }
export function getProductDetail(id) {
  return request({
    url: `/product/${id}`,
    method: 'get'
  })
}

// 商品列表（无需登录）：params { pageNum, pageSize, category? }
// -> data { total, pageNum, pageSize, list:[{ productId, title, price, cover, category, status, createTime }] }
export function getProductList(params) {
  return request({
    url: '/product/list',
    method: 'get',
    params
  })
}

// 我发布的商品（需登录）：params { pageNum, pageSize } -> 同 list 结构
export function getMyProducts(params) {
  return request({
    url: '/product/my',
    method: 'get',
    params
  })
}

// 搜索商品（走 ES 检索，无需登录）：params { pageNum, pageSize, keyword?, category?, minPrice?, maxPrice?, sort? }
export function searchProducts(params) {
  return request({
    url: '/product/search',
    method: 'get',
    params
  })
}
