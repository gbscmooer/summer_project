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

// 上传1~5张商品实拍图（需登录），返回 data { images: ['/api/product/image/...'] }
export function uploadProductImages(files) {
  const formData = new FormData()
  files.forEach((file) => formData.append('files', file))
  return request({
    url: '/product/image/upload',
    method: 'post',
    data: formData,
    timeout: 60000
  })
}

// 删除本人上传的商品实拍图（需登录）
export function deleteProductImage(url) {
  return request({
    url: '/product/image/delete',
    method: 'post',
    data: { url }
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

// 新手教程专用 0 元体验商品（无需登录）
export function getTutorialProduct() {
  return request({
    url: '/product/tutorial',
    method: 'get'
  })
}

// 发布配额（需登录）
export function getPublishQuota() {
  return request({
    url: '/product/publish-quota',
    method: 'get'
  })
}

// 用户活动热力图（需登录）
export function getActivityHeatmap(params) {
  return request({
    url: '/product/activity/heatmap',
    method: 'get',
    params
  })
}

// 商家商品仪表盘（需商家角色）
export function getSellerProductDashboard() {
  return request({
    url: '/product/seller/dashboard',
    method: 'get'
  })
}

// 商品留言列表（无需登录）
export function listProductComments(productId, params) {
  return request({
    url: `/product/${productId}/comments`,
    method: 'get',
    params
  })
}

// 发表商品留言（需登录）
export function postProductComment(productId, data) {
  return request({
    url: `/product/${productId}/comments`,
    method: 'post',
    data
  })
}
