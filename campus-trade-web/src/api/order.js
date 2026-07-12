import request from './request'

// 订单相关接口封装，路径前缀 /api/order
// 买家身份由网关从 token 解析后注入请求头 X-User-Id，前端无需手动传

// 下单：body { productId }
// -> data { orderId, orderNo, productId, productTitle, price, status }
export function createOrder(data) {
  return request({
    url: '/order',
    method: 'post',
    data
  })
}

// 订单详情（需登录，仅买卖家可见）
// -> data { orderId, orderNo, productId, productTitle, price, buyerId,
//           buyerNickname, sellerId, sellerNickname, status, statusText, createTime }
export function getOrderDetail(id) {
  return request({
    url: `/order/${id}`,
    method: 'get'
  })
}

// 我买到的（需登录）：params { status?, pageNum, pageSize }
// -> data { total, pageNum, pageSize, list:[{ orderId, orderNo, productId,
//           productTitle, price, status, statusText, createTime, counterpartNickname }] }
export function getBuyerOrders(params) {
  return request({
    url: '/order/buyer',
    method: 'get',
    params
  })
}

// 我卖出的（需登录）：params 同上 -> 同结构
export function getSellerOrders(params) {
  return request({
    url: '/order/seller',
    method: 'get',
    params
  })
}

// 支付（仅买家本人，0->1）-> data null
export function payOrder(id) {
  return request({
    url: `/order/${id}/pay`,
    method: 'post'
  })
}

// 确认收货（仅买家本人，1->2）-> data null
export function confirmOrder(id) {
  return request({
    url: `/order/${id}/confirm`,
    method: 'post'
  })
}

// 取消（仅买家本人，0->3，后端回滚库存）-> data null
export function cancelOrder(id) {
  return request({
    url: `/order/${id}/cancel`,
    method: 'post'
  })
}

// 秒杀下单：body { productId } -> data { queueId }
export function seckillOrder(data) {
  return request({
    url: '/order/seckill',
    method: 'post',
    data
  })
}

// 查询秒杀结果 -> data { status, orderNo }；status: 0 排队中 / 1 成功 / -1 失败
export function getSeckillResult(productId) {
  return request({
    url: `/order/seckill/result/${productId}`,
    method: 'get'
  })
}

// 商家收入统计（需商家角色）
export function getSellerIncomeStats() {
  return request({
    url: '/order/seller/stats',
    method: 'get'
  })
}

// 商家收入仪表盘（需商家角色）
export function getSellerDashboard() {
  return request({
    url: '/order/seller/dashboard',
    method: 'get'
  })
}

// 提交交易评价：body { rating: 1-5, content? } -> data { reviewId }
export function submitOrderReview(orderId, data) {
  return request({
    url: `/order/${orderId}/review`,
    method: 'post',
    data
  })
}

// 查询订单评价（买卖家可见；未评价时 data 为 null）
export function getOrderReview(orderId) {
  return request({
    url: `/order/${orderId}/review`,
    method: 'get'
  })
}

// 卖家评价列表（公开）：params { pageNum, pageSize }
export function listSellerReviews(sellerId, params) {
  return request({
    url: `/order/reviews/seller/${sellerId}`,
    method: 'get',
    params
  })
}
