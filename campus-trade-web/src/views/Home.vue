<template>
  <div class="home-page">
    <div class="home-header">
      <h1 class="home-title">{{ t('home.title') }}</h1>
    </div>

    <!-- 搜索区 -->
    <section class="search-hero">
      <el-input
        v-model="keyword"
        :placeholder="searchPlaceholder"
        clearable
        size="large"
        class="hero-search"
        @keyup.enter="runSearch"
      >
        <template #prefix>
          <el-icon><Search /></el-icon>
        </template>
        <template #append>
          <el-button :icon="Search" @click="runSearch">{{ t('home.search') }}</el-button>
        </template>
      </el-input>
      <div class="mode-row">
        <button
          v-for="m in searchModes"
          :key="m.id"
          type="button"
          class="mode-btn"
          :class="{ active: searchMode === m.id }"
          @click="setSearchMode(m.id)"
        >
          {{ t(m.labelKey) }}
        </button>
      </div>
    </section>

    <div class="home-grid">
      <div class="home-main">
        <!-- 新手教程 -->
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
              <div class="promo-card" @click="goPublishOrEvents">
                <div class="promo-card-bg promo-bg-1" />
                <span class="promo-card-title">{{ t('onboarding.promoPublish') }}</span>
                <el-icon class="promo-arrow"><ArrowRight /></el-icon>
              </div>
              <div class="promo-card" @click="setSearchMode('products'); keyword = ''; runSearch()">
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
                <span class="promo-card-badge">0 {{ t('common.pointsUnit') }}</span>
                <el-icon class="promo-arrow"><ArrowRight /></el-icon>
              </div>
            </div>
          </div>
        </section>

        <!-- 商品搜索结果 -->
        <section v-if="resultMode === 'products'" class="section">
          <div class="feed-head">
            <h2 class="section-title">{{ t('home.productResults') }}</h2>
            <button type="button" class="text-btn" @click="backToFeed">{{ t('home.backToFeed') }}</button>
          </div>
          <div v-loading="productLoading" class="product-grid">
            <div
              v-for="item in productList"
              :key="item.productId"
              class="product-card"
              @click="goProduct(item.productId)"
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
                  <span class="product-price">{{ formatPoints(item.price) }}</span>
                  <span class="product-cat">{{ item.category }}</span>
                </div>
              </div>
            </div>
            <div v-if="!productLoading && productList.length === 0" class="empty-hint">
              {{ t('home.noProducts') }}
            </div>
          </div>
        </section>

        <!-- Feed -->
        <section v-else class="section">
          <div class="feed-head">
            <h2 class="section-title">{{ t('home.feedTitle') }}</h2>
            <el-button size="small" :loading="feedLoading" @click="refreshFeed">
              <el-icon class="refresh-icon"><Refresh /></el-icon>
              {{ t('home.refreshFeed') }}
            </el-button>
          </div>

          <div v-loading="feedLoading" class="feed-list">
            <article
              v-for="post in feedPosts"
              :key="post.postId"
              class="feed-card"
              @click="goTopic(post.postId)"
            >
              <h3 class="feed-card-title">{{ post.title }}</h3>
              <p v-if="previewText(post.content)" class="feed-card-preview">{{ previewText(post.content) }}</p>
              <div class="feed-card-meta">
                <span>{{ authorName(post) }}</span>
                <span class="meta-dot">·</span>
                <span>{{ t('topics.upvoteCount').replace('{n}', post.upvoteCount || 0) }}</span>
                <span class="meta-dot">·</span>
                <span>{{ t('topics.commentCount').replace('{n}', post.commentCount || 0) }}</span>
              </div>
            </article>
            <div v-if="!feedLoading && feedPosts.length === 0" class="empty-hint">
              {{ t('home.feedEmpty') }}
            </div>
          </div>
        </section>
      </div>

      <!-- 右侧栏 -->
      <aside class="home-aside">
        <div class="aside-card">
          <h2 class="aside-title">{{ t('home.announcements') }}</h2>
          <div class="aside-list">
            <div v-for="(u, i) in announcements" :key="i" class="aside-item">
              <span class="aside-date">{{ u.date }}</span>
              <h4 class="aside-headline">{{ u.title }}</h4>
              <p class="aside-desc">{{ u.desc }}</p>
            </div>
          </div>
          <router-link class="aside-link" to="/notifications">{{ t('home.viewNotifications') }}</router-link>
        </div>

        <div class="aside-card">
          <h2 class="aside-title">{{ t('home.eventsTitle') }}</h2>
          <p class="aside-desc">{{ eventsSummary }}</p>
          <router-link class="aside-cta" to="/events">{{ t('home.goEvents') }}</router-link>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Check, ArrowRight, Search, Picture, Refresh } from '@element-plus/icons-vue'
import { searchProducts, getProductList } from '@/api/product'
import { getFeed, listTopicPosts } from '@/api/topic'
import { getEventsStatus } from '@/api/points'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'
import { useOnboarding } from '@/composables/useOnboarding'
import { htmlToPlainText } from '@/utils/sanitizeHtml'

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

const keyword = ref('')
const searchMode = ref('posts')
const resultMode = ref('feed')

const feedLoading = ref(false)
const feedPosts = ref([])
const productLoading = ref(false)
const productList = ref([])

const eventsStatus = ref(null)

const searchModes = [
  { id: 'posts', labelKey: 'home.modePosts' },
  { id: 'products', labelKey: 'home.modeProducts' },
  { id: 'ai', labelKey: 'home.modeAi' },
  { id: 'create', labelKey: 'home.modeCreate' }
]

const searchPlaceholder = computed(() => {
  if (searchMode.value === 'products') return t('home.searchProductsPlaceholder')
  if (searchMode.value === 'ai') return t('home.searchAiPlaceholder')
  return t('home.searchPostsPlaceholder')
})

const announcements = computed(() => [
  {
    date: t('home.announce1Date'),
    title: t('home.announce1Title'),
    desc: t('home.announce1Desc')
  },
  {
    date: t('home.announce2Date'),
    title: t('home.announce2Title'),
    desc: t('home.announce2Desc')
  }
])

const eventsSummary = computed(() => {
  if (!userStore.isLogin) return t('home.eventsGuest')
  const s = eventsStatus.value
  if (!s) return t('home.eventsLoading')
  if (s.checkedInToday) return t('home.eventsCheckedIn').replace('{pts}', String(s.points ?? userStore.points))
  return t('home.eventsNotCheckedIn').replace('{pts}', String(s.points ?? userStore.points))
})

function formatPoints(price) {
  const n = Number(price)
  const value = Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(0)) : String(price ?? 0)
  return `${value} ${t('common.pointsUnit')}`
}

function authorName(post) {
  return post.nickname || (post.userId ? `用户 #${post.userId}` : t('topics.anonymous'))
}

function previewText(content) {
  const text = htmlToPlainText(content || '')
  if (!text) return ''
  return text.length > 140 ? `${text.slice(0, 140)}…` : text
}

function shuffle(arr) {
  const a = [...arr]
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1))
    ;[a[i], a[j]] = [a[j], a[i]]
  }
  return a
}

function requireLogin(redirect) {
  if (userStore.isLogin) return true
  ElMessage.info(t('home.loginRequired'))
  router.push({ path: '/login', query: { redirect } })
  return false
}

function setSearchMode(id) {
  searchMode.value = id
  if (id === 'ai') {
    if (!requireLogin('/ai-shopping')) return
    router.push('/ai-shopping')
    return
  }
  if (id === 'create') {
    if (!requireLogin('/topics/create')) return
    router.push('/topics/create')
    return
  }
}

function runSearch() {
  if (searchMode.value === 'ai') {
    setSearchMode('ai')
    return
  }
  if (searchMode.value === 'create') {
    setSearchMode('create')
    return
  }
  if (searchMode.value === 'products') {
    fetchProducts()
    return
  }
  // 只搜帖子：有关键词跳话题页，否则过滤本地 feed
  const q = keyword.value.trim()
  if (q) {
    router.push({ path: '/topics', query: { q } })
    return
  }
  resultMode.value = 'feed'
  refreshFeed()
}

async function fetchProducts() {
  resultMode.value = 'products'
  productLoading.value = true
  try {
    const params = { pageNum: 1, pageSize: 12 }
    let res
    if (keyword.value.trim()) {
      params.keyword = keyword.value.trim()
      res = await searchProducts(params)
    } else {
      res = await getProductList(params)
    }
    productList.value = res.data?.list || []
  } catch {
    productList.value = []
  } finally {
    productLoading.value = false
  }
  onboarding.trackStep('browse')
}

function backToFeed() {
  resultMode.value = 'feed'
  searchMode.value = 'posts'
}

async function refreshFeed() {
  feedLoading.value = true
  try {
    let list = []
    try {
      const res = await getFeed({ size: 20 })
      list = Array.isArray(res.data) ? res.data : (res.data?.list || [])
    } catch {
      const res = await listTopicPosts({ pageNum: 1, pageSize: 20 })
      list = shuffle(res.data?.list || [])
    }
    // 若 feed 接口返回空数组但成功，仍展示空；若有关键词则前端过滤
    const q = keyword.value.trim().toLowerCase()
    if (q && searchMode.value === 'posts') {
      list = list.filter((p) => {
        const title = (p.title || '').toLowerCase()
        const body = htmlToPlainText(p.content || '').toLowerCase()
        return title.includes(q) || body.includes(q)
      })
    }
    feedPosts.value = list
  } catch {
    feedPosts.value = []
  } finally {
    feedLoading.value = false
  }
}

function goTopic(id) {
  router.push(`/topics/${id}`)
}

function goProduct(id) {
  router.push(`/product/${id}`)
}

function goPublishOrEvents() {
  if (userStore.isMerchant || userStore.isAdmin) {
    router.push('/publish')
  } else {
    router.push('/events')
  }
}

async function loadEventsSummary() {
  if (!userStore.isLogin) {
    eventsStatus.value = null
    return
  }
  try {
    const res = await getEventsStatus()
    eventsStatus.value = res.data || null
    if (res.data?.points != null) {
      userStore.setUserInfo({ points: Number(res.data.points) })
    }
  } catch {
    eventsStatus.value = null
  }
}

onMounted(async () => {
  await Promise.all([refreshFeed(), loadEventsSummary(), onboarding.refresh()])
  onboarding.trackStep('browse')
})
</script>

<style scoped>
.home-page {
  max-width: 1100px;
}

.home-header {
  margin-bottom: 20px;
}

.home-title {
  font-size: 28px;
  font-weight: 500;
  letter-spacing: -0.02em;
}

.search-hero {
  margin-bottom: 28px;
}

.hero-search {
  width: 100%;
}

.mode-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
}

.mode-btn {
  padding: 6px 14px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: 8px;
  background: var(--oa-bg-sidebar);
  color: var(--oa-text-secondary);
  font-size: 13px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s, border-color 0.15s;
}

.mode-btn:hover {
  color: var(--oa-text);
  border-color: var(--oa-border);
}

.mode-btn.active {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
  border-color: var(--oa-border);
}

.home-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 32px;
  align-items: start;
}

.section {
  margin-bottom: 28px;
}

.section-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
  margin: 0;
}

.feed-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}

.refresh-icon {
  margin-right: 4px;
}

.text-btn {
  border: none;
  background: transparent;
  color: var(--oa-text-secondary);
  font-size: 13px;
  cursor: pointer;
}

.text-btn:hover {
  color: var(--oa-text);
}

.feed-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 120px;
}

.feed-card {
  padding: 16px 18px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  background: var(--oa-bg-sidebar);
  cursor: pointer;
  transition: border-color 0.15s;
}

.feed-card:hover {
  border-color: var(--oa-border);
}

.feed-card-title {
  font-size: 15px;
  font-weight: 550;
  margin: 0 0 6px;
  line-height: 1.4;
}

.feed-card-preview {
  margin: 0 0 10px;
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.feed-card-meta {
  font-size: 12px;
  color: var(--oa-text-muted);
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.meta-dot {
  opacity: 0.6;
}

.empty-hint {
  text-align: center;
  color: var(--oa-text-muted);
  font-size: 13px;
  padding: 32px 16px;
}

/* Onboarding — keep existing look */
.onboarding-section {
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius-lg, var(--oa-radius));
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
  background: linear-gradient(90deg, #10a37f, #22c55e);
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
  opacity: 0.55;
}

.promo-bg-1 {
  background: linear-gradient(135deg, #1a7f64 0%, #10a37f 50%, #34d399 100%);
}

.promo-bg-2 {
  background: linear-gradient(135deg, #374151 0%, #6b7280 50%, #9ca3af 100%);
}

.promo-bg-3 {
  background: linear-gradient(135deg, #0f766e 0%, #14b8a6 50%, #5eead4 100%);
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

/* Products */
.product-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  min-height: 80px;
}

.product-card {
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  overflow: hidden;
  cursor: pointer;
  background: var(--oa-bg-sidebar);
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
  gap: 8px;
}

.product-price {
  font-size: 13px;
  font-weight: 600;
}

.product-cat {
  font-size: 11px;
  color: var(--oa-text-muted);
}

/* Aside */
.home-aside {
  position: sticky;
  top: 32px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.aside-card {
  padding: 16px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  background: var(--oa-bg-sidebar);
}

.aside-title {
  font-size: 14px;
  font-weight: 500;
  margin: 0 0 12px;
}

.aside-list {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.aside-item {
  padding-bottom: 14px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.aside-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.aside-date {
  font-size: 11px;
  color: var(--oa-text-muted);
}

.aside-headline {
  font-size: 13px;
  font-weight: 500;
  margin: 4px 0;
  line-height: 1.4;
}

.aside-desc {
  font-size: 12px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
  margin: 0 0 12px;
}

.aside-link {
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.aside-link:hover {
  color: var(--oa-text);
}

.aside-cta {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  background: var(--oa-text);
  color: var(--oa-on-primary);
  border-radius: 8px;
  font-size: 13px;
  font-weight: 500;
}

.aside-cta:hover {
  opacity: 0.9;
}

@media (max-width: 960px) {
  .home-grid {
    grid-template-columns: 1fr;
  }

  .home-aside {
    position: static;
  }

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
