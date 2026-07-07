<template>
  <div class="home-page">
    <!-- 页头 -->
    <div class="home-header">
      <h1 class="home-title">Home</h1>
      <div class="time-pills">
        <button
          v-for="t in timeRanges"
          :key="t"
          class="time-pill"
          :class="{ active: activeRange === t }"
          @click="activeRange = t"
        >
          {{ t }}
        </button>
      </div>
    </div>

    <div class="home-grid">
      <!-- 左栏主内容 -->
      <div class="home-main">
        <!-- Get started -->
        <section class="section">
          <h2 class="section-title">Get started</h2>
          <div class="get-started-row">
            <div class="checklist">
              <div
                v-for="(step, i) in getStartedSteps"
                :key="i"
                class="check-item"
                :class="{ done: step.done }"
              >
                <span class="check-num">{{ i + 1 }}.</span>
                <span class="check-icon" :class="{ done: step.done }">
                  <el-icon v-if="step.done"><Check /></el-icon>
                </span>
                <span class="check-label">{{ step.label }}</span>
              </div>
            </div>

            <!-- 右侧两张 promo 卡 -->
            <div class="promo-cards">
              <div class="promo-card" @click="$router.push('/publish')">
                <div class="promo-card-bg promo-bg-1" />
                <span class="promo-card-title">快速发布指南</span>
                <el-icon class="promo-arrow"><ArrowRight /></el-icon>
              </div>
              <div class="promo-card" @click="handleSearch">
                <div class="promo-card-bg promo-bg-2" />
                <span class="promo-card-title">浏览热门商品</span>
                <el-icon class="promo-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </section>

        <!-- 统计卡片行 -->
        <section class="stats-row">
          <div class="stat-card">
            <div class="stat-head">
              <span class="stat-label">Total listings</span>
              <el-icon class="stat-info"><InfoFilled /></el-icon>
            </div>
            <div class="stat-value">{{ stats.totalListings }}</div>
            <svg class="stat-chart" viewBox="0 0 200 40" preserveAspectRatio="none">
              <polyline
                fill="none"
                stroke="#ef4444"
                stroke-width="1.5"
                points="0,35 25,30 50,32 75,20 100,22 125,15 150,18 175,10 200,12"
              />
            </svg>
          </div>

          <div class="stat-card">
            <div class="stat-head">
              <span class="stat-label">Orders &amp; trades</span>
            </div>
            <div class="stat-value">{{ stats.totalOrders }}</div>
            <svg class="stat-chart" viewBox="0 0 200 40" preserveAspectRatio="none">
              <polyline
                fill="none"
                stroke="#6b7280"
                stroke-width="1.5"
                stroke-dasharray="4 3"
                points="0,30 40,28 80,32 120,25 160,28 200,20"
              />
            </svg>
          </div>

          <div class="stat-card stat-card-yellow">
            <div class="stat-head">
              <span class="stat-label dark">Pending orders</span>
            </div>
            <div class="stat-value dark">{{ stats.pendingOrders }}</div>
            <button class="yellow-btn" @click="$router.push('/orders')">View orders</button>
          </div>

          <div class="stat-card">
            <div class="stat-head">
              <span class="stat-label">Total views</span>
            </div>
            <div class="stat-value">{{ stats.totalViews }}</div>
            <svg class="stat-chart" viewBox="0 0 200 40" preserveAspectRatio="none">
              <polyline
                fill="none"
                stroke="#22c55e"
                stroke-width="1.5"
                points="0,38 30,35 60,30 90,25 120,20 150,15 180,10 200,8"
              />
            </svg>
          </div>
        </section>

        <!-- Safety insights 横幅 -->
        <section class="insights-banner">
          <button class="dismiss-btn" @click="showBanner = false">Dismiss</button>
          <div v-if="showBanner" class="banner-inner">
            <div class="banner-text">
              <h3>Introducing safety insights</h3>
              <p>校园淘现已支持交易安全提醒，保障每一笔闲置交易。</p>
            </div>
            <button class="banner-btn">Learn more</button>
          </div>
        </section>

        <!-- Recommended -->
        <section class="section">
          <h2 class="section-title">Recommended listings</h2>
          <div v-loading="loading" class="recommended-grid">
            <div
              v-for="item in recommendedList"
              :key="item.productId"
              class="model-card"
              @click="goDetail(item.productId)"
            >
              <div class="model-cover">
                <el-image :src="item.cover" fit="cover" class="model-img">
                  <template #error>
                    <div class="model-placeholder"><el-icon :size="28"><Picture /></el-icon></div>
                  </template>
                </el-image>
              </div>
              <div class="model-info">
                <span class="model-name">{{ item.title }}</span>
                <span class="model-price">¥{{ formatPrice(item.price) }}</span>
              </div>
            </div>
            <div v-if="!loading && recommendedList.length === 0" class="empty-hint">
              暂无推荐商品
            </div>
          </div>
        </section>

        <!-- 商品浏览区 -->
        <section class="section browse-section">
          <h2 class="section-title">Browse marketplace</h2>

          <div class="search-bar">
            <el-input
              v-model="keyword"
              placeholder="Search listings..."
              clearable
              class="oa-search"
              @keyup.enter="handleSearch"
            >
              <template #prefix>
                <el-icon><Search /></el-icon>
              </template>
            </el-input>
            <div class="filter-pills">
              <button
                v-for="cat in ['', ...categories]"
                :key="cat || 'all'"
                class="filter-pill"
                :class="{ active: activeCategory === cat }"
                @click="setCategory(cat)"
              >
                {{ cat || 'All' }}
              </button>
            </div>
          </div>

          <div v-loading="loading" class="product-grid">
            <div
              v-for="item in list"
              :key="item.productId"
              class="product-card"
              @click="goDetail(item.productId)"
            >
              <div class="product-cover">
                <el-image :src="item.cover" fit="cover" class="product-img">
                  <template #error>
                    <div class="cover-placeholder"><el-icon :size="28"><Picture /></el-icon></div>
                  </template>
                </el-image>
              </div>
              <div class="product-info">
                <div class="product-title">{{ item.title }}</div>
                <div class="product-meta">
                  <span class="product-price">¥{{ formatPrice(item.price) }}</span>
                  <span class="product-cat">{{ item.category }}</span>
                </div>
              </div>
            </div>
          </div>

          <div v-if="total > pageSize" class="pagination-row">
            <el-pagination
              v-model:current-page="pageNum"
              v-model:page-size="pageSize"
              :total="total"
              :page-sizes="[8, 16, 24]"
              layout="total, prev, pager, next"
              background
              @current-change="fetchList"
              @size-change="onSizeChange"
            />
          </div>
        </section>
      </div>

      <!-- 右栏 Updates -->
      <aside class="home-aside">
        <h2 class="aside-title">Updates</h2>
        <div class="updates-list">
          <div v-for="(u, i) in updates" :key="i" class="update-item">
            <span class="update-date">{{ u.date }}</span>
            <h4 class="update-headline">{{ u.title }}</h4>
            <p class="update-desc">{{ u.desc }}</p>
          </div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Check, ArrowRight, Search, Picture, InfoFilled } from '@element-plus/icons-vue'
import { getProductList, searchProducts } from '@/api/product'
import { CATEGORIES } from '@/constants/product'
import { useUserStore } from '@/store/user'

const router = useRouter()
const userStore = useUserStore()
const categories = CATEGORIES

const timeRanges = ['24h', '7d', '30d', '90d']
const activeRange = ref('30d')
const showBanner = ref(true)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(8)
const activeCategory = ref('')
const keyword = ref('')

const stats = ref({
  totalListings: 0,
  totalOrders: 0,
  pendingOrders: 0,
  totalViews: 0
})

const recommendedList = computed(() => list.value.slice(0, 4))

const getStartedSteps = computed(() => [
  { label: userStore.isLogin ? 'Create an account' : 'Create an account', done: userStore.isLogin },
  { label: 'Publish first listing', done: false },
  { label: 'Complete first trade', done: false }
])

const updates = [
  {
    date: '2 months ago',
    title: 'Campus Trade v2.0 — 全新交易体验',
    desc: 'Campus Trade v2.0 带来更流畅的发布流程与订单追踪，让校园闲置交易更简单。'
  },
  {
    date: '3 months ago',
    title: 'Introducing instant notifications',
    desc: '实时消息推送，订单状态变更第一时间通知买卖双方。'
  },
  {
    date: '5 months ago',
    title: 'Multi-category search',
    desc: '支持按分类、价格区间、关键词多维度搜索，快速找到心仪好物。'
  }
]

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
    if (activeCategory.value) params.category = activeCategory.value

    let res
    if (keyword.value) {
      params.keyword = keyword.value
      res = await searchProducts(params)
    } else {
      res = await getProductList(params)
    }
    list.value = res.data.list || []
    total.value = res.data.total || 0
    stats.value.totalListings = res.data.total || list.value.length
    stats.value.totalViews = Math.floor((res.data.total || 0) * 12.5)
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  pageNum.value = 1
  fetchList()
}

function setCategory(cat) {
  activeCategory.value = cat
  pageNum.value = 1
  fetchList()
}

function onSizeChange() {
  pageNum.value = 1
  fetchList()
}

function goDetail(id) {
  router.push(`/product/${id}`)
}

onMounted(fetchList)
</script>

<style scoped>
.home-page {
  max-width: 1200px;
}

.home-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 28px;
}

.home-title {
  font-size: 28px;
  font-weight: 500;
  letter-spacing: -0.02em;
}

.time-pills {
  display: flex;
  gap: 4px;
  background: var(--oa-bg-elevated);
  border: 1px solid var(--oa-border-subtle);
  border-radius: 8px;
  padding: 3px;
}

.time-pill {
  padding: 5px 12px;
  border: none;
  background: transparent;
  color: var(--oa-text-secondary);
  font-size: 13px;
  border-radius: 6px;
  cursor: pointer;
  transition: all 0.15s;
}

.time-pill:hover {
  color: var(--oa-text);
}

.time-pill.active {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
}

.home-grid {
  display: grid;
  grid-template-columns: 1fr 280px;
  gap: 40px;
  align-items: start;
}

.section {
  margin-bottom: 32px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
  margin-bottom: 16px;
}

/* Get started */
.get-started-row {
  display: flex;
  gap: 16px;
}

.checklist {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.check-item {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

.check-item.done {
  color: var(--oa-text);
}

.check-num {
  color: var(--oa-text-muted);
  min-width: 18px;
}

.check-icon {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: 1.5px solid var(--oa-border);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 11px;
}

.check-icon.done {
  background: #22c55e;
  border-color: #22c55e;
  color: #fff;
}

.promo-cards {
  display: flex;
  flex-direction: column;
  gap: 8px;
  width: 200px;
  flex-shrink: 0;
}

.promo-card {
  position: relative;
  height: 72px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  overflow: hidden;
  cursor: pointer;
  display: flex;
  align-items: flex-end;
  padding: 12px;
  transition: border-color 0.15s;
}

.promo-card:hover {
  border-color: var(--oa-border);
}

.promo-card-bg {
  position: absolute;
  inset: 0;
  opacity: 0.6;
}

.promo-bg-1 {
  background: linear-gradient(135deg, #6366f1 0%, #ec4899 50%, #f97316 100%);
}

.promo-bg-2 {
  background: linear-gradient(135deg, #06b6d4 0%, #8b5cf6 50%, #f43f5e 100%);
}

.promo-card-title {
  position: relative;
  font-size: 13px;
  font-weight: 500;
  color: #fff;
  z-index: 1;
}

.promo-arrow {
  position: absolute;
  right: 12px;
  bottom: 12px;
  color: rgba(255, 255, 255, 0.7);
  z-index: 1;
}

/* Stats */
.stats-row {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  margin-bottom: 24px;
}

.stat-card {
  background: var(--oa-bg-sidebar);
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  padding: 16px;
  min-height: 130px;
  display: flex;
  flex-direction: column;
}

.stat-card-yellow {
  background: var(--oa-yellow-card);
  border-color: #d4d0c8;
}

.stat-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 8px;
}

.stat-label {
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.stat-label.dark {
  color: #666;
}

.stat-info {
  font-size: 14px;
  color: var(--oa-text-muted);
}

.stat-value {
  font-size: 28px;
  font-weight: 500;
  color: var(--oa-text);
  margin-bottom: 8px;
}

.stat-value.dark {
  color: var(--oa-yellow-text);
}

.stat-chart {
  width: 100%;
  height: 36px;
  margin-top: auto;
}

.yellow-btn {
  margin-top: auto;
  padding: 6px 14px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  align-self: flex-start;
}

.yellow-btn:hover {
  background: #333;
}

/* Banner */
.insights-banner {
  margin-bottom: 32px;
  position: relative;
}

.dismiss-btn {
  position: absolute;
  top: 12px;
  right: 16px;
  background: transparent;
  border: none;
  color: rgba(255, 255, 255, 0.7);
  font-size: 13px;
  cursor: pointer;
  z-index: 2;
}

.banner-inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20px 24px;
  border-radius: var(--oa-radius);
  background: linear-gradient(135deg, #fce7f3 0%, #fbcfe8 40%, #e9d5ff 100%);
  gap: 16px;
}

.banner-text h3 {
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  margin-bottom: 4px;
}

.banner-text p {
  font-size: 13px;
  color: #444;
  line-height: 1.4;
}

.banner-btn {
  padding: 8px 16px;
  background: #000;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  white-space: nowrap;
  flex-shrink: 0;
}

/* Recommended */
.recommended-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  min-height: 80px;
}

.model-card {
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s;
}

.model-card:hover {
  border-color: var(--oa-border);
}

.model-cover {
  aspect-ratio: 16/10;
  background: var(--oa-bg-elevated);
}

.model-img {
  width: 100%;
  height: 100%;
}

.model-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oa-text-muted);
}

.model-info {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.model-name {
  font-size: 13px;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.model-price {
  font-size: 12px;
  color: var(--oa-text-secondary);
}

.empty-hint {
  grid-column: 1 / -1;
  text-align: center;
  color: var(--oa-text-muted);
  font-size: 13px;
  padding: 24px;
}

/* Browse */
.search-bar {
  margin-bottom: 16px;
}

.oa-search {
  margin-bottom: 12px;
}

.filter-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.filter-pill {
  padding: 5px 12px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: 20px;
  background: transparent;
  color: var(--oa-text-secondary);
  font-size: 12px;
  cursor: pointer;
  transition: all 0.15s;
}

.filter-pill:hover,
.filter-pill.active {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
  border-color: var(--oa-border);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 12px;
  min-height: 120px;
}

.product-card {
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s;
}

.product-card:hover {
  border-color: var(--oa-border);
}

.product-cover {
  aspect-ratio: 1;
  background: var(--oa-bg-elevated);
}

.product-img {
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oa-text-muted);
}

.product-info {
  padding: 10px 12px;
}

.product-title {
  font-size: 13px;
  line-height: 1.4;
  height: 36px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 6px;
}

.product-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.product-price {
  font-size: 14px;
  font-weight: 600;
}

.product-cat {
  font-size: 11px;
  color: var(--oa-text-muted);
}

.pagination-row {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

/* Aside Updates */
.home-aside {
  position: sticky;
  top: 32px;
}

.aside-title {
  font-size: 14px;
  font-weight: 500;
  margin-bottom: 16px;
}

.updates-list {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.update-item {
  padding-bottom: 20px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.update-item:last-child {
  border-bottom: none;
}

.update-date {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.update-headline {
  font-size: 13px;
  font-weight: 500;
  margin: 6px 0 4px;
  line-height: 1.4;
}

.update-desc {
  font-size: 12px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
}

@media (max-width: 1100px) {
  .home-grid {
    grid-template-columns: 1fr;
  }

  .home-aside {
    position: static;
  }

  .stats-row,
  .recommended-grid,
  .product-grid {
    grid-template-columns: repeat(2, 1fr);
  }

  .get-started-row {
    flex-direction: column;
  }

  .promo-cards {
    flex-direction: row;
    width: 100%;
  }

  .promo-card {
    flex: 1;
  }
}
</style>
