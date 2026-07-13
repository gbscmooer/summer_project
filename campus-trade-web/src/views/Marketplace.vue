<template>
  <div class="page-container marketplace-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">{{ t('marketplace.title') }}</h1>
        <p class="page-subtitle">{{ t('marketplace.subtitle') }}</p>
      </div>
      <el-button
        v-if="userStore.canPublish"
        type="primary"
        size="small"
        @click="$router.push('/publish')"
      >
        {{ t('nav.publish') }}
      </el-button>
    </div>

    <div class="toolbar oa-panel">
      <el-input
        v-model="keyword"
        :placeholder="t('marketplace.searchPlaceholder')"
        clearable
        class="search-input"
        @keyup.enter="onSearch"
        @clear="onSearch"
      >
        <template #append>
          <el-button :icon="Search" @click="onSearch">{{ t('home.search') }}</el-button>
        </template>
      </el-input>
      <div class="cat-row">
        <button
          type="button"
          class="cat-btn"
          :class="{ active: !category }"
          @click="selectCategory('')"
        >
          {{ t('marketplace.allCategories') }}
        </button>
        <button
          v-for="c in categories"
          :key="c"
          type="button"
          class="cat-btn"
          :class="{ active: category === c }"
          @click="selectCategory(c)"
        >
          {{ c }}
        </button>
      </div>
    </div>

    <div v-loading="loading" class="product-grid">
      <div
        v-for="item in list"
        :key="item.productId"
        class="product-card"
        @click="$router.push(`/product/${item.productId}`)"
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
        :description="t('marketplace.empty')"
        class="empty-block"
      />
    </div>

    <div v-if="total > pageSize" class="pager">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        layout="prev, pager, next"
        :total="total"
        @current-change="fetchList"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Search, Picture } from '@element-plus/icons-vue'
import { getProductList, searchProducts } from '@/api/product'
import { CATEGORIES } from '@/constants/product'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'

const { t, locale } = useI18n()
const userStore = useUserStore()
const categories = CATEGORIES

const keyword = ref('')
const category = ref('')
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(12)
const loading = ref(false)

function formatPoints(price) {
  const n = Number(price) || 0
  const unit = locale.value === 'zh-CN' ? '积分' : 'pts'
  return `${n} ${unit}`
}

function selectCategory(c) {
  category.value = c
  pageNum.value = 1
  fetchList()
}

function onSearch() {
  pageNum.value = 1
  fetchList()
}

async function fetchList() {
  loading.value = true
  try {
    const params = {
      pageNum: pageNum.value,
      pageSize: pageSize.value,
      category: category.value || undefined
    }
    const kw = keyword.value.trim()
    const res = kw
      ? await searchProducts({ ...params, keyword: kw })
      : await getProductList(params)
    list.value = res.data?.list || []
    total.value = res.data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.page-subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

.toolbar {
  margin-bottom: 16px;
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.search-input {
  max-width: 520px;
}

.cat-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cat-btn {
  border: 1px solid var(--oa-border);
  background: transparent;
  color: var(--oa-text-secondary);
  border-radius: 999px;
  padding: 4px 12px;
  font-size: 13px;
  cursor: pointer;
}

.cat-btn.active {
  border-color: var(--oa-accent, #10a37f);
  color: var(--oa-accent, #10a37f);
  background: rgba(16, 163, 127, 0.08);
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 16px;
  min-height: 120px;
}

.product-card {
  border: none;
  border-radius: 12px;
  overflow: hidden;
  cursor: pointer;
  background: var(--oa-bg-sidebar, var(--oa-bg-elevated, #fff));
  transition: border-color 0.15s ease;
}

.product-card:hover {
  border-color: var(--oa-accent, #10a37f);
}

.product-cover {
  aspect-ratio: 4 / 3;
  background: var(--oa-bg-muted, #f4f4f4);
}

.product-img {
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oa-text-muted);
}

.product-info {
  padding: 12px;
}

.product-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--oa-text);
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 40px;
}

.product-meta {
  margin-top: 8px;
  display: flex;
  justify-content: space-between;
  gap: 8px;
  font-size: 13px;
}

.product-price {
  color: var(--oa-accent, #10a37f);
  font-weight: 600;
}

.product-cat {
  color: var(--oa-text-muted);
}

.empty-block {
  grid-column: 1 / -1;
}

.pager {
  margin-top: 20px;
  display: flex;
  justify-content: center;
}
</style>
