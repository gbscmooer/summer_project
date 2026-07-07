<template>
  <div class="page-container" v-loading="loading">
    <button class="oa-back-btn" @click="$router.back()">
      <el-icon><ArrowLeft /></el-icon>
      Back
    </button>

    <el-empty v-if="!loading && !detail" description="Listing not found or delisted" />

    <template v-if="detail">
      <h1 class="page-title listing-title">{{ detail.title }}</h1>

      <div class="detail-grid">
        <!-- 左侧图片 -->
        <div class="oa-panel gallery-panel">
          <el-carousel
            v-if="images.length > 0"
            :autoplay="false"
            height="400px"
            indicator-position="outside"
            :arrow="images.length > 1 ? 'hover' : 'never'"
          >
            <el-carousel-item v-for="(img, idx) in images" :key="idx">
              <el-image :src="img" fit="contain" class="gallery-img">
                <template #error>
                  <div class="gallery-placeholder">
                    <el-icon :size="48"><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </el-carousel-item>
          </el-carousel>
          <div v-else class="gallery-placeholder gallery-empty">
            <el-icon :size="48"><Picture /></el-icon>
          </div>
        </div>

        <!-- 右侧信息 -->
        <div class="info-panel">
          <div class="price-card">
            <span class="price-label">Price</span>
            <span class="price">¥{{ formatPrice(detail.price) }}</span>
          </div>

          <div class="meta-grid">
            <div class="meta-item">
              <span class="meta-label">Category</span>
              <span class="meta-value">{{ detail.category }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Status</span>
              <span class="oa-status" :class="statusClass(detail.status)">
                {{ getStatusText(detail.status) }}
              </span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Stock</span>
              <span class="meta-value">{{ detail.stock }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Seller</span>
              <span class="meta-value">{{ detail.sellerNickname }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">Views</span>
              <span class="meta-value">{{ detail.viewCount }}</span>
            </div>
          </div>

          <div class="buy-box">
            <el-button
              type="primary"
              size="large"
              :icon="ShoppingCart"
              :loading="buying"
              :disabled="detail.status !== 1 || seckilling"
              @click="onBuy"
            >
              {{ detail.status === 1 ? 'Buy now' : 'Unavailable' }}
            </el-button>
            <el-button
              size="large"
              :loading="seckilling"
              :disabled="detail.status !== 1 || buying"
              @click="onSeckill"
            >
              {{ seckilling ? 'Queuing...' : 'Flash sale' }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 描述 -->
      <div class="oa-panel desc-panel">
        <h3 class="oa-section-title">Description</h3>
        <p class="description">{{ detail.description || 'No description provided.' }}</p>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, ShoppingCart, ArrowLeft } from '@element-plus/icons-vue'
import { getProductDetail } from '@/api/product'
import { createOrder, seckillOrder, getSeckillResult } from '@/api/order'
import { useUserStore } from '@/store/user'
import { getStatusText } from '@/constants/product'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const loading = ref(false)
const detail = ref(null)
const buying = ref(false)
const seckilling = ref(false)

const images = computed(() => {
  if (!detail.value || !Array.isArray(detail.value.images)) return []
  return detail.value.images.filter(Boolean)
})

function statusClass(status) {
  const map = { 0: 'oa-status-info', 1: 'oa-status-success', 2: 'oa-status-danger' }
  return map[status] || 'oa-status-info'
}

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

async function fetchDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getProductDetail(id)
    detail.value = res.data
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function onBuy() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (buying.value) return
  const productId = detail.value?.productId
  if (!productId) return
  buying.value = true
  try {
    const res = await createOrder({ productId })
    ElMessage.success(res.message || 'Order placed')
    router.push('/orders')
  } finally {
    buying.value = false
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function pollSeckillResult(productId, maxAttempts = 30) {
  for (let i = 0; i < maxAttempts; i++) {
    await sleep(1000)
    const res = await getSeckillResult(productId)
    const status = res.data?.status
    const orderNo = res.data?.orderNo
    if (status === 0) continue
    if (status === 1) return { success: true, orderNo }
    return { success: false }
  }
  return { success: false, timeout: true }
}

async function onSeckill() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  if (seckilling.value) return
  const productId = detail.value?.productId
  if (!productId) return
  seckilling.value = true
  try {
    const res = await seckillOrder({ productId })
    ElMessage.info(res.message || 'Queuing, please wait')
    const result = await pollSeckillResult(productId)
    if (result.success) {
      ElMessage.success(`Flash sale success, order ${result.orderNo}`)
      router.push('/orders')
    } else if (result.timeout) {
      ElMessage.warning('Still queuing, check Orders later')
    } else {
      ElMessage.error('Flash sale failed, try again')
    }
  } finally {
    seckilling.value = false
  }
}

watch(() => route.params.id, fetchDetail)
onMounted(fetchDetail)
</script>

<style scoped>
.listing-title {
  margin-bottom: 24px;
  font-size: 24px;
}

.detail-grid {
  display: grid;
  grid-template-columns: 1fr 360px;
  gap: 20px;
  margin-bottom: 20px;
}

.gallery-panel {
  padding: 0;
  overflow: hidden;
}

.gallery-img {
  width: 100%;
  height: 100%;
  background: var(--oa-bg-elevated);
}

.gallery-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oa-text-muted);
  background: var(--oa-bg-elevated);
}

.gallery-empty {
  height: 400px;
}

.info-panel {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.price-card {
  background: var(--oa-bg-sidebar);
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  padding: 20px 24px;
}

.price-label {
  display: block;
  font-size: 12px;
  color: var(--oa-text-secondary);
  margin-bottom: 4px;
}

.price {
  font-size: 32px;
  font-weight: 500;
  color: var(--oa-text);
  letter-spacing: -0.02em;
}

.meta-grid {
  background: var(--oa-bg-sidebar);
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  padding: 4px 0;
}

.meta-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 12px 20px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.meta-item:last-child {
  border-bottom: none;
}

.meta-label {
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.meta-value {
  font-size: 13px;
  color: var(--oa-text);
}

.buy-box {
  display: flex;
  flex-direction: column;
  gap: 10px;
  margin-top: auto;
}

.buy-box .el-button {
  width: 100%;
  height: 44px;
}

.desc-panel {
  margin-top: 0;
}

.description {
  white-space: pre-wrap;
  line-height: 1.7;
  color: var(--oa-text-secondary);
  font-size: 14px;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
