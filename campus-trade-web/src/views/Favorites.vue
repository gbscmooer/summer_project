<template>
  <div class="page-container favorites-page">
    <button class="oa-back-btn" @click="$router.back()">
      <el-icon><ArrowLeft /></el-icon>
      {{ t('favorites.back') }}
    </button>

    <div class="page-header">
      <h1 class="page-title">{{ t('favorites.title') }}</h1>
      <p class="page-subtitle">{{ t('favorites.subtitle') }}</p>
    </div>

    <div v-loading="loading" class="product-grid">
      <div
        v-for="item in list"
        :key="item.productId"
        class="product-card"
        @click="goProduct(item.productId)"
      >
        <div class="product-cover">
          <el-image :src="item.cover" fit="cover" class="product-img">
            <template #error>
              <div class="cover-placeholder">
                <el-icon :size="28"><Picture /></el-icon>
              </div>
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
      <el-empty
        v-if="!loading && list.length === 0"
        :description="t('favorites.empty')"
      />
    </div>

    <div v-if="total > list.length" class="oa-pagination">
      <el-button :loading="loading" @click="loadMore">{{ t('favorites.loadMore') }}</el-button>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Picture } from '@element-plus/icons-vue'
import { listMyFavorites } from '@/api/favorite'
import { useI18n } from '@/i18n'

const router = useRouter()
const { t } = useI18n()

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(20)

function formatPoints(price) {
  const n = Number(price)
  const value = Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(0)) : String(price ?? 0)
  return `${value} ${t('common.pointsUnit')}`
}

function goProduct(id) {
  if (!id) return
  router.push(`/product/${id}`)
}

async function fetchList(reset = false) {
  if (reset) {
    pageNum.value = 1
    list.value = []
  }
  loading.value = true
  try {
    const res = await listMyFavorites({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    const page = res.data || {}
    total.value = page.total || 0
    const rows = page.list || []
    list.value = reset ? rows : [...list.value, ...rows]
  } catch {
    if (reset) {
      list.value = []
      total.value = 0
    }
  } finally {
    loading.value = false
  }
}

function loadMore() {
  if (loading.value || list.value.length >= total.value) return
  pageNum.value += 1
  fetchList(false)
}

onMounted(() => fetchList(true))
</script>

<style scoped>
.favorites-page .page-header {
  margin-bottom: 20px;
}

.page-subtitle {
  margin: 6px 0 0;
  color: var(--oa-text-muted);
  font-size: 14px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.product-card {
  cursor: pointer;
  border-radius: 12px;
  overflow: hidden;
  background: var(--oa-bg-panel, var(--oa-bg-elevated));
  border: 1px solid var(--oa-border, transparent);
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.product-card:hover {
  transform: translateY(-2px);
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
  padding: 10px 12px 14px;
}

.product-title {
  font-size: 14px;
  font-weight: 600;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  margin-bottom: 8px;
}

.product-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.product-price {
  color: var(--oa-accent, #c45c26);
  font-weight: 600;
}

.product-cat {
  color: var(--oa-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.oa-pagination {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
