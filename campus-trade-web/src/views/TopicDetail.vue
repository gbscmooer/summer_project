<template>
  <div class="page-container" v-loading="loading">
    <button class="oa-back-btn" @click="$router.push('/topics')">
      <el-icon><ArrowLeft /></el-icon>
      {{ t('topics.back') }}
    </button>

    <el-empty v-if="!loading && !post" :description="t('topics.notFound')" />

    <template v-if="post">
      <div class="post-header">
        <h1 class="page-title">{{ post.title }}</h1>
        <div class="post-meta">
          <span>{{ authorName }}</span>
          <span class="meta-dot">·</span>
          <span>{{ formatTime(post.createTime) }}</span>
        </div>
        <el-button
          v-if="canDelete"
          size="small"
          type="danger"
          plain
          :loading="deleting"
          @click="onDelete"
        >
          {{ t('topics.delete') }}
        </el-button>
      </div>

      <div class="oa-panel content-panel">
        <p class="post-content">{{ post.content }}</p>
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
              <span class="product-price">¥{{ formatPrice(item.price) }}</span>
            </div>
          </div>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import { getTopicPost, deleteTopicPost } from '@/api/topic'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const loading = ref(false)
const deleting = ref(false)
const post = ref(null)

const products = computed(() => post.value?.products || [])

const authorName = computed(() => {
  if (!post.value) return ''
  return post.value.nickname || (post.value.userId ? `用户 #${post.value.userId}` : t('topics.anonymous'))
})

const canDelete = computed(() => {
  if (!post.value || !userStore.isLogin) return false
  return post.value.userId === userStore.userInfo?.userId
})

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

async function fetchPost() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getTopicPost(id)
    post.value = res.data
  } catch {
    post.value = null
  } finally {
    loading.value = false
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

watch(() => route.params.id, fetchPost)
onMounted(fetchPost)
</script>

<style scoped>
.post-header {
  margin-bottom: 20px;
}

.post-meta {
  margin-top: 8px;
  font-size: 13px;
  color: var(--oa-text-muted);
}

.meta-dot {
  margin: 0 6px;
}

.post-header .el-button {
  margin-top: 12px;
}

.post-content {
  margin: 0;
  white-space: pre-wrap;
  line-height: 1.75;
  font-size: 15px;
  color: var(--oa-text-secondary);
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
  border: 1px solid var(--oa-border-subtle);
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
</style>
