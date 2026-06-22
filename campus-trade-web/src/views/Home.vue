<template>
  <div class="page-container">
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
import { Picture } from '@element-plus/icons-vue'
import { getProductList } from '@/api/product'
import { CATEGORIES } from '@/constants/product'

const router = useRouter()
const categories = CATEGORIES

const loading = ref(false)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const activeCategory = ref('')

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
    const res = await getProductList(params)
    // res.data = { total, pageNum, pageSize, list: [...] }
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
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
