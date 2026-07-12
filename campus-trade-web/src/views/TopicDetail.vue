<template>
  <div class="page-container detail-layout" v-loading="loading">
    <aside class="detail-side">
      <div class="side-section">
        <div class="side-label">{{ t('topics.resources') }}</div>
        <nav class="side-nav">
          <router-link class="side-link" to="/topics">{{ t('topics.feed') }}</router-link>
          <router-link class="side-link" to="/topics/create">{{ t('topics.compose') }}</router-link>
        </nav>
      </div>
      <div class="side-section">
        <div class="side-label">{{ t('topics.trending') }}</div>
        <button
          v-for="item in trending"
          :key="item.postId"
          type="button"
          class="trend-item"
          @click="$router.push(`/topics/${item.postId}`)"
        >
          <span class="trend-icon">↗</span>
          <span class="trend-text">
            <span class="trend-title">{{ item.title }}</span>
            <span class="trend-reason">{{ item.reason || t('topics.trendingReason') }}</span>
          </span>
        </button>
      </div>
    </aside>

    <div class="detail-main">
      <button class="oa-back-btn" @click="$router.push('/topics')">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('topics.back') }}
      </button>

      <el-empty v-if="!loading && !post" :description="t('topics.notFound')" />

      <template v-if="post">
        <div class="post-header">
          <h1 class="page-title">{{ post.title }}</h1>

          <div class="author-row">
            <button
              type="button"
              class="author-avatar"
              :class="{ clickable: canOpenAuthor }"
              :aria-label="authorName"
              @click="onOpenAuthor"
            >
              <img
                :src="authorAvatarSrc"
                alt=""
                class="author-avatar-img"
                referrerpolicy="no-referrer"
                @error="onAuthorAvatarError"
              />
            </button>
            <div class="author-meta">
              <div class="author-name-line">
                <button
                  v-if="canOpenAuthor"
                  type="button"
                  class="author-name link"
                  @click="onOpenAuthor"
                >
                  {{ authorName }}
                </button>
                <span v-else class="author-name">{{ authorName }}</span>
                <button
                  v-if="canMessageAuthor"
                  type="button"
                  class="dm-btn"
                  @click="onMessageAuthor"
                >
                  {{ t('profilePage.sendDm') }}
                </button>
                <span class="meta-dot">·</span>
                <span class="author-time">{{ formatTime(post.createTime) }}</span>
              </div>
              <p v-if="post.bio" class="author-bio">{{ post.bio }}</p>
            </div>
          </div>

          <p v-if="post.upvoteCount" class="agree-meta">
            {{ t('topics.agreeCount').replace('{n}', String(post.upvoteCount)) }}
          </p>
        </div>

        <div class="oa-panel content-panel">
          <div
            v-if="safeContent"
            class="post-content rich-content"
            v-html="safeContent"
          />
          <p v-else class="post-content muted">{{ t('topics.noBody') }}</p>
        </div>

        <div v-if="post.tipEnabled && canTip" class="tip-mid">
          <button type="button" class="tip-mid-btn" @click="tipVisible = true">
            {{ t('topics.tipPost') }}
          </button>
          <span v-if="post.tipTotal" class="tip-total">
            {{ t('topics.tipTotal').replace('{n}', post.tipTotal) }}
          </span>
        </div>

        <div class="action-bar">
          <button
            type="button"
            class="bar-btn vote"
            :class="{ active: post.upvoted }"
            @click="onUpvotePost"
          >
            <span class="bar-icon">▲</span>
            <span>{{ t('topics.agree') }}</span>
            <span v-if="post.upvoteCount" class="bar-count">{{ post.upvoteCount }}</span>
          </button>
          <button type="button" class="bar-btn" @click="scrollToComments">
            <span>{{ t('topics.comment') }}</span>
            <span class="bar-count">{{ commentTotal }}</span>
          </button>
          <button type="button" class="bar-btn" @click="onSharePost">
            {{ t('topics.share') }}
          </button>
          <button
            v-if="canDelete"
            type="button"
            class="bar-btn danger"
            :disabled="deleting"
            @click="onDelete"
          >
            {{ t('topics.delete') }}
          </button>
        </div>

        <div v-if="products.length > 0" class="oa-panel products-panel">
          <h3 class="oa-section-title">{{ t('topics.attachedProducts') }}</h3>
          <div class="product-grid">
            <div
              v-for="item in products"
              :key="item.productId"
              class="product-card"
              @click="$router.push(`/product/${item.productId}`)"
            >
              <el-image v-if="item.cover" :src="item.cover" fit="cover" class="product-cover" />
              <div v-else class="product-cover placeholder" />
              <div class="product-body">
                <span class="product-title">{{ item.title }}</span>
                <span class="product-price">{{ formatPoints(item.price) }}</span>
              </div>
            </div>
          </div>
        </div>

        <section id="topic-comments" class="comments-section">
          <h3 class="oa-section-title">
            {{ t('topics.commentsTitle').replace('{n}', String(commentTotal)) }}
          </h3>

          <TopicCommentBar
            v-model="topComment"
            v-model:image-url="topImage"
            :placeholder="t('topics.joinConversation')"
            :submit-label="t('topics.comment')"
            :submitting="commentSubmitting"
            @submit="submitTopComment"
            @cancel="resetTopComment"
          />

          <div v-loading="commentsLoading" class="comments-tree">
            <el-empty
              v-if="!commentsLoading && comments.length === 0"
              :description="t('topics.commentsEmpty')"
            />
            <TopicCommentThread
              v-for="item in comments"
              :key="item.commentId"
              :comment="item"
              :post-id="post.postId"
              @changed="fetchComments"
            />
          </div>
        </section>
      </template>
    </div>

    <el-dialog v-model="tipVisible" :title="t('topics.tipTitle')" width="400px" @open="onTipDialogOpen">
      <el-form label-position="top">
        <el-form-item :label="t('topics.tipAmount')">
          <el-input-number v-model="tipAmount" :min="1" :max="10000" :step="1" controls-position="right" style="width: 100%" />
        </el-form-item>
        <p class="tip-balance muted">{{ t('topics.tipBalance').replace('{n}', String(userStore.points)) }}</p>
      </el-form>
      <template #footer>
        <el-button @click="tipVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="tipping" @click="onTipPost">{{ t('topics.tipConfirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import {
  getTopicPost,
  deleteTopicPost,
  upvoteTopicPost,
  tipTopicPost,
  listTopicComments,
  createTopicComment,
  listTrendingTopics
} from '@/api/topic'
import TopicCommentBar from '@/components/TopicCommentBar.vue'
import TopicCommentThread from '@/components/TopicCommentThread.vue'
import { sanitizeHtml, htmlToPlainText } from '@/utils/sanitizeHtml'
import { letterAvatarDataUri, resolveAvatarSrc } from '@/utils/avatar'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const loading = ref(false)
const deleting = ref(false)
const commentsLoading = ref(false)
const commentSubmitting = ref(false)
const post = ref(null)
const comments = ref([])
const trending = ref([])
const topComment = ref('')
const topImage = ref('')
const authorAvatarBroken = ref(false)

const products = computed(() => post.value?.products || [])

const safeContent = computed(() => {
  const raw = post.value?.content
  if (!raw) return ''
  const cleaned = sanitizeHtml(raw)
  return htmlToPlainText(cleaned) ? cleaned : ''
})

const authorName = computed(() => {
  if (!post.value) return ''
  return post.value.nickname || (post.value.userId ? `用户 #${post.value.userId}` : t('topics.anonymous'))
})

const authorLetter = computed(() => {
  const name = authorName.value || '?'
  return name.slice(0, 1).toUpperCase()
})

const authorAvatarSrc = computed(() => {
  if (authorAvatarBroken.value) return letterAvatarDataUri(authorName.value)
  return resolveAvatarSrc(post.value?.avatar, authorName.value)
})

const canDelete = computed(() => {
  if (!post.value || !userStore.isLogin) return false
  return post.value.userId === userStore.userInfo?.userId
})

const canTip = computed(() => {
  if (!post.value || !userStore.isLogin) return false
  return post.value.userId !== userStore.userInfo?.userId
})

const canOpenAuthor = computed(() => post.value?.userId != null)

const canMessageAuthor = computed(() => {
  if (!post.value || !userStore.isLogin) return false
  return post.value.userId != null && post.value.userId !== userStore.userInfo?.userId
})

function onAuthorAvatarError() {
  authorAvatarBroken.value = true
}

function onOpenAuthor() {
  if (!canOpenAuthor.value) return
  router.push(`/users/${post.value.userId}`)
}

function onMessageAuthor() {
  if (!canMessageAuthor.value) return
  router.push({ path: '/messages', query: { peerUserId: String(post.value.userId) } })
}

const tipVisible = ref(false)
const tipAmount = ref(10)
const tipping = ref(false)
const tipRequestId = ref('')

function onTipDialogOpen() {
  tipAmount.value = 10
  tipRequestId.value = (typeof crypto !== 'undefined' && crypto.randomUUID)
    ? crypto.randomUUID()
    : `tip-${Date.now()}-${Math.random().toString(36).slice(2, 10)}`
}

async function onTipPost() {
  if (!ensureLogin() || !post.value) return
  if (!post.value.tipEnabled) {
    ElMessage.warning(t('topics.tipDisabled'))
    return
  }
  if (!canTip.value) {
    ElMessage.warning(t('topics.tipSelf'))
    return
  }
  const amount = Number(tipAmount.value)
  if (!Number.isFinite(amount) || amount < 1) return
  if (!tipRequestId.value) onTipDialogOpen()
  tipping.value = true
  try {
    const res = await tipTopicPost(post.value.postId, { amount, requestId: tipRequestId.value })
    const tipTotal = res.data?.tipTotal ?? res.data
    if (tipTotal != null && typeof tipTotal === 'object' && tipTotal.tipTotal != null) {
      post.value.tipTotal = tipTotal.tipTotal
    } else if (typeof tipTotal === 'number') {
      post.value.tipTotal = tipTotal
    } else {
      post.value.tipTotal = Number(post.value.tipTotal || 0) + amount
    }
    tipVisible.value = false
    ElMessage.success(t('topics.tipSuccess'))
    userStore.refreshPoints()
  } finally {
    tipping.value = false
  }
}

const commentTotal = computed(() => countTree(comments.value))

function countTree(list) {
  let n = 0
  for (const item of list || []) {
    n += 1 + countTree(item.children || [])
  }
  return n
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

function formatPoints(price) {
  const n = Number(price)
  const value = Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(0)) : String(price ?? 0)
  return `${value} ${t('common.pointsUnit')}`
}

function ensureLogin() {
  if (userStore.isLogin) return true
  router.push({ path: '/login', query: { redirect: route.fullPath } })
  return false
}

function scrollToComments() {
  document.getElementById('topic-comments')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function fetchPost() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  authorAvatarBroken.value = false
  try {
    const res = await getTopicPost(id)
    post.value = res.data
  } catch {
    post.value = null
  } finally {
    loading.value = false
  }
}

async function fetchComments() {
  const id = route.params.id
  if (!id) return
  commentsLoading.value = true
  try {
    const res = await listTopicComments(id)
    comments.value = res.data || []
    if (post.value) {
      post.value.commentCount = countTree(comments.value)
    }
  } catch {
    comments.value = []
  } finally {
    commentsLoading.value = false
  }
}

async function fetchTrending() {
  try {
    const res = await listTrendingTopics({ limit: 6 })
    trending.value = res.data || []
  } catch {
    trending.value = []
  }
}

async function onUpvotePost() {
  if (!ensureLogin() || !post.value) return
  try {
    const res = await upvoteTopicPost(post.value.postId)
    post.value.upvoteCount = res.data?.upvoteCount ?? post.value.upvoteCount
    post.value.upvoted = !!res.data?.upvoted
  } catch {
    // ignore
  }
}

async function onSharePost() {
  const url = `${window.location.origin}/topics/${post.value.postId}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('topics.linkCopied'))
  } catch {
    ElMessage.info(url)
  }
}

function resetTopComment() {
  topComment.value = ''
  topImage.value = ''
}

async function submitTopComment() {
  if (!ensureLogin() || !post.value) return
  const content = topComment.value.trim()
  if (!content) return
  commentSubmitting.value = true
  try {
    await createTopicComment(post.value.postId, {
      content,
      imageUrl: topImage.value || undefined
    })
    ElMessage.success(t('topics.commentDone'))
    resetTopComment()
    await fetchComments()
  } finally {
    commentSubmitting.value = false
  }
}

async function onDelete() {
  try {
    await ElMessageBox.confirm(t('topics.deleteConfirm'), t('topics.tip'), {
      confirmButtonText: t('topics.confirm'),
      cancelButtonText: t('topics.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }
  deleting.value = true
  try {
    await deleteTopicPost(post.value.postId)
    ElMessage.success(t('topics.deleteDone'))
    router.push('/topics')
  } finally {
    deleting.value = false
  }
}

async function loadAll() {
  await Promise.all([fetchPost(), fetchComments(), fetchTrending()])
}

watch(() => route.params.id, loadAll)
onMounted(loadAll)
</script>

<style scoped>
.detail-layout {
  display: grid;
  grid-template-columns: 220px minmax(0, 1fr);
  gap: 24px;
  align-items: start;
}

.detail-side {
  position: sticky;
  top: 16px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.side-section {
  padding: 14px 12px;
  border: none;
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
}

.trend-reason {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.post-header {
  margin-bottom: 16px;
}

.author-row {
  display: flex;
  align-items: flex-start;
  gap: 12px;
  margin-top: 16px;
}

.author-avatar {
  flex-shrink: 0;
  width: 40px;
  height: 40px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--oa-bg-elevated);
  color: var(--oa-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  border: 1px solid var(--oa-border-subtle);
  padding: 0;
  cursor: default;
}

.author-avatar.clickable {
  cursor: pointer;
}

.author-avatar.clickable:hover {
  border-color: var(--el-color-primary);
}

.author-avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.author-meta {
  min-width: 0;
  flex: 1;
}

.author-name-line {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 0;
  font-size: 14px;
}

.author-name {
  font-weight: 600;
  color: var(--oa-text);
}

.author-name.link {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  color: var(--oa-text);
}

.author-name.link:hover {
  color: var(--el-color-primary);
}

.dm-btn {
  margin-left: 8px;
  border: 1px solid var(--oa-border-subtle);
  background: transparent;
  color: var(--oa-text-muted);
  font-size: 12px;
  padding: 2px 8px;
  border-radius: 999px;
  cursor: pointer;
  line-height: 1.4;
}

.dm-btn:hover {
  color: var(--el-color-primary);
  border-color: var(--el-color-primary);
}

.author-time {
  font-size: 13px;
  color: var(--oa-text-muted);
}

.author-bio {
  margin: 4px 0 0;
  font-size: 13px;
  line-height: 1.45;
  color: var(--oa-text-muted);
}

.meta-dot {
  margin: 0 6px;
  color: var(--oa-text-muted);
}

.agree-meta {
  margin: 12px 0 0;
  font-size: 13px;
  color: var(--oa-text-muted);
}

.content-panel {
  margin-bottom: 0;
}

.post-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
  font-size: 15px;
  color: var(--oa-text-secondary);
}

.post-content.rich-content {
  white-space: normal;
}

.post-content.rich-content :deep(img),
.post-content.rich-content :deep(video) {
  max-width: 100%;
  border-radius: 12px;
  display: block;
  margin: 12px 0;
}

.post-content.rich-content :deep(blockquote) {
  margin: 12px 0;
  padding: 8px 14px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--oa-text-muted);
  background: color-mix(in srgb, var(--oa-text) 4%, transparent);
  border-radius: 0 10px 10px 0;
}

.post-content.rich-content :deep(pre) {
  padding: 12px 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--oa-text) 6%, transparent);
  overflow: auto;
  font-size: 13px;
}

.post-content.rich-content :deep(a) {
  color: var(--el-color-primary);
}

.post-content.muted {
  color: var(--oa-text-muted);
  font-style: italic;
}

.tip-mid {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  margin: 20px 0 8px;
}

.tip-mid-btn {
  border: 1px solid var(--oa-border-subtle);
  background: var(--oa-bg-elevated);
  color: var(--oa-text-secondary);
  border-radius: 999px;
  padding: 6px 18px;
  font-size: 13px;
  cursor: pointer;
}

.tip-mid-btn:hover {
  color: var(--oa-accent, #10a37f);
  border-color: var(--oa-accent, #10a37f);
}

.tip-total {
  font-size: 12px;
  color: var(--oa-accent, #10a37f);
}

.action-bar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 4px;
  margin: 12px 0 8px;
  padding: 8px 0;
  border-top: 1px solid var(--oa-border-subtle);
  border-bottom: 1px solid var(--oa-border-subtle);
}

.bar-btn {
  border: 0;
  background: transparent;
  color: var(--oa-text-muted);
  font-size: 13px;
  padding: 8px 12px;
  border-radius: 8px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}

.bar-btn:hover {
  background: var(--oa-bg-elevated);
  color: var(--oa-text);
}

.bar-btn.vote.active {
  color: var(--el-color-primary);
}

.bar-btn.danger {
  color: var(--el-color-danger);
  margin-left: auto;
}

.bar-btn.danger:hover {
  background: color-mix(in srgb, var(--el-color-danger) 12%, transparent);
}

.bar-btn:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

.bar-icon {
  font-size: 11px;
}

.bar-count {
  font-variant-numeric: tabular-nums;
}

.products-panel {
  margin-top: 16px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 12px;
}

.product-card {
  border: none;
  border-radius: var(--oa-radius);
  overflow: hidden;
  cursor: pointer;
  transition: border-color 0.15s ease;
}

.product-card:hover {
  border-color: var(--oa-border-strong, var(--el-color-primary-light-5));
}

.product-cover {
  width: 100%;
  height: 120px;
  background: var(--oa-bg-elevated);
}

.product-cover.placeholder {
  display: block;
}

.product-body {
  padding: 10px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.product-title {
  font-size: 13px;
  color: var(--oa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
}

.comments-section {
  margin-top: 24px;
}

.comments-tree {
  margin-top: 16px;
  min-height: 40px;
}

.tip-balance {
  margin: 0;
  font-size: 13px;
}

@media (max-width: 900px) {
  .detail-layout {
    grid-template-columns: 1fr;
  }

  .detail-side {
    position: static;
    order: 2;
  }

  .bar-btn.danger {
    margin-left: 0;
  }
}
</style>
