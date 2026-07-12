<template>
  <div class="thread" :class="{ nested: depth > 0 }">
    <div class="thread-main">
      <button
        v-if="hasChildren"
        type="button"
        class="collapse-btn"
        :aria-label="collapsed ? t('topics.expandThread') : t('topics.collapseThread')"
        @click="collapsed = !collapsed"
      >
        {{ collapsed ? '+' : '−' }}
      </button>
      <div v-else class="collapse-spacer" />

      <div class="thread-body">
        <div class="comment-head">
          <button
            v-if="canOpenAuthor"
            type="button"
            class="avatar clickable"
            :aria-label="authorName"
            @click="onOpenAuthor"
          >
            <img
              :src="avatarSrc"
              alt=""
              class="avatar-img"
              referrerpolicy="no-referrer"
              @error="onAvatarError"
            />
          </button>
          <div v-else class="avatar" aria-hidden="true">
            <img
              :src="avatarSrc"
              alt=""
              class="avatar-img"
              referrerpolicy="no-referrer"
              @error="onAvatarError"
            />
          </div>
          <button
            v-if="canOpenAuthor"
            type="button"
            class="author link"
            @click="onOpenAuthor"
          >
            {{ authorName }}
          </button>
          <span v-else class="author">{{ authorName }}</span>
          <span class="time">{{ relativeTime(comment.createTime) }}</span>
        </div>

        <p class="comment-text">{{ comment.content }}</p>
        <el-image
          v-if="comment.imageUrl"
          :src="comment.imageUrl"
          fit="cover"
          class="comment-image"
          :preview-src-list="[comment.imageUrl]"
        />

        <div class="comment-actions">
          <button type="button" class="action-btn" :class="{ active: comment.upvoted }" @click="onUpvote">
            <span class="caret">▲</span>
            <span>{{ comment.upvoteCount || 0 }}</span>
          </button>
          <button type="button" class="action-btn" @click="toggleReply">
            {{ t('topics.reply') }}
          </button>
          <button type="button" class="action-btn" @click="onShare">
            {{ t('topics.share') }}
          </button>
        </div>

        <div v-if="replying" class="reply-box">
          <TopicCommentBar
            v-model="replyText"
            v-model:image-url="replyImage"
            :placeholder="t('topics.replyPlaceholder')"
            :submit-label="t('topics.comment')"
            :submitting="replySubmitting"
            @submit="submitReply"
            @cancel="closeReply"
          />
        </div>
      </div>
    </div>

    <div v-if="hasChildren && !collapsed" class="children">
      <TopicCommentThread
        v-for="child in comment.children"
        :key="child.commentId"
        :comment="child"
        :post-id="postId"
        :depth="depth + 1"
        @changed="$emit('changed')"
      />
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import { createTopicComment, upvoteTopicComment } from '@/api/topic'
import TopicCommentBar from '@/components/TopicCommentBar.vue'
import { letterAvatarDataUri, resolveAvatarSrc } from '@/utils/avatar'

const props = defineProps({
  comment: { type: Object, required: true },
  postId: { type: [Number, String], required: true },
  depth: { type: Number, default: 0 }
})

const emit = defineEmits(['changed'])

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const collapsed = ref(false)
const replying = ref(false)
const replyText = ref('')
const replyImage = ref('')
const replySubmitting = ref(false)
const avatarBroken = ref(false)

const hasChildren = computed(() => (props.comment.children || []).length > 0)

const authorName = computed(() => {
  return props.comment.nickname
    || (props.comment.userId ? `用户 #${props.comment.userId}` : t('topics.anonymous'))
})

const avatarLetter = computed(() => {
  const name = authorName.value || '?'
  return name.slice(0, 1).toUpperCase()
})

const avatarSrc = computed(() => {
  if (avatarBroken.value) return letterAvatarDataUri(authorName.value)
  return resolveAvatarSrc(props.comment.avatar, authorName.value)
})

const canOpenAuthor = computed(() => props.comment.userId != null)

function onAvatarError() {
  avatarBroken.value = true
}

function onOpenAuthor() {
  if (!canOpenAuthor.value) return
  router.push(`/users/${props.comment.userId}`)
}

function relativeTime(value) {
  if (!value) return ''
  const date = new Date(String(value).replace(' ', 'T'))
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ')
  const diffMs = Date.now() - date.getTime()
  const mins = Math.floor(diffMs / 60000)
  if (mins < 1) return t('topics.justNow')
  if (mins < 60) return t('topics.minutesAgo').replace('{n}', String(mins))
  const hours = Math.floor(mins / 60)
  if (hours < 24) return t('topics.hoursAgo').replace('{n}', String(hours))
  const days = Math.floor(hours / 24)
  if (days < 30) return t('topics.daysAgo').replace('{n}', String(days))
  return String(value).replace('T', ' ').slice(0, 16)
}

function ensureLogin() {
  if (userStore.isLogin) return true
  router.push({ path: '/login', query: { redirect: `/topics/${props.postId}` } })
  return false
}

async function onUpvote() {
  if (!ensureLogin()) return
  try {
    const res = await upvoteTopicComment(props.comment.commentId)
    props.comment.upvoteCount = res.data?.upvoteCount ?? props.comment.upvoteCount
    props.comment.upvoted = !!res.data?.upvoted
  } catch {
    // ignore
  }
}

function toggleReply() {
  if (!ensureLogin()) return
  replying.value = !replying.value
}

function closeReply() {
  replying.value = false
  replyText.value = ''
  replyImage.value = ''
}

async function submitReply() {
  const content = replyText.value.trim()
  if (!content) return
  replySubmitting.value = true
  try {
    await createTopicComment(props.postId, {
      parentId: props.comment.commentId,
      content,
      imageUrl: replyImage.value || undefined
    })
    ElMessage.success(t('topics.commentDone'))
    closeReply()
    emit('changed')
  } finally {
    replySubmitting.value = false
  }
}

async function onShare() {
  const url = `${window.location.origin}/topics/${props.postId}#comment-${props.comment.commentId}`
  try {
    await navigator.clipboard.writeText(url)
    ElMessage.success(t('topics.linkCopied'))
  } catch {
    ElMessage.info(url)
  }
}
</script>

<style scoped>
.thread {
  position: relative;
}

.thread.nested {
  margin-left: 12px;
  padding-left: 14px;
  border-left: 2px solid var(--oa-border-subtle);
}

.thread-main {
  display: flex;
  gap: 8px;
  padding: 10px 0 4px;
}

.collapse-btn {
  flex-shrink: 0;
  width: 20px;
  height: 20px;
  margin-top: 4px;
  border: none;
  border-radius: 50%;
  background: var(--oa-bg-page, #fff);
  color: var(--oa-text-muted);
  cursor: pointer;
  font-size: 12px;
  line-height: 1;
}

.collapse-spacer {
  width: 20px;
  flex-shrink: 0;
}

.thread-body {
  min-width: 0;
  flex: 1;
}

.comment-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
}

.avatar {
  width: 28px;
  height: 28px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--oa-bg-elevated);
  color: var(--oa-text-secondary);
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
  border: 0;
  padding: 0;
  cursor: default;
}

.avatar.clickable {
  cursor: pointer;
}

.avatar.clickable:hover {
  outline: 1px solid var(--el-color-primary);
}

.avatar-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: block;
}

.author {
  font-size: 13px;
  font-weight: 600;
  color: var(--oa-text);
}

.author.link {
  border: 0;
  background: transparent;
  padding: 0;
  cursor: pointer;
  font: inherit;
  font-weight: 600;
  color: var(--oa-text);
}

.author.link:hover {
  color: var(--el-color-primary);
}

.time {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.comment-text {
  margin: 0 0 8px;
  white-space: pre-wrap;
  line-height: 1.6;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

.comment-image {
  width: 160px;
  height: 160px;
  border-radius: 10px;
  margin-bottom: 8px;
  border: none;
}

.comment-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin-bottom: 4px;
}

.action-btn {
  border: 0;
  background: transparent;
  color: var(--oa-text-muted);
  font-size: 12px;
  padding: 4px 8px;
  border-radius: 999px;
  cursor: pointer;
  display: inline-flex;
  align-items: center;
  gap: 4px;
}

.action-btn:hover {
  background: var(--oa-bg-elevated);
  color: var(--oa-text);
}

.action-btn.active {
  color: var(--el-color-primary);
}

.caret {
  font-size: 10px;
}

.reply-box {
  margin: 8px 0 4px;
}

.children {
  margin-top: 2px;
}
</style>
