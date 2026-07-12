<template>
  <div class="page-container topics-layout">
    <aside class="topics-side">
      <div class="side-section">
        <div class="side-label">{{ t('topics.resources') }}</div>
        <nav class="side-nav">
          <router-link class="side-link" :class="{ active: true }" to="/topics">
            {{ t('topics.feed') }}
          </router-link>
          <router-link class="side-link" to="/topics/create">
            {{ t('topics.compose') }}
          </router-link>
          <router-link class="side-link" to="/">
            {{ t('topics.backHome') }}
          </router-link>
        </nav>
      </div>

      <div class="side-section trending-panel">
        <div class="side-label">{{ t('topics.trending') }}</div>
        <el-empty
          v-if="!trendingLoading && trending.length === 0"
          :description="t('topics.trendingEmpty')"
          :image-size="48"
        />
        <button
          v-for="item in trending"
          :key="item.postId"
          type="button"
          class="trend-item"
          @click="goDetail(item.postId)"
        >
          <span class="trend-icon" aria-hidden="true">↗</span>
          <span class="trend-text">
            <span class="trend-title">{{ item.title }}</span>
            <span class="trend-reason">{{ item.reason || t('topics.trendingReason') }}</span>
          </span>
        </button>
      </div>
    </aside>

    <div class="topics-main">
      <div class="page-header topics-header">
        <div>
          <h1 class="page-title">{{ t('topics.title') }}</h1>
          <p class="page-subtitle">{{ t('topics.subtitle') }}</p>
        </div>
        <el-button type="primary" @click="goCreate">
          {{ t('topics.compose') }}
        </el-button>
      </div>

      <div v-loading="loading" class="topics-feed">
        <el-empty v-if="!loading && posts.length === 0" :description="t('topics.empty')" />

        <article
          v-for="post in posts"
          :key="post.postId"
          class="oa-panel post-card"
          @click="goDetail(post.postId)"
        >
          <div class="post-card-head">
            <h2 class="post-title">{{ post.title }}</h2>
            <el-tag v-if="post.productCount > 0" size="small" type="info">
              {{ t('topics.productCount').replace('{n}', post.productCount) }}
            </el-tag>
          </div>
          <p v-if="previewText(post.content)" class="post-preview">{{ previewText(post.content) }}</p>
          <div class="post-meta">
            <span>{{ authorName(post) }}</span>
            <span class="meta-dot">·</span>
            <span>{{ formatTime(post.createTime) }}</span>
            <span class="meta-dot">·</span>
            <span>{{ t('topics.commentCount').replace('{n}', post.commentCount || 0) }}</span>
            <span class="meta-dot">·</span>
            <span>{{ t('topics.upvoteCount').replace('{n}', post.upvoteCount || 0) }}</span>
          </div>
        </article>

        <div v-if="total > posts.length || pageNum > 1" class="oa-pagination">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20]"
            layout="total, prev, pager, next"
            background
            @current-change="fetchPosts"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import { listTopicPosts, listTrendingTopics } from '@/api/topic'
import { htmlToPlainText } from '@/utils/sanitizeHtml'

const { t } = useI18n()
const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const trendingLoading = ref(false)
const posts = ref([])
const trending = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

onMounted(() => {
  fetchPosts()
  fetchTrending()
})

function authorName(post) {
  return post.nickname || (post.userId ? `用户 #${post.userId}` : t('topics.anonymous'))
}

function previewText(content) {
  const text = htmlToPlainText(content || '')
  if (!text) return ''
  return text.length > 160 ? `${text.slice(0, 160)}…` : text
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

function goDetail(id) {
  router.push(`/topics/${id}`)
}

function goCreate() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: '/topics/create' } })
    return
  }
  router.push('/topics/create')
}

async function fetchPosts() {
  loading.value = true
  try {
    const params = { pageNum: pageNum.value, pageSize: pageSize.value }
    const q = typeof route.query.q === 'string' ? route.query.q.trim() : ''
    if (q) params.keyword = q
    const res = await listTopicPosts(params)
    const page = res.data || {}
    posts.value = page.list || []
    total.value = page.total || 0
  } catch {
    posts.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

async function fetchTrending() {
  trendingLoading.value = true
  try {
    const res = await listTrendingTopics({ limit: 8 })
    trending.value = res.data || []
  } catch {
    trending.value = []
  } finally {
    trendingLoading.value = false
  }
}

function onSizeChange() {
  pageNum.value = 1
  fetchPosts()
}
</script>

<style scoped>
.topics-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.topics-side {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.side-section {
  padding: 14px 12px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  background: var(--oa-bg-sidebar, var(--oa-bg-elevated, #fafafa));
}

.side-label {
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.06em;
  text-transform: uppercase;
  color: var(--oa-text-muted);
  margin-bottom: 10px;
  padding: 0 6px;
}

.side-nav {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.side-link {
  display: block;
  padding: 8px 10px;
  border-radius: 8px;
  color: var(--oa-text-secondary);
  text-decoration: none;
  font-size: 14px;
}

.side-link:hover,
.side-link.active {
  background: var(--oa-bg-elevated);
  color: var(--oa-text);
}

.trend-item {
  display: flex;
  gap: 10px;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 8px 6px;
  border-radius: 8px;
  cursor: pointer;
  color: inherit;
}

.trend-item:hover {
  background: var(--oa-bg-elevated);
}

.trend-icon {
  color: var(--oa-text-muted);
  font-size: 14px;
  line-height: 1.4;
}

.trend-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.trend-title {
  font-size: 13px;
  font-weight: 600;
  color: var(--oa-text);
  line-height: 1.35;
}

.trend-reason {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.topics-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.page-subtitle {
  margin-top: 6px;
  font-size: 14px;
  color: var(--oa-text-secondary);
  font-weight: 400;
}

.topics-feed {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.post-card {
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.post-card:hover {
  border-color: var(--oa-border-strong, var(--oa-border-subtle));
}

.post-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.post-title {
  margin: 0;
  font-size: 18px;
  font-weight: 500;
  line-height: 1.35;
}

.post-preview {
  margin: 0 0 12px;
  color: var(--oa-text-secondary);
  font-size: 14px;
  line-height: 1.6;
  white-space: pre-wrap;
}

.post-meta {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.meta-dot {
  margin: 0 6px;
}

.oa-pagination {
  margin-top: 8px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .topics-layout {
    grid-template-columns: 1fr;
  }

  .topics-side {
    position: static;
    order: 2;
  }
}
</style>
