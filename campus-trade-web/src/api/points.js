import request from './request'

// 当前积分余额
export function getPoints() {
  return request({
    url: '/user/points',
    method: 'get'
  })
}

// 活动页状态：签到、点赞任务、积分摘要
export function getEventsStatus() {
  return request({
    url: '/user/events/status',
    method: 'get'
  })
}

// 每日签到
export function checkin() {
  return request({
    url: '/user/events/checkin',
    method: 'post'
  })
}

// 领取每日点赞任务奖励
export function claimLikeReward() {
  return request({
    url: '/user/events/claim-like-reward',
    method: 'post'
  })
}

// 积分流水列表
export function getPointsLedger(params) {
  return request({
    url: '/user/points/ledger',
    method: 'get',
    params
  })
}

// 积分消耗/收入统计（饼图 + 折线）
export function getPointsStats(params) {
  return request({
    url: '/user/points/stats',
    method: 'get',
    params
  })
}
