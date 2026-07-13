<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ t('orders.title') }}</h1>
    </div>

    <div class="oa-panel">
      <!-- Tab pills -->
      <div class="oa-filter-pills tab-pills">
        <button
          class="oa-filter-pill"
          :class="{ active: activeTab === 'buyer' }"
          @click="switchTab('buyer')"
        >
          {{ t('orders.purchases') }}
        </button>
        <button
          class="oa-filter-pill"
          :class="{ active: activeTab === 'seller' }"
          @click="switchTab('seller')"
        >
          {{ t('orders.sales') }}
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
          <p>{{ activeTab === 'buyer' ? t('orders.emptyPurchases') : t('orders.emptySales') }}</p>
          <el-button v-if="activeTab === 'buyer'" type="primary" @click="$router.push('/')">
            {{ t('orders.browseMarketplace') }}
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
                    {{ activeTab === 'buyer' ? t('orders.seller') : t('orders.buyer') }}:
                    {{ order.counterpartNickname || '—' }}
                  </span>
                  <span class="oa-meta">#{{ order.orderNo }}</span>
                  <span class="oa-meta">{{ formatTime(order.createTime) }}</span>
                </div>
              </div>
              <div class="order-actions">
                <el-button size="small" @click="openDetail(order.orderId)">{{ t('orders.details') }}</el-button>
                <template v-if="activeTab === 'buyer'">
                  <template v-if="order.status === 0">
                    <el-button
                      size="small"
                      type="primary"
                      :loading="actingId === order.orderId"
                      @click="onPay(order)"
                    >
                      {{ t('orders.pay') }}
                    </el-button>
                    <el-button
                      size="small"
                      :loading="actingId === order.orderId"
                      @click="onCancel(order)"
                    >
                      {{ t('orders.cancel') }}
                    </el-button>
                  </template>
                  <el-button
                    v-else-if="order.status === 1"
                    size="small"
                    type="primary"
                    :loading="actingId === order.orderId"
                    @click="onConfirm(order)"
                  >
                    {{ t('orders.confirm') }}
                  </el-button>
                  <el-button
                    v-else-if="order.status === 2"
                    size="small"
                    type="warning"
                    @click="openReview(order)"
                  >
                    {{ t('orders.review') }}
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

    <el-dialog v-model="detailVisible" :title="t('orders.detailTitle')" width="520px">
      <div v-loading="detailLoading">
        <el-empty v-if="!detailLoading && !orderDetail" :description="t('orders.loadFailed')" />
        <el-descriptions v-else-if="orderDetail" :column="1" border>
          <el-descriptions-item :label="t('orders.orderNo')">{{ orderDetail.orderNo }}</el-descriptions-item>
          <el-descriptions-item :label="t('orders.product')">{{ orderDetail.productTitle }}</el-descriptions-item>
          <el-descriptions-item :label="t('orders.price')">{{ formatPoints(orderDetail.price) }}</el-descriptions-item>
          <el-descriptions-item :label="t('orders.status')">{{ orderDetail.statusText }}</el-descriptions-item>
          <el-descriptions-item :label="t('orders.buyer')">{{ orderDetail.buyerNickname || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('orders.seller')">{{ orderDetail.sellerNickname || '—' }}</el-descriptions-item>
          <el-descriptions-item :label="t('orders.created')">{{ formatTime(orderDetail.createTime) }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-dialog>

    <el-dialog
      v-model="reviewVisible"
      :title="t('orders.reviewTitle')"
      width="480px"
      @closed="resetReviewForm"
    >
      <div v-loading="reviewLoading">
        <p v-if="reviewOrder" class="review-product">{{ reviewOrder.productTitle }}</p>
        <p class="review-hint">{{ t('orders.within7Days') }}</p>
        <div v-if="existingReview" class="review-readonly">
          <div class="review-row">
            <span class="review-label">{{ t('orders.rating') }}</span>
            <el-rate :model-value="existingReview.rating" disabled />
          </div>
          <div class="review-row">
            <span class="review-label">{{ t('orders.content') }}</span>
            <p class="review-content">{{ existingReview.content || '—' }}</p>
          </div>
          <p class="review-meta">{{ t('orders.alreadyReviewed') }} · {{ formatTime(existingReview.createTime) }}</p>
        </div>
        <div v-else class="review-form">
          <div class="review-row">
            <span class="review-label">{{ t('orders.rating') }}</span>
            <el-rate v-model="reviewRating" :max="5" />
          </div>
          <div class="review-row">
            <span class="review-label">{{ t('orders.content') }}</span>
            <el-input
              v-model="reviewContent"
              type="textarea"
              :rows="3"
              maxlength="500"
              show-word-limit
              :placeholder="t('orders.contentPlaceholder')"
            />
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="reviewVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button
          v-if="!existingReview"
          type="primary"
          :loading="reviewSubmitting"
          :disabled="!reviewRating"
          @click="onSubmitReview"
        >
          {{ t('orders.submit') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getBuyerOrders,
  getSellerOrders,
  getOrderDetail,
  payOrder,
  confirmOrder,
  cancelOrder,
  getOrderReview,
  submitOrderReview
} from '@/api/order'
import { useOnboarding } from '@/composables/useOnboarding'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'

const onboarding = useOnboarding()
const userStore = useUserStore()
const { t } = useI18n()

const statusOptions = computed(() => [
  { label: t('orders.statusAll'), value: 'all' },
  { label: t('orders.statusPending'), value: 0 },
  { label: t('orders.statusPaid'), value: 1 },
  { label: t('orders.statusCompleted'), value: 2 },
  { label: t('orders.statusCancelled'), value: 3 }
])

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

const reviewVisible = ref(false)
const reviewLoading = ref(false)
const reviewSubmitting = ref(false)
const reviewOrder = ref(null)
const existingReview = ref(null)
const reviewRating = ref(0)
const reviewContent = ref('')

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
  ElMessageBox.confirm(
    t('orders.payConfirm').replace('{title}', order.productTitle),
    t('orders.payDialogTitle'),
    {
      confirmButtonText: t('orders.pay'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    }
  )
    .then(async () => {
      actingId.value = order.orderId
      try {
        await payOrder(order.orderId)
        ElMessage.success(t('orders.paySuccess'))
        await fetchOrders()
        await userStore.refreshPoints()
        await onboarding.refresh()
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function onConfirm(order) {
  ElMessageBox.confirm(
    t('orders.confirmReceiptMsg').replace('{title}', order.productTitle),
    t('orders.confirmReceiptTitle'),
    {
      confirmButtonText: t('orders.confirm'),
      cancelButtonText: t('common.cancel'),
      type: 'warning'
    }
  )
    .then(async () => {
      actingId.value = order.orderId
      try {
        await confirmOrder(order.orderId)
        ElMessage.success(t('orders.confirmReceiptSuccess'))
        await fetchOrders()
        await onboarding.refresh()
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function onCancel(order) {
  ElMessageBox.confirm(
    t('orders.cancelConfirm').replace('{title}', order.productTitle),
    t('orders.cancelTitle'),
    {
      confirmButtonText: t('orders.cancelOrderBtn'),
      cancelButtonText: t('orders.keepOrder'),
      type: 'warning'
    }
  )
    .then(async () => {
      actingId.value = order.orderId
      try {
        await cancelOrder(order.orderId)
        ElMessage.success(t('orders.cancelSuccess'))
        await fetchOrders()
      } finally {
        actingId.value = null
      }
    })
    .catch(() => {})
}

function resetReviewForm() {
  reviewOrder.value = null
  existingReview.value = null
  reviewRating.value = 0
  reviewContent.value = ''
  reviewLoading.value = false
  reviewSubmitting.value = false
}

async function openReview(order) {
  reviewOrder.value = order
  reviewVisible.value = true
  reviewLoading.value = true
  existingReview.value = null
  reviewRating.value = 0
  reviewContent.value = ''
  try {
    const res = await getOrderReview(order.orderId)
    if (res.data) {
      existingReview.value = res.data
    }
  } catch {
    existingReview.value = null
  } finally {
    reviewLoading.value = false
  }
}

async function onSubmitReview() {
  if (!reviewOrder.value || !reviewRating.value) return
  reviewSubmitting.value = true
  try {
    await submitOrderReview(reviewOrder.value.orderId, {
      rating: reviewRating.value,
      content: reviewContent.value?.trim() || undefined
    })
    ElMessage.success(t('orders.submitSuccess'))
    reviewVisible.value = false
    await fetchOrders()
  } catch {
    // request 拦截器已提示业务错误
  } finally {
    reviewSubmitting.value = false
  }
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
  gap: 8px;
  flex-wrap: wrap;
}

.order-title {
  font-weight: 600;
  color: var(--oa-text);
}

.order-meta {
  margin-top: 6px;
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  font-size: 13px;
}

.order-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.review-product {
  font-weight: 600;
  margin: 0 0 8px;
}

.review-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--oa-text-muted);
}

.review-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
  align-items: flex-start;
}

.review-label {
  width: 72px;
  flex-shrink: 0;
  padding-top: 4px;
  color: var(--oa-text-muted);
  font-size: 13px;
}

.review-content {
  margin: 0;
  white-space: pre-wrap;
}

.review-meta {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--oa-text-muted);
}

.review-form .review-row {
  align-items: center;
}

.review-form .review-label {
  padding-top: 0;
}
</style>
