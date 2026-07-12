<template>
  <div class="page-container topics-layout">
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

    <aside class="topics-side">
      <div class="trending-panel">
        <div class="side-label">{{ t('topics.trending') }}</div>
        <el-empty
          v-if="!trendingLoading && trending.length === 0"
          :description="t('topics.trendingEmpty')"
          :image-size="48"
        />
        <button
          v-for="(item, index) in trending"
          :key="item.postId"
          type="button"
          class="trend-item"
          @click="goDetail(item.postId)"
        >
          <span class="trend-rank" :class="{ hot: index < 3 }">{{ index + 1 }}</span>
          <span class="trend-text">
            <span class="trend-title">{{ item.title }}</span>
            <span class="trend-reason">{{ item.reason || t('topics.trendingReason') }}</span>
          </span>
        </button>
      </div>
    </aside>
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
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 28px;
  align-items: start;
}

.topics-side {
  position: sticky;
  top: 16px;
}

.trending-panel {
  padding: 4px 0;
  background: transparent;
}

.side-label {
  font-size: 15px;
  font-weight: 600;
  color: var(--oa-text);
  margin-bottom: 12px;
  letter-spacing: -0.01em;
}

.trend-item {
  display: flex;
  gap: 12px;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 10px 4px;
  border-radius: 8px;
  cursor: pointer;
  color: inherit;
}

.trend-item:hover {
  background: var(--oa-bg-elevated);
}

.trend-rank {
  flex-shrink: 0;
  width: 20px;
  font-size: 14px;
  font-weight: 600;
  color: var(--oa-text-muted);
  line-height: 1.4;
  font-variant-numeric: tabular-nums;
}

.trend-rank.hot {
  color: #ef4444;
}

.trend-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.trend-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
  line-height: 1.4;
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
  gap: 8px;
}

.post-card {
  cursor: pointer;
  transition: background 0.15s ease;
}

.post-card:hover {
  background: var(--oa-bg-hover);
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
