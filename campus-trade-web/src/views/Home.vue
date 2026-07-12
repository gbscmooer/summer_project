<template>
  <div class="home-page">
    <!-- 页头 -->
    <div class="home-header">
      <h1 class="home-title">Home</h1>
    </div>

    <div class="home-grid">
      <!-- 左栏主内容 -->
      <div class="home-main">
        <!-- 新手教程（仅新注册用户可见） -->
        <section v-if="onboardingVisible" class="section onboarding-section">
          <div class="onboarding-head">
            <div>
              <h2 class="section-title">{{ t('onboarding.title') }}</h2>
              <p class="onboarding-subtitle">{{ t('onboarding.subtitle') }}</p>
            </div>
            <button type="button" class="onboarding-dismiss" @click="onboarding.dismiss">
              {{ t('onboarding.dismiss') }}
            </button>
          </div>

          <div class="onboarding-progress">
            <div class="onboarding-progress-bar">
              <div class="onboarding-progress-fill" :style="{ width: onboardingProgressPercent + '%' }" />
            </div>
            <span class="onboarding-progress-text">
              {{ t('onboarding.progress').replace('{done}', onboardingCompletedCount).replace('{total}', onboardingTotalCount) }}
            </span>
          </div>

          <div class="get-started-row">
            <div class="checklist">
              <button
                v-for="(step, i) in onboardingSteps"
                :key="step.key"
                type="button"
                class="check-item check-item-btn"
                :class="{ done: step.done, active: !step.done }"
                @click="onboarding.goToStep(step)"
              >
                <span class="check-num">{{ i + 1 }}.</span>
                <span class="check-icon" :class="{ done: step.done }">
                  <el-icon v-if="step.done"><Check /></el-icon>
                </span>
                <span class="check-label">{{ t(`onboarding.steps.${step.key}`) }}</span>
                <el-icon v-if="!step.done" class="check-go"><ArrowRight /></el-icon>
              </button>
            </div>

            <div class="promo-cards">
              <div class="promo-card" @click="$router.push('/publish')">
                <div class="promo-card-bg promo-bg-1" />
                <span class="promo-card-title">{{ t('onboarding.promoPublish') }}</span>
                <el-icon class="promo-arrow"><ArrowRight /></el-icon>
              </div>
              <div class="promo-card" @click="handleSearch">
                <div class="promo-card-bg promo-bg-2" />
                <span class="promo-card-title">{{ t('onboarding.promoBrowse') }}</span>
                <el-icon class="promo-arrow"><ArrowRight /></el-icon>
              </div>
              <div
                v-if="onboardingTutorialProductId"
                class="promo-card promo-card-tutorial"
                @click="$router.push(`/product/${onboardingTutorialProductId}`)"
              >
                <div class="promo-card-bg promo-bg-3" />
                <span class="promo-card-title">{{ t('onboarding.promoTutorial') }}</span>
                <span class="promo-card-badge">¥0 · 限购2</span>
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
          </div>
        </section>

        <!-- Safety insights 横幅 -->
        <section v-if="showBanner" class="insights-banner">
          <div class="banner-inner">
            <div class="banner-header">
              <h3 class="banner-title">Introducing safety insights</h3>
              <button type="button" class="dismiss-btn" @click="showBanner = false">关闭</button>
            </div>
            <p class="banner-desc">校园集市现已支持交易安全提醒，保障每一笔闲置交易。</p>
            <div class="banner-footer">
              <button type="button" class="banner-btn">了解更多</button>
            </div>
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
import { useI18n } from '@/i18n'
import { useOnboarding } from '@/composables/useOnboarding'

const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()
const onboarding = useOnboarding()
const onboardingVisible = onboarding.visible
const onboardingSteps = onboarding.steps
const onboardingProgressPercent = onboarding.progressPercent
const onboardingCompletedCount = onboarding.completedCount
const onboardingTotalCount = onboarding.totalCount
const onboardingTutorialProductId = onboarding.tutorialProductId
const categories = CATEGORIES

const showBanner = ref(true)

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(8)
const activeCategory = ref('')
const keyword = ref('')

const stats = ref({
  totalListings: 0
})

const recommendedList = computed(() => list.value.slice(0, 4))

const updates = [
  {
    date: '2 months ago',
    title: 'Campus Market v2.0 — 全新交易体验',
    desc: 'Campus Market v2.0 带来更流畅的发布流程与订单追踪，让校园闲置交易更简单。'
  },
  {
    date: '3 months ago',
    title: 'Introducing instant notifications',
    desc: '实时消息推送，订单状态变更第一时间通知买卖双方。'
  },
  {
    date: '5 months ago',
    title: 'Multi-category search',
    desc: '支持按分类和关键词搜索，快速找到心仪好物。'
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

onMounted(async () => {
  await fetchList()
  await onboarding.refresh()
  onboarding.trackStep('browse')
})
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

/* Get started / 新手教程 */
.onboarding-section {
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius-lg);
  padding: 20px;
  background: var(--oa-bg-elevated);
}

.onboarding-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 12px;
}

.onboarding-head .section-title {
  margin-bottom: 4px;
}

.onboarding-subtitle {
  font-size: 13px;
  color: var(--oa-text-secondary);
  margin: 0;
}

.onboarding-dismiss {
  border: none;
  background: transparent;
  color: var(--oa-text-muted);
  font-size: 13px;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: var(--oa-radius);
  flex-shrink: 0;
}

.onboarding-dismiss:hover {
  color: var(--oa-text);
  background: var(--oa-bg-hover);
}

.onboarding-progress {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.onboarding-progress-bar {
  flex: 1;
  height: 6px;
  border-radius: 999px;
  background: var(--oa-bg-hover);
  overflow: hidden;
}

.onboarding-progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #22c55e);
  border-radius: 999px;
  transition: width 0.3s ease;
}

.onboarding-progress-text {
  font-size: 12px;
  color: var(--oa-text-muted);
  white-space: nowrap;
}

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

.check-item-btn {
  width: 100%;
  border: none;
  background: transparent;
  text-align: left;
  padding: 6px 8px;
  margin: -6px -8px;
  border-radius: var(--oa-radius);
  cursor: pointer;
  transition: background 0.15s;
}

.check-item-btn:hover {
  background: var(--oa-bg-hover);
}

.check-item-btn.active .check-label {
  color: var(--oa-text);
}

.check-go {
  margin-left: auto;
  color: var(--oa-text-muted);
  font-size: 14px;
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
  width: 220px;
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

.promo-bg-3 {
  background: linear-gradient(135deg, #22c55e 0%, #14b8a6 50%, #0ea5e9 100%);
}

.promo-card-tutorial {
  height: 80px;
}

.promo-card-badge {
  position: relative;
  z-index: 1;
  display: inline-block;
  margin-top: 4px;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.9);
  background: rgba(0, 0, 0, 0.2);
  padding: 2px 6px;
  border-radius: 999px;
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
}

.banner-inner {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 18px 20px;
  border-radius: var(--oa-radius);
  background: linear-gradient(135deg, #fce7f3 0%, #fbcfe8 40%, #e9d5ff 100%);
}

.banner-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.banner-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1a1a1a;
  line-height: 1.4;
}

.dismiss-btn {
  flex-shrink: 0;
  margin-top: 1px;
  padding: 2px 4px;
  background: transparent;
  border: none;
  color: #666;
  font-size: 13px;
  cursor: pointer;
  white-space: nowrap;
  transition: color 0.15s;
}

.dismiss-btn:hover {
  color: #1a1a1a;
}

.banner-desc {
  margin: 0;
  font-size: 13px;
  color: #444;
  line-height: 1.5;
  max-width: 52ch;
}

.banner-footer {
  display: flex;
  align-items: center;
  margin-top: 4px;
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
  transition: background 0.15s;
}

.banner-btn:hover {
  background: #333;
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
