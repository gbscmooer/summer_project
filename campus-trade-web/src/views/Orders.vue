<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">Orders</h1>
    </div>

    <div class="oa-panel">
      <!-- Tab pills -->
      <div class="oa-filter-pills tab-pills">
        <button
          class="oa-filter-pill"
          :class="{ active: activeTab === 'buyer' }"
          @click="switchTab('buyer')"
        >
          Purchases
        </button>
        <button
          class="oa-filter-pill"
          :class="{ active: activeTab === 'seller' }"
          @click="switchTab('seller')"
        >
          Sales
        </button>
      </div>

      <!-- 状态筛选 -->
      <div class="oa-filter-pills">
        <button
          v-for="s in statusOptions"
          :key="s.value"
          class="oa-filter-pill"
          :class="{ active: statusFilter === s.value }"
          @click="setStatus(s.value)"
        >
          {{ s.label }}
        </button>
      </div>

      <div v-loading="loading">
        <div v-if="!loading && list.length === 0" class="oa-empty-state">
          <p>{{ activeTab === 'buyer' ? 'No purchases yet' : 'No sales yet' }}</p>
          <el-button v-if="activeTab === 'buyer'" type="primary" @click="$router.push('/')">
            Browse marketplace
          </el-button>
        </div>

        <div v-else class="order-list">
          <div v-for="order in list" :key="order.orderId" class="oa-list-item order-item">
            <div class="order-row">
              <div class="order-main">
                <div class="order-title-line">
                  <span class="order-title">{{ order.productTitle }}</span>
                  <span class="oa-status" :class="statusClass(order.status)">
                    {{ order.statusText }}
                  </span>
                </div>
                <div class="order-meta">
                  <span class="oa-price">{{ formatPoints(order.price) }}</span>
                  <span class="oa-meta">
                    {{ activeTab === 'buyer' ? 'Seller' : 'Buyer' }}:
                    {{ order.counterpartNickname || '—' }}
                  </span>
                  <span class="oa-meta">#{{ order.orderNo }}</span>
                  <span class="oa-meta">{{ formatTime(order.createTime) }}</span>
                </div>
              </div>
              <div class="order-actions">
                <el-button size="small" @click="openDetail(order.orderId)">Details</el-button>
                <template v-if="activeTab === 'buyer'">
                  <template v-if="order.status === 0">
                    <el-button
                      size="small"
                      type="primary"
                      :loading="actingId === order.orderId"
                      @click="onPay(order)"
                    >
                      Pay
                    </el-button>
                    <el-button
                      size="small"
                      :loading="actingId === order.orderId"
                      @click="onCancel(order)"
                    >
                      Cancel
                    </el-button>
                  </template>
                  <el-button
                    v-else-if="order.status === 1"
                    size="small"
                    type="primary"
                    :loading="actingId === order.orderId"
                    @click="onConfirm(order)"
                  >
                    Confirm
                  </el-button>
                </template>
              </div>
            </div>
          </div>
        </div>

        <div v-if="total > 0" class="oa-pagination">
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
      </div>
    </div>

    <el-dialog v-model="detailVisible" title="Order details" width="520px">
      <div v-loading="detailLoading">
        <el-empty v-if="!detailLoading && !orderDetail" description="Unable to load order" />
        <el-descriptions v-else-if="orderDetail" :column="1" border>
          <el-descriptions-item label="Order No">{{ orderDetail.orderNo }}</el-descriptions-item>
          <el-descriptions-item label="Product">{{ orderDetail.productTitle }}</el-descriptions-item>
          <el-descriptions-item label="Price">{{ formatPoints(orderDetail.price) }}</el-descriptions-item>
          <el-descriptions-item label="Status">{{ orderDetail.statusText }}</el-descriptions-item>
          <el-descriptions-item label="Buyer">{{ orderDetail.buyerNickname || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Seller">{{ orderDetail.sellerNickname || '—' }}</el-descriptions-item>
          <el-descriptions-item label="Created">{{ formatTime(orderDetail.createTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBuyerOrders,
  getSellerOrders,
  getOrderDetail,
  payOrder,
  confirmOrder,
  cancelOrder
} from '@/api/order'
import { useOnboarding } from '@/composables/useOnboarding'
import { useI18n } from '@/i18n'

const onboarding = useOnboarding()
const { t } = useI18n()

const statusOptions = [
  { label: 'All', value: 'all' },
  { label: 'Pending', value: 0 },
  { label: 'Paid', value: 1 },
  { label: 'Completed', value: 2 },
  { label: 'Cancelled', value: 3 }
]

const activeTab = ref('buyer')
const statusFilter = ref('all')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const orderDetail = ref(null)
const actingId = ref(null)

function statusClass(status) {
  const map = { 0: 'oa-status-warning', 1: 'oa-status-primary', 2: 'oa-status-success', 3: 'oa-status-info' }
  return map[status] || 'oa-status-info'
}

function formatPoints(price) {
  const n = Number(price)
  const value = Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(0)) : String(price ?? 0)
  return `${value} ${t('common.pointsUnit')}`
}

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
  if (statusFilter.value !== 'all') params.status = statusFilter.value
  try {
    const res =
      activeTab.value === 'buyer'
        ? await getBuyerOrders(params)
        : await getSellerOrders(params)
    list.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function switchTab(tab) {
  activeTab.value = tab
  pageNum.value = 1
  total.value = 0
  list.value = []
  statusFilter.value = 'all'
  fetchOrders()
}

function setStatus(val) {
  statusFilter.value = val
  pageNum.value = 1
  fetchOrders()
}

async function openDetail(orderId) {
  detailVisible.value = true
  detailLoading.value = true
  orderDetail.value = null
  try {
    const res = await getOrderDetail(orderId)
    orderDetail.value = res.data
  } catch {
    orderDetail.value = null
  } finally {
    detailLoading.value = false
  }
}

function onSizeChange() {
  pageNum.value = 1
  fetchOrders()
}

function onPay(order) {
  ElMessageBox.confirm(`Confirm payment for "${order.productTitle}"?`, 'Payment', {
    confirmButtonText: 'Pay',
    cancelButtonText: 'Cancel',
    type: 'warning'
  })
    .then(async () => {
      actingId.value = order.orderId
      try {
        await payOrder(order.orderId)
        ElMessage.success('Payment successful')
        await fetchOrders()
        await onboarding.refresh()
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function onConfirm(order) {
  ElMessageBox.confirm(`Confirm receipt of "${order.productTitle}"?`, 'Confirm receipt', {
    confirmButtonText: 'Confirm',
    cancelButtonText: 'Cancel',
    type: 'warning'
  })
    .then(async () => {
      actingId.value = order.orderId
      try {
        await confirmOrder(order.orderId)
        ElMessage.success('Receipt confirmed')
        await fetchOrders()
        await onboarding.refresh()
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function onCancel(order) {
  ElMessageBox.confirm(`Cancel order "${order.productTitle}"?`, 'Cancel order', {
    confirmButtonText: 'Cancel order',
    cancelButtonText: 'Keep',
    type: 'warning'
  })
    .then(async () => {
      actingId.value = order.orderId
      try {
        await cancelOrder(order.orderId)
        ElMessage.success('Order cancelled')
        await fetchOrders()
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

onMounted(fetchOrders)
</script>

<style scoped>
.tab-pills {
  margin-bottom: 12px;
}

.order-list {
  display: flex;
  flex-direction: column;
}

.order-item {
  cursor: default;
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
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
}

.order-meta {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
}

.order-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
</style>
