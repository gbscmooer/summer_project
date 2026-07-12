<template>
  <div class="page-container topics-page">
    <div class="page-header topics-header">
      <div>
        <h1 class="page-title">{{ t('topics.title') }}</h1>
        <p class="page-subtitle">{{ t('topics.subtitle') }}</p>
      </div>
      <el-button type="primary" @click="openCompose">
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
        <p class="post-preview">{{ post.content }}</p>
        <div class="post-meta">
          <span>{{ authorName(post) }}</span>
          <span class="meta-dot">·</span>
          <span>{{ formatTime(post.createTime) }}</span>
        </div>
      </article>

      <div v-if="total > posts.length" class="oa-pagination">
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

    <el-dialog
      v-model="composeVisible"
      :title="t('topics.composeTitle')"
      width="640px"
      destroy-on-close
      @closed="resetCompose"
    >
      <el-form label-position="top" class="compose-form">
        <el-form-item :label="t('topics.postTitle')" required>
          <el-input v-model="composeForm.title" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('topics.postContent')" required>
          <el-input
            v-model="composeForm.content"
            type="textarea"
            :rows="6"
            maxlength="2000"
            show-word-limit
            :placeholder="t('topics.postContentPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('topics.attachProducts')">
          <p class="field-hint">{{ t('topics.attachProductsHint') }}</p>
          <div v-loading="productsLoading" class="product-picker">
            <el-empty
              v-if="!productsLoading && myProducts.length === 0"
              :description="t('topics.noProducts')"
            />
            <el-checkbox-group v-else v-model="composeForm.productIds" :max="5">
              <label
                v-for="item in myProducts"
                :key="item.productId"
                class="product-option"
              >
                <el-checkbox :value="item.productId" />
                <el-image v-if="item.cover" :src="item.cover" fit="cover" class="product-thumb" />
                <div v-else class="product-thumb placeholder" />
                <div class="product-info">
                  <span class="product-title">{{ item.title }}</span>
                  <span class="product-price">¥{{ formatPrice(item.price) }}</span>
                </div>
              </label>
            </el-checkbox-group>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="composeVisible = false">{{ t('topics.cancel') }}</el-button>
        <el-button type="primary" :loading="submitting" @click="submitPost">
          {{ t('topics.publish') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import { listTopicPosts, createTopicPost } from '@/api/topic'
import { getMyProducts } from '@/api/product'

const { t } = useI18n()
const router = useRouter()
const userStore = useUserStore()

const loading = ref(false)
const posts = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)

const composeVisible = ref(false)
const submitting = ref(false)
const productsLoading = ref(false)
const myProducts = ref([])
const composeForm = reactive({
  title: '',
  content: '',
  productIds: []
})

onMounted(fetchPosts)

function authorName(post) {
  return post.nickname || (post.userId ? `用户 #${post.userId}` : t('topics.anonymous'))
}

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

function goDetail(id) {
  router.push(`/topics/${id}`)
}

async function fetchPosts() {
  loading.value = true
  try {
    const res = await listTopicPosts({ pageNum: pageNum.value, pageSize: pageSize.value })
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

function onSizeChange() {
  pageNum.value = 1
  fetchPosts()
}

function resetCompose() {
  composeForm.title = ''
  composeForm.content = ''
  composeForm.productIds = []
  myProducts.value = []
}

async function openCompose() {
  if (!userStore.isLogin) {
    router.push({ path: '/login', query: { redirect: '/topics' } })
    return
  }
  composeVisible.value = true
  productsLoading.value = true
  try {
    const res = await getMyProducts({ pageNum: 1, pageSize: 50 })
    const list = res.data?.list || []
    myProducts.value = list.filter((p) => p.status !== 0)
  } catch {
    myProducts.value = []
  } finally {
    productsLoading.value = false
  }
}

async function submitPost() {
  const title = composeForm.title.trim()
  const content = composeForm.content.trim()
  if (!title) {
    ElMessage.warning(t('topics.titleRequired'))
    return
  }
  if (!content) {
    ElMessage.warning(t('topics.contentRequired'))
    return
  }
  submitting.value = true
  try {
    const res = await createTopicPost({
      title,
      content,
      productIds: composeForm.productIds
    })
    ElMessage.success(res.message || t('topics.publishDone'))
    composeVisible.value = false
    pageNum.value = 1
    await fetchPosts()
    const postId = res.data?.postId
    if (postId) {
      router.push(`/topics/${postId}`)
    }
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
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

.compose-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.field-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.45;
}

.product-picker {
  max-height: 280px;
  overflow: auto;
  border: 1px solid var(--oa-border-subtle);
  border-radius: var(--oa-radius);
  padding: 8px;
}

.product-option {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 8px;
  cursor: pointer;
}

.product-option:hover {
  background: var(--oa-bg-elevated);
}

.product-thumb {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  flex-shrink: 0;
  background: var(--oa-bg-elevated);
}

.product-thumb.placeholder {
  display: block;
}

.product-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.product-title {
  font-size: 13px;
  color: var(--oa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  font-size: 12px;
  color: var(--oa-text-secondary);
}
</style>
