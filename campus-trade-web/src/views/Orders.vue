<template>
  <div class="page-container">
    <el-card class="orders-card">
      <el-tabs v-model="activeTab" @tab-change="onTabChange">
        <!-- 我买到的 -->
        <el-tab-pane label="我买到的" name="buyer">
          <div v-loading="loading">
            <el-empty v-if="!loading && list.length === 0" description="还没有买到的订单">
              <el-button type="primary" @click="$router.push('/')">去逛逛</el-button>
            </el-empty>

            <div v-else class="order-list">
              <el-card
                v-for="order in list"
                :key="order.orderId"
                class="order-item"
                shadow="hover"
              >
                <div class="order-row">
                  <div class="order-main">
                    <div class="order-title-line">
                      <span class="order-title">{{ order.productTitle }}</span>
                      <el-tag size="small" :type="statusType(order.status)">
                        {{ order.statusText }}
                      </el-tag>
                    </div>
                    <div class="order-meta">
                      <span class="price">¥{{ formatPrice(order.price) }}</span>
                      <span class="meta-item">卖家：{{ order.counterpartNickname || '—' }}</span>
                      <span class="meta-item">订单号：{{ order.orderNo }}</span>
                      <span class="meta-item">{{ formatTime(order.createTime) }}</span>
                    </div>
                  </div>
                  <!-- 买家操作：按状态显示 -->
                  <div class="order-actions">
                    <template v-if="order.status === 0">
                      <el-button
                        size="small"
                        type="danger"
                        :loading="actingId === order.orderId"
                        @click="onPay(order)"
                      >
                        支付
                      </el-button>
                      <el-button
                        size="small"
                        :loading="actingId === order.orderId"
                        @click="onCancel(order)"
                      >
                        取消
                      </el-button>
                    </template>
                    <template v-else-if="order.status === 1">
                      <el-button
                        size="small"
                        type="primary"
                        :loading="actingId === order.orderId"
                        @click="onConfirm(order)"
                      >
                        确认收货
                      </el-button>
                    </template>
                    <span v-else class="no-action">—</span>
                  </div>
                </div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>

        <!-- 我卖出的（只读） -->
        <el-tab-pane label="我卖出的" name="seller">
          <div v-loading="loading">
            <el-empty v-if="!loading && list.length === 0" description="还没有卖出的订单" />

            <div v-else class="order-list">
              <el-card
                v-for="order in list"
                :key="order.orderId"
                class="order-item"
                shadow="hover"
              >
                <div class="order-row">
                  <div class="order-main">
                    <div class="order-title-line">
                      <span class="order-title">{{ order.productTitle }}</span>
                      <el-tag size="small" :type="statusType(order.status)">
                        {{ order.statusText }}
                      </el-tag>
                    </div>
                    <div class="order-meta">
                      <span class="price">¥{{ formatPrice(order.price) }}</span>
                      <span class="meta-item">买家：{{ order.counterpartNickname || '—' }}</span>
                      <span class="meta-item">订单号：{{ order.orderNo }}</span>
                      <span class="meta-item">{{ formatTime(order.createTime) }}</span>
                    </div>
                  </div>
                </div>
              </el-card>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>

      <!-- 分页 -->
      <div v-if="total > 0" class="pagination-wrapper">
        <el-pagination
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 30]"
          layout="total, sizes, prev, pager, next"
          background
          @current-change="fetchOrders"
          @size-change="onSizeChange"
        />
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBuyerOrders,
  getSellerOrders,
  payOrder,
  confirmOrder,
  cancelOrder
} from '@/api/order'

// 订单状态映射：0 待付款 / 1 已付款 / 2 已完成 / 3 已取消
const ORDER_STATUS_TYPE = {
  0: 'warning',
  1: 'primary',
  2: 'success',
  3: 'info'
}

const activeTab = ref('buyer')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
// 正在操作的订单 id（用于按钮 loading，避免整列表禁用）
const actingId = ref(null)

function statusType(status) {
  return ORDER_STATUS_TYPE[status] || 'info'
}

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

// 后端 LocalDateTime 序列化为 ISO 字符串，这里转为本地可读格式
function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function fetchOrders() {
  loading.value = true
  const params = { pageNum: pageNum.value, pageSize: pageSize.value }
  try {
    const res =
      activeTab.value === 'buyer'
        ? await getBuyerOrders(params)
        : await getSellerOrders(params)
    list.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onTabChange() {
  // 切 Tab 重置分页并重新拉取
  pageNum.value = 1
  total.value = 0
  list.value = []
  fetchOrders()
}

function onSizeChange() {
  pageNum.value = 1
  fetchOrders()
}

function onPay(order) {
  ElMessageBox.confirm(`确认支付订单「${order.productTitle}」？`, '支付确认', {
    confirmButtonText: '支付',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      actingId.value = order.orderId
      try {
        await payOrder(order.orderId)
        ElMessage.success('支付成功')
        await fetchOrders()
      } catch (e) {
        // 错误提示已由拦截器处理
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function onConfirm(order) {
  ElMessageBox.confirm(`确认已收到「${order.productTitle}」？`, '确认收货', {
    confirmButtonText: '确认收货',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      actingId.value = order.orderId
      try {
        await confirmOrder(order.orderId)
        ElMessage.success('确认收货成功')
        await fetchOrders()
      } catch (e) {
        // 错误提示已由拦截器处理
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function onCancel(order) {
  ElMessageBox.confirm(`确定取消订单「${order.productTitle}」？`, '取消订单', {
    confirmButtonText: '取消订单',
    cancelButtonText: '再想想',
    type: 'warning'
  })
    .then(async () => {
      actingId.value = order.orderId
      try {
        await cancelOrder(order.orderId)
        ElMessage.success('已取消')
        await fetchOrders()
      } catch (e) {
        // 错误提示已由拦截器处理
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

onMounted(fetchOrders)
</script>

<style scoped>
.orders-card {
  min-height: 400px;
}

.order-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.order-item {
  border-radius: 8px;
}

.order-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}

.order-main {
  flex: 1;
  min-width: 240px;
}

.order-title-line {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.order-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.order-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  color: #909399;
  font-size: 13px;
}

.price {
  color: #f56c6c;
  font-size: 16px;
  font-weight: 700;
}

.meta-item {
  white-space: nowrap;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.no-action {
  color: #c0c4cc;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
