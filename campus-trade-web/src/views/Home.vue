<template>
  <div class="page-container">
    <!-- 搜索与筛选面板 -->
    <div class="search-panel">
      <div class="search-row">
        <el-input
          v-model="keyword"
          placeholder="请输入商品标题或描述进行搜索..."
          clearable
          class="search-input"
          @keyup.enter="handleSearch"
        >
          <template #prefix>
            <el-icon><Search /></el-icon>
          </template>
        </el-input>
        <el-button type="primary" class="search-btn" @click="handleSearch">搜索</el-button>
      </div>

      <div class="filter-row">
        <!-- 价格区间 -->
        <div class="filter-item price-filter">
          <span class="filter-label">价格区间：</span>
          <el-input-number
            v-model="minPrice"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="最低价"
            class="price-input"
          />
          <span class="price-split">-</span>
          <el-input-number
            v-model="maxPrice"
            :min="0"
            :precision="2"
            :controls="false"
            placeholder="最高价"
            class="price-input"
          />
          <el-button size="small" style="margin-left: 8px" @click="handleSearch">确定</el-button>
          <el-button size="small" type="info" plain @click="clearPriceFilter">重置</el-button>
        </div>

        <!-- 排序方式 -->
        <div class="filter-item sort-filter">
          <span class="filter-label">排序方式：</span>
          <el-select v-model="sort" placeholder="排序" size="small" style="width: 120px" @change="handleSearch">
            <el-option label="默认时间" value="" />
            <el-option label="价格升序" value="price_asc" />
            <el-option label="价格降序" value="price_desc" />
          </el-select>
        </div>
      </div>
    </div>

    <!-- 分类筛选 Tab -->
    <el-tabs v-model="activeCategory" class="category-tabs" @tab-change="onCategoryChange">
      <el-tab-pane label="全部" name="" />
      <el-tab-pane
        v-for="cat in categories"
        :key="cat"
        :label="cat"
        :name="cat"
      />
    </el-tabs>

    <!-- 商品网格 -->
    <div v-loading="loading" class="grid-wrapper">
      <el-empty v-if="!loading && list.length === 0" description="暂无商品" />

      <div v-else class="product-grid">
        <el-card
          v-for="item in list"
          :key="item.productId"
          class="product-card"
          shadow="hover"
          :body-style="{ padding: '0' }"
          @click="goDetail(item.productId)"
        >
          <div class="cover-box">
            <el-image :src="item.cover" fit="cover" class="cover-img" lazy>
              <template #error>
                <div class="cover-placeholder">
                  <el-icon :size="32"><Picture /></el-icon>
                </div>
              </template>
            </el-image>
          </div>
          <div class="card-info">
            <div class="card-title" :title="item.title">{{ item.title }}</div>
            <div class="card-meta">
              <span class="card-price">¥{{ formatPrice(item.price) }}</span>
              <el-tag size="small" type="info" effect="plain">{{ item.category }}</el-tag>
            </div>
          </div>
        </el-card>
      </div>
    </div>

    <!-- 分页 -->
    <div v-if="total > 0" class="pagination-wrapper">
      <el-pagination
        v-model:current-page="pageNum"
        v-model:page-size="pageSize"
        :total="total"
        :page-sizes="[10, 20, 30, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        background
        @current-change="fetchList"
        @size-change="onSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { Picture, Search } from '@element-plus/icons-vue'
import { getProductList, searchProducts } from '@/api/product'
import { CATEGORIES } from '@/constants/product'

const router = useRouter()
const categories = CATEGORIES

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const activeCategory = ref('')

// 搜索筛选字段
const keyword = ref('')
const minPrice = ref(null)
const maxPrice = ref(null)
const sort = ref('')

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
    // 仅在选择了具体分类时才传 category
    if (activeCategory.value) {
      params.category = activeCategory.value
    }
    
    // 如果存在搜索关键词、价格过滤或排序，走 ES 搜索；否则走标准 MySQL 分类列表
    const isSearching = keyword.value || minPrice.value !== null || maxPrice.value !== null || sort.value
    let res
    if (isSearching) {
      if (keyword.value) params.keyword = keyword.value
      if (minPrice.value !== null) params.minPrice = minPrice.value
      if (maxPrice.value !== null) params.maxPrice = maxPrice.value
      if (sort.value) params.sort = sort.value
      res = await searchProducts(params)
    } else {
      res = await getProductList(params)
    }
    
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
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

function clearPriceFilter() {
  minPrice.value = null
  maxPrice.value = null
  handleSearch()
}

function onCategoryChange() {
  // 切换分类后回到第一页
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

onMounted(fetchList)
</script>

<style scoped>
.search-panel {
  background-color: #fff;
  border-radius: 8px;
  padding: 16px;
  margin-bottom: 16px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.search-row {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.search-input {
  flex: 1;
}

.search-btn {
  width: 100px;
}

.filter-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
  align-items: center;
}

.filter-item {
  display: flex;
  align-items: center;
}

.filter-label {
  font-size: 14px;
  color: #606266;
  margin-right: 8px;
}

.price-filter {
  display: flex;
  align-items: center;
}

.price-input {
  width: 110px;
}

.price-split {
  margin: 0 6px;
  color: #dcdfe6;
}

.category-tabs {
  margin-bottom: 8px;
}

.grid-wrapper {
  min-height: 240px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.product-card {
  cursor: pointer;
  overflow: hidden;
  transition: transform 0.18s ease;
}

.product-card:hover {
  transform: translateY(-3px);
}

.cover-box {
  width: 100%;
  aspect-ratio: 1 / 1;
  background-color: #f5f7fa;
}

.cover-img {
  width: 100%;
  height: 100%;
  display: block;
}

.cover-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background-color: #f5f7fa;
}

.card-info {
  padding: 10px 12px 14px;
}

.card-title {
  font-size: 14px;
  line-height: 1.4;
  height: 40px;
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  margin-bottom: 8px;
}

.card-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-price {
  color: #f56c6c;
  font-size: 18px;
  font-weight: 700;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 28px;
}
</style>
