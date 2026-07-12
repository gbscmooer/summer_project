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
            <span class="price-label">{{ t('common.points') }}</span>
            <span class="price">{{ formatPoints(detail.price) }}</span>
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
              <span class="meta-value">{{ sellerText }}</span>
            </div>
            <div class="meta-item">
              <span class="meta-label">{{ t('orders.sellerRating') }}</span>
              <button
                type="button"
                class="meta-link"
                :disabled="!detail.sellerId"
                @click="scrollToSellerReviews"
              >
                {{ sellerRatingText }}
              </button>
            </div>
            <div class="meta-item">
              <span class="meta-label">Views</span>
              <span class="meta-value">{{ detail.viewCount }}</span>
            </div>
            <div v-if="userStore.isLogin" class="meta-item">
              <span class="meta-label">{{ t('favorites.countLabel') }}</span>
              <span class="meta-value">{{ favoriteCount }}</span>
            </div>
          </div>

          <div class="buy-box">
            <el-button
              size="large"
              :type="favorited ? 'warning' : 'default'"
              :loading="favoriteLoading"
              @click="onToggleFavorite"
            >
              <el-icon class="fav-icon"><StarFilled v-if="favorited" /><Star v-else /></el-icon>
              {{ favorited ? t('favorites.remove') : t('favorites.add') }}
            </el-button>
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
            <el-button
              v-if="canContactSeller"
              size="large"
              @click="onContactSeller"
            >
              {{ t('messages.contactSeller') }}
            </el-button>
          </div>
        </div>
      </div>

      <!-- 描述 -->
      <div class="oa-panel desc-panel">
        <h3 class="oa-section-title">Description</h3>
        <p class="description">{{ detail.description || 'No description provided.' }}</p>
      </div>

      <!-- 卖家交易评价 -->
      <div id="seller-reviews" class="oa-panel reviews-panel">
        <h3 class="oa-section-title">
          {{ t('orders.sellerReviewsTitle') }}
          <span v-if="reviewTotal > 0" class="section-count">({{ reviewTotal }})</span>
        </h3>
        <div v-loading="reviewsLoading" class="review-list">
          <el-empty
            v-if="!reviewsLoading && reviews.length === 0"
            :description="t('orders.sellerReviewsEmpty')"
          />
          <div v-for="item in reviews" :key="item.reviewId" class="review-item">
            <div class="review-meta">
              <span class="review-author">
                {{ item.buyerNickname || `${t('orders.reviewBuyer')} #${item.buyerId}` }}
              </span>
              <el-rate :model-value="item.rating || 0" disabled />
              <span class="review-time">{{ formatCommentTime(item.createTime) }}</span>
            </div>
            <p class="review-content">{{ item.content || '—' }}</p>
          </div>
        </div>
        <div v-if="reviewTotal > reviews.length" class="oa-pagination">
          <el-button :loading="reviewsLoading" @click="loadMoreReviews">
            {{ t('orders.loadMoreReviews') }}
          </el-button>
        </div>
      </div>

      <!-- 留言 -->
      <div class="oa-panel comments-panel">
        <h3 class="oa-section-title">Comments ({{ commentTotal }})</h3>

        <div v-if="userStore.isLogin" class="comment-form">
          <el-input
            v-model="commentText"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            placeholder="Ask a question or leave a message for the seller..."
          />
          <el-button
            type="primary"
            :loading="commentSubmitting"
            :disabled="!commentText.trim()"
            @click="onPostComment"
          >
            Post comment
          </el-button>
        </div>
        <p v-else class="comment-login-hint">
          <router-link :to="{ path: '/login', query: { redirect: route.fullPath } }">Sign in</router-link>
          to leave a comment.
        </p>

        <div v-loading="commentsLoading" class="comment-list">
          <el-empty v-if="!commentsLoading && comments.length === 0" description="No comments yet" />
          <div v-for="item in comments" :key="item.commentId" class="comment-item">
            <div class="comment-meta">
              <span class="comment-author">{{ commentAuthor(item) }}</span>
              <span class="comment-time">{{ formatCommentTime(item.createTime) }}</span>
            </div>
            <p class="comment-content">{{ item.content }}</p>
          </div>
        </div>

        <div v-if="commentTotal > comments.length" class="oa-pagination">
          <el-button :loading="commentsLoading" @click="loadMoreComments">Load more</el-button>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, ShoppingCart, ArrowLeft, Star, StarFilled } from '@element-plus/icons-vue'
import { getProductDetail, listProductComments, postProductComment } from '@/api/product'
import { getFavoriteStatus, toggleFavorite } from '@/api/favorite'
import { createOrder, seckillOrder, getSeckillResult, listSellerReviews } from '@/api/order'
import { useUserStore } from '@/store/user'
import { getStatusText } from '@/constants/product'
import { useOnboarding } from '@/composables/useOnboarding'
import { useI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const onboarding = useOnboarding()
const { t } = useI18n()
const loading = ref(false)
const detail = ref(null)
const buying = ref(false)
const seckilling = ref(false)
const comments = ref([])
const commentTotal = ref(0)
const commentPageNum = ref(1)
const commentPageSize = ref(10)
const commentsLoading = ref(false)
const commentSubmitting = ref(false)
const commentText = ref('')
const favorited = ref(false)
const favoriteCount = ref(0)
const favoriteLoading = ref(false)
const reviews = ref([])
const reviewTotal = ref(0)
const reviewPageNum = ref(1)
const reviewPageSize = ref(10)
const reviewsLoading = ref(false)

const images = computed(() => {
  if (!detail.value || !Array.isArray(detail.value.images)) return []
  return detail.value.images.filter(Boolean)
})

const sellerText = computed(() => {
  if (!detail.value) return '-'
  return detail.value.sellerNickname || (detail.value.sellerId ? `用户 #${detail.value.sellerId}` : '-')
})

const sellerRatingText = computed(() => {
  if (!detail.value) return '-'
  const count = detail.value.sellerReviewCount
  const avg = detail.value.sellerAvgRating
  if (count == null || Number(count) <= 0 || avg == null) {
    return t('orders.noRating')
  }
  const avgText = Number(avg).toFixed(1)
  const countText = String(t('orders.reviewsCount')).replace('{n}', String(count))
  return `${avgText} · ${countText}`
})

const canContactSeller = computed(() => {
  if (!userStore.isLogin || !detail.value?.sellerId) return false
  return detail.value.sellerId !== userStore.userInfo?.userId
})

function onContactSeller() {
  if (!canContactSeller.value) return
  router.push({ path: '/messages', query: { peerUserId: String(detail.value.sellerId) } })
}

function scrollToSellerReviews() {
  const el = document.getElementById('seller-reviews')
  if (el) el.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function fetchSellerReviews(reset = false) {
  const sellerId = detail.value?.sellerId
  if (!sellerId) {
    reviews.value = []
    reviewTotal.value = 0
    return
  }
  if (reset) {
    reviewPageNum.value = 1
    reviews.value = []
  }
  reviewsLoading.value = true
  try {
    const res = await listSellerReviews(sellerId, {
      pageNum: reviewPageNum.value,
      pageSize: reviewPageSize.value
    })
    const list = res.data?.list || []
    reviewTotal.value = Number(res.data?.total) || 0
    reviews.value = reset ? list : [...reviews.value, ...list]
  } catch {
    if (reset) {
      reviews.value = []
      reviewTotal.value = 0
    }
  } finally {
    reviewsLoading.value = false
  }
}

function loadMoreReviews() {
  reviewPageNum.value += 1
  fetchSellerReviews(false)
}

function statusClass(status) {
  const map = { 0: 'oa-status-info', 1: 'oa-status-success', 2: 'oa-status-danger' }
  return map[status] || 'oa-status-info'
}

function formatPoints(price) {
  const n = Number(price)
  const value = Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(0)) : String(price ?? 0)
  return `${value} ${t('common.pointsUnit')}`
}

async function fetchDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  favorited.value = false
  favoriteCount.value = 0
  try {
    const res = await getProductDetail(id)
    detail.value = res.data
    commentPageNum.value = 1
    comments.value = []
    reviewPageNum.value = 1
    reviews.value = []
    await Promise.all([fetchComments(true), fetchFavoriteStatus(), fetchSellerReviews(true)])
  } catch {
    detail.value = null
  } finally {
    loading.value = false
  }
}

async function fetchFavoriteStatus() {
  const productId = detail.value?.productId || route.params.id
  if (!productId || !userStore.isLogin) {
    favorited.value = false
    return
  }
  try {
    const res = await getFavoriteStatus(productId)
    favorited.value = !!res.data?.favorited
    favoriteCount.value = Number(res.data?.favoriteCount) || 0
  } catch {
    favorited.value = false
  }
}

async function onToggleFavorite() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const productId = detail.value?.productId
  if (!productId || favoriteLoading.value) return
  favoriteLoading.value = true
  try {
    const res = await toggleFavorite(productId)
    favorited.value = !!res.data?.favorited
    favoriteCount.value = Number(res.data?.favoriteCount) || 0
    ElMessage.success(favorited.value ? t('favorites.addedTip') : t('favorites.removedTip'))
  } finally {
    favoriteLoading.value = false
  }
}

async function fetchComments(reset = false) {
  const productId = detail.value?.productId || route.params.id
  if (!productId) return
  if (reset) {
    commentPageNum.value = 1
    comments.value = []
  }
  commentsLoading.value = true
  try {
    const res = await listProductComments(productId, {
      pageNum: commentPageNum.value,
      pageSize: commentPageSize.value
    })
    const page = res.data || {}
    commentTotal.value = page.total || 0
    const list = page.list || []
    if (reset) {
      comments.value = list
    } else {
      comments.value = [...comments.value, ...list]
    }
  } catch {
    if (reset) {
      comments.value = []
      commentTotal.value = 0
    }
  } finally {
    commentsLoading.value = false
  }
}

function loadMoreComments() {
  if (commentsLoading.value || comments.value.length >= commentTotal.value) return
  commentPageNum.value += 1
  fetchComments(false)
}

function commentAuthor(item) {
  if (item.nickname) return item.nickname
  if (item.userId) return `User #${item.userId}`
  return 'Anonymous'
}

function formatCommentTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

async function onPostComment() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return
  }
  const content = commentText.value.trim()
  if (!content) return
  const productId = detail.value?.productId
  if (!productId) return
  commentSubmitting.value = true
  try {
    await postProductComment(productId, { content })
    ElMessage.success('Comment posted')
    commentText.value = ''
    await fetchComments(true)
  } finally {
    commentSubmitting.value = false
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
    await onboarding.refresh()
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
  border: none;
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
  border: none;
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

.meta-link {
  border: none;
  background: transparent;
  padding: 0;
  font-size: 13px;
  color: var(--el-color-primary);
  cursor: pointer;
  text-align: right;
}

.meta-link:disabled {
  color: var(--oa-text);
  cursor: default;
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

.fav-icon {
  margin-right: 6px;
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

.reviews-panel {
  margin-top: 20px;
}

.section-count {
  font-weight: 400;
  color: var(--oa-text-secondary);
}

.review-list {
  min-height: 64px;
}

.review-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.review-item:last-child {
  border-bottom: none;
}

.review-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 6px;
}

.review-author {
  font-size: 13px;
  font-weight: 500;
  color: var(--oa-text);
}

.review-time {
  margin-left: auto;
  font-size: 12px;
  color: var(--oa-text-muted, var(--oa-text-secondary));
}

.review-content {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--oa-text-secondary);
  white-space: pre-wrap;
}

.comments-panel {
  margin-top: 20px;
}

.comment-form {
  display: flex;
  flex-direction: column;
  gap: 12px;
  margin-bottom: 20px;
}

.comment-form .el-button {
  align-self: flex-end;
}

.comment-login-hint {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.comment-login-hint a {
  color: var(--el-color-primary);
  text-decoration: none;
}

.comment-list {
  min-height: 80px;
}

.comment-item {
  padding: 14px 0;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.comment-item:last-child {
  border-bottom: none;
}

.comment-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 6px;
}

.comment-author {
  font-size: 13px;
  font-weight: 500;
  color: var(--oa-text);
}

.comment-time {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.comment-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

.comments-panel .oa-pagination {
  margin-top: 12px;
  display: flex;
  justify-content: center;
}

@media (max-width: 900px) {
  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
