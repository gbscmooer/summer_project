// 商品分类（固定可选）
export const CATEGORIES = ['教材', '数码', '生活', '其他']

// 商品状态映射：0 下架 / 1 在售 / 2 已售
export const STATUS_MAP = {
  0: { text: '已下架', type: 'info' },
  1: { text: '在售', type: 'success' },
  2: { text: '已售', type: 'warning' }
}

export function getStatusText(status) {
  const item = STATUS_MAP[status]
  return item ? item.text : '未知'
}

export function getStatusType(status) {
  const item = STATUS_MAP[status]
  return item ? item.type : 'info'
}
