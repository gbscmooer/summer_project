<template>
  <div class="page-container" v-loading="loading">
    <el-page-header v-if="detail" class="detail-header" @back="$router.back()">
      <template #content>
        <span class="header-title">商品详情</span>
      </template>
    </el-page-header>

    <el-empty v-if="!loading && !detail" description="商品不存在或已下架" />

    <el-card v-if="detail" class="detail-card">
      <div class="detail-body">
        <!-- 左侧：图片轮播 -->
        <div class="gallery">
          <el-carousel
            v-if="images.length > 0"
            :autoplay="false"
            height="380px"
            indicator-position="outside"
            :arrow="images.length > 1 ? 'hover' : 'never'"
          >
            <el-carousel-item v-for="(img, idx) in images" :key="idx">
              <el-image :src="img" fit="contain" class="gallery-img">
                <template #error>
                  <div class="gallery-placeholder">
                    <el-icon :size="48"><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </el-carousel-item>
          </el-carousel>
          <div v-else class="gallery-placeholder gallery-empty">
            <el-icon :size="48"><Picture /></el-icon>
          </div>
        </div>

        <!-- 右侧：信息 -->
        <div class="info">
          <h1 class="title">{{ detail.title }}</h1>

          <div class="price-box">
            <span class="price-label">价格</span>
            <span class="price">¥{{ formatPrice(detail.price) }}</span>
          </div>

          <el-descriptions :column="1" border class="meta-desc">
            <el-descriptions-item label="分类">
              <el-tag size="small" type="info" effect="plain">{{ detail.category }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag size="small" :type="getStatusType(detail.status)">
                {{ getStatusText(detail.status) }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="库存">{{ detail.stock }}</el-descriptions-item>
            <el-descriptions-item label="卖家">{{ detail.sellerNickname }}</el-descriptions-item>
            <el-descriptions-item label="浏览量">{{ detail.viewCount }}</el-descriptions-item>
          </el-descriptions>

          <div class="buy-box">
            <el-button
              type="danger"
              size="large"
              :icon="ShoppingCart"
              @click="onBuy"
            >
              立即购买
            </el-button>
          </div>
        </div>
      </div>

      <!-- 商品描述 -->
      <el-divider content-position="left">商品描述</el-divider>
      <div class="description">{{ detail.description || '卖家很懒，没有填写描述～' }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Picture, ShoppingCart } from '@element-plus/icons-vue'
import { getProductDetail } from '@/api/product'
import { getStatusText, getStatusType } from '@/constants/product'

const route = useRoute()
const loading = ref(false)
const detail = ref(null)

const images = computed(() => {
  if (!detail.value || !Array.isArray(detail.value.images)) return []
  return detail.value.images.filter((u) => !!u)
})

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

async function fetchDetail() {
  const id = route.params.id
  if (!id) return
  loading.value = true
  try {
    const res = await getProductDetail(id)
    detail.value = res.data
  } catch (e) {
    detail.value = null
  } finally {
    loading.value = false
  }
}

// 订单功能尚未开发，先占位提示
function onBuy() {
  ElMessage.info('订单功能开发中')
}

// 支持在详情页之间切换（路由参数变化时重新拉取）
watch(
  () => route.params.id,
  () => fetchDetail()
)

onMounted(fetchDetail)
</script>

<style scoped>
.detail-header {
  margin-bottom: 16px;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
}

.detail-body {
  display: flex;
  gap: 28px;
  flex-wrap: wrap;
}

.gallery {
  flex: 0 0 420px;
  max-width: 100%;
}

.gallery-img {
  width: 100%;
  height: 100%;
  background-color: #f5f7fa;
}

.gallery-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #c0c4cc;
  background-color: #f5f7fa;
}

.gallery-empty {
  height: 380px;
  border-radius: 4px;
}

.info {
  flex: 1;
  min-width: 280px;
  display: flex;
  flex-direction: column;
}

.title {
  font-size: 22px;
  line-height: 1.4;
  margin-bottom: 16px;
  color: #303133;
}

.price-box {
  background-color: #fef0f0;
  border-radius: 6px;
  padding: 14px 16px;
  margin-bottom: 16px;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.price-label {
  color: #909399;
  font-size: 13px;
}

.price {
  color: #f56c6c;
  font-size: 28px;
  font-weight: 700;
}

.meta-desc {
  margin-bottom: 20px;
}

.buy-box {
  margin-top: auto;
}

.description {
  white-space: pre-wrap;
  line-height: 1.8;
  color: #606266;
  font-size: 14px;
}
</style>
