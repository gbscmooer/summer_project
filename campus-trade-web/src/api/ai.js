import request from './request'

// 自然语言找商品：body { query, pageSize }
export function searchProductsByAi(data) {
  return request({
    url: '/ai/search',
    method: 'post',
    data,
    timeout: 60000
  })
}

// 根据已上传商品图片生成待确认的发布草稿：body { images, notes }
export function createAiListingDraft(data) {
  return request({
    url: '/ai/listing-draft',
    method: 'post',
    data,
    timeout: 90000
  })
}
