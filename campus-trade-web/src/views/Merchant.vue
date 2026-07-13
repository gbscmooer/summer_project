<template>
  <div class="page-container merchant-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('merchant.title') }}</h1>
      <p class="page-subtitle">{{ t('merchant.subtitle') }}</p>
    </div>

    <!-- 商家数据中心 -->
    <div class="oa-panel merchant-dashboard" v-loading="dashboardLoading">
      <div class="oa-panel-header">
        <span class="oa-panel-title">{{ t('merchant.dashboard') }}</span>
      </div>

      <div class="stats-grid">
        <div class="stat-card highlight">
          <span class="stat-label">{{ t('merchant.totalRevenue') }}</span>
          <span class="stat-value">{{ formatPoints(orderDashboard?.summary?.totalRevenue || 0) }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('merchant.completedOrders') }}</span>
          <span class="stat-value">{{ orderDashboard?.summary?.completedOrders ?? 0 }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('merchant.pendingShipment') }}</span>
          <span class="stat-value">{{ orderDashboard?.summary?.pendingShipmentOrders ?? 0 }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('merchant.pendingPayment') }}</span>
          <span class="stat-value">{{ orderDashboard?.summary?.pendingPaymentOrders ?? 0 }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('merchant.activeListings') }}</span>
          <span class="stat-value">{{ productDashboard?.summary?.activeListings ?? 0 }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('merchant.soldListings') }}</span>
          <span class="stat-value">{{ productDashboard?.summary?.soldListings ?? 0 }}</span>
        </div>
        <div class="stat-card">
          <span class="stat-label">{{ t('merchant.totalViews') }}</span>
          <span class="stat-value">{{ productDashboard?.summary?.totalViews ?? 0 }}</span>
        </div>
      </div>

      <div class="charts-grid">
        <div class="chart-card">
          <h3 class="chart-title">{{ t('activity.revenueTrend') }}</h3>
          <v-chart class="chart" :option="revenueChartOption" autoresize />
        </div>
        <div class="chart-card">
          <h3 class="chart-title">{{ t('activity.orderStatusPie') }}</h3>
          <v-chart class="chart chart-pie" :option="orderPieOption" autoresize />
        </div>
        <div class="chart-card">
          <h3 class="chart-title">{{ t('activity.categoryPie') }}</h3>
          <v-chart class="chart chart-pie" :option="categoryPieOption" autoresize />
        </div>
      </div>
    </div>

    <!-- 我发布的商品 -->
    <div class="oa-panel listings-panel">
      <div class="oa-panel-header">
        <span class="oa-panel-title">{{ t('activity.myListings') }}</span>
        <el-button type="primary" size="small" @click="$router.push('/publish')">
          {{ t('activity.newListing') }}
        </el-button>
      </div>

      <div v-if="quota" class="quota-hint">
        <el-alert
          :title="quota.unlimited ? t('merchant.quotaUnlimited') : quotaText"
          :type="quota.unlimited ? 'success' : (quota.remaining > 0 ? 'info' : 'warning')"
          :closable="false"
          show-icon
        />
      </div>

      <div v-loading="listLoading">
        <div v-if="!listLoading && list.length === 0" class="oa-empty-state">
          <p>{{ t('activity.noListings') }}</p>
          <el-button type="primary" @click="$router.push('/publish')">{{ t('activity.createListing') }}</el-button>
        </div>

        <el-table v-else :data="list" style="width: 100%">
          <el-table-column :label="t('activity.colCover')" width="80">
            <template #default="{ row }">
              <el-image :src="row.cover" fit="cover" class="thumb-img">
                <template #error>
                  <div class="thumb-placeholder">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </template>
          </el-table-column>
          <el-table-column prop="title" :label="t('activity.colTitle')" min-width="180" show-overflow-tooltip />
          <el-table-column :label="t('activity.colPrice')" width="110">
            <template #default="{ row }">
              <span class="oa-price">{{ formatPoints(row.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" :label="t('activity.colCategory')" width="100" />
          <el-table-column :label="t('activity.colStatus')" width="100">
            <template #default="{ row }">
              <span class="oa-status" :class="listingStatusClass(row.status)">
                {{ getStatusText(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('activity.colActions')" width="220" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/product/${row.productId}`)">
                {{ t('activity.view') }}
              </el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="row.status === 0"
                @click="openEditProduct(row)"
              >
                {{ t('activity.edit') }}
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="row.status === 0"
                @click="onDelist(row)"
              >
                {{ t('activity.delist') }}
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="total > 0" class="oa-pagination">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 30]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="fetchMyProducts"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </div>

    <!-- 编辑商品弹窗 -->
    <el-dialog v-model="productEditVisible" :title="t('activity.editListing')" width="640px">
      <el-form
        ref="productEditFormRef"
        :model="productEditForm"
        :rules="productEditRules"
        label-position="top"
        v-loading="productEditLoading"
      >
        <el-form-item :label="t('activity.colTitle')" prop="title">
          <el-input v-model="productEditForm.title" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item :label="t('activity.colCategory')" prop="category">
          <el-select v-model="productEditForm.category" :placeholder="t('activity.selectCategory')" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('activity.colPrice')" prop="price">
          <el-input-number
            v-model="productEditForm.price"
            :min="0.01"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item :label="t('merchant.stock')" prop="stock">
          <el-input-number
            v-model="productEditForm.stock"
            :min="1"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="商品图片" prop="imageList">
          <ImageUploadGallery v-model="productEditForm.imageList" :max-count="5" />
          <p class="edit-hint">仅支持本地上传，不可填写外链；超过 1MB 自动压缩。</p>
        </el-form-item>
        <el-form-item :label="t('topics.postContent')" prop="description">
          <el-input v-model="productEditForm.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productEditVisible = false">{{ t('settings.cancel') }}</el-button>
        <el-button type="primary" :loading="productSaving" @click="onSaveProductEdit">{{ t('settings.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { getUserInfo } from '@/api/user'
import {
  getMyProducts,
  getProductDetail,
  updateProduct,
  deleteProduct,
  getPublishQuota,
  getSellerProductDashboard
} from '@/api/product'
import { getSellerDashboard } from '@/api/order'
import { getStatusText, CATEGORIES } from '@/constants/product'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'
import ImageUploadGallery from '@/components/ImageUploadGallery.vue'

use([CanvasRenderer, PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const { t } = useI18n()
const userStore = useUserStore()
const categories = CATEGORIES

const orderDashboard = ref(null)
const productDashboard = ref(null)
const dashboardLoading = ref(false)

const quota = ref(null)
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const listLoading = ref(false)

const quotaText = computed(() => {
  if (!quota.value || quota.value.unlimited) return ''
  return t('merchant.quotaHint')
    .replace('{limit}', String(quota.value.limit))
    .replace('{used}', String(quota.value.used))
    .replace('{remaining}', String(quota.value.remaining))
})

const revenueChartOption = computed(() => {
  const points = orderDashboard.value?.dailyRevenue || []
  return {
    color: ['#10a37f'],
    grid: { left: 48, right: 16, top: 24, bottom: 32 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: points.map((p) => p.date.slice(5)),
      axisLabel: { color: '#9b9b9b', fontSize: 10 },
      axisLine: { lineStyle: { color: '#424242' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9b9b9b', fontSize: 10 },
      splitLine: { lineStyle: { color: '#333' } }
    },
    series: [{
      type: 'line',
      smooth: true,
      data: points.map((p) => Number(p.revenue || 0)),
      areaStyle: { color: 'rgba(16, 163, 127, 0.15)' }
    }]
  }
})

const orderPieOption = computed(() => {
  const items = orderDashboard.value?.orderStatusBreakdown || []
  return {
    color: ['#f59e0b', '#3b82f6', '#10a37f', '#6b7280'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#9b9b9b', fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '45%'],
      data: items.filter((i) => i.count > 0).map((i) => ({
        name: i.statusText,
        value: i.count
      })),
      label: { color: '#ececec', fontSize: 11 }
    }]
  }
})

const categoryPieOption = computed(() => {
  const items = productDashboard.value?.categoryBreakdown || []
  return {
    color: ['#10a37f', '#3b82f6', '#f59e0b', '#8b5cf6', '#ef4444', '#06b6d4'],
    tooltip: { trigger: 'item' },
    legend: { bottom: 0, textStyle: { color: '#9b9b9b', fontSize: 11 } },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      center: ['50%', '45%'],
      data: items.map((i) => ({ name: i.category, value: i.count })),
      label: { color: '#ececec', fontSize: 11 }
    }]
  }
})

function formatPoints(price) {
  const n = Number(price)
  const value = Number.isFinite(n) ? (Number.isInteger(n) ? String(n) : n.toFixed(0)) : String(price ?? 0)
  return `${value} ${t('common.pointsUnit')}`
}

function listingStatusClass(status) {
  const map = { 0: 'oa-status-info', 1: 'oa-status-success', 2: 'oa-status-danger' }
  return map[status] || 'oa-status-info'
}

async function fetchDashboard() {
  dashboardLoading.value = true
  try {
    const [orderRes, productRes] = await Promise.all([
      getSellerDashboard(),
      getSellerProductDashboard()
    ])
    orderDashboard.value = orderRes.data
    productDashboard.value = productRes.data
  } catch {
    orderDashboard.value = null
    productDashboard.value = null
  } finally {
    dashboardLoading.value = false
  }
}

async function fetchQuota() {
  try {
    const res = await getPublishQuota()
    quota.value = res.data
  } catch {
    quota.value = null
  }
}

async function fetchMyProducts() {
  listLoading.value = true
  try {
    const res = await getMyProducts({ pageNum: pageNum.value, pageSize: pageSize.value })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    listLoading.value = false
  }
}

function onSizeChange() {
  pageNum.value = 1
  fetchMyProducts()
}

const productEditVisible = ref(false)
const productEditLoading = ref(false)
const productSaving = ref(false)
const productEditFormRef = ref(null)
const editingProductId = ref(null)
const productEditForm = reactive({
  title: '',
  category: '',
  price: 0,
  stock: 1,
  imageList: [],
  description: ''
})

const productEditRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (value === null || value === undefined) callback(new Error('请输入价格'))
        else if (Number(value) <= 0) callback(new Error('价格必须大于 0'))
        else callback()
      },
      trigger: 'blur'
    }
  ],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  imageList: [
    {
      required: true,
      validator: (_rule, value, callback) => {
        if (!Array.isArray(value) || value.length === 0) {
          callback(new Error('请至少上传一张商品图片'))
        } else {
          callback()
        }
      },
      trigger: 'change'
    }
  ]
}

async function openEditProduct(row) {
  editingProductId.value = row.productId
  productEditVisible.value = true
  productEditLoading.value = true
  try {
    const res = await getProductDetail(row.productId)
    const data = res.data || {}
    productEditForm.title = data.title || ''
    productEditForm.category = data.category || ''
    productEditForm.price = data.price || 0
    productEditForm.stock = data.stock || 1
    productEditForm.description = data.description || ''
    productEditForm.imageList = Array.isArray(data.images) ? [...data.images] : []
  } catch {
    productEditVisible.value = false
  } finally {
    productEditLoading.value = false
  }
}

async function onSaveProductEdit() {
  if (!productEditFormRef.value || !editingProductId.value) return
  try {
    await productEditFormRef.value.validate()
  } catch {
    return
  }
  productSaving.value = true
  try {
    await updateProduct(editingProductId.value, {
      title: productEditForm.title,
      description: productEditForm.description,
      price: productEditForm.price,
      images: productEditForm.imageList.join(','),
      category: productEditForm.category,
      stock: productEditForm.stock
    })
    ElMessage.success(t('activity.listingUpdated'))
    productEditVisible.value = false
    fetchMyProducts()
    fetchDashboard()
  } finally {
    productSaving.value = false
  }
}

function onDelist(row) {
  ElMessageBox.confirm(t('activity.delistConfirm'), t('settings.tip'), {
    confirmButtonText: t('activity.delist'),
    cancelButtonText: t('settings.cancel'),
    type: 'warning'
  })
    .then(async () => {
      await deleteProduct(row.productId)
      ElMessage.success(t('activity.delistDone'))
      if (list.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
      fetchMyProducts()
      fetchDashboard()
    })
    .catch(() => {})
}

onMounted(async () => {
  if (userStore.isLogin) {
    try {
      const infoRes = await getUserInfo()
      userStore.setUserInfo(infoRes.data)
    } catch {
      // ignore
    }
  }
  fetchDashboard()
  fetchQuota()
  fetchMyProducts()
})
</script>

<style scoped>
.page-subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

.merchant-dashboard {
  margin-bottom: 16px;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(140px, 1fr));
  gap: 12px;
  margin-bottom: 20px;
}

.stat-card {
  padding: 14px;
  border-radius: 8px;
  background: var(--oa-bg-elevated);
  border: none;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.stat-card.highlight {
  border-color: var(--el-color-primary-light-5);
  background: var(--el-color-primary-light-9);
}

.stat-label {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.stat-value {
  font-size: 20px;
  font-weight: 600;
  color: var(--oa-text);
}

.charts-grid {
  display: grid;
  grid-template-columns: 2fr 1fr 1fr;
  gap: 16px;
}

.chart-card {
  padding: 16px;
  border-radius: 8px;
  background: var(--oa-bg-elevated);
  border: none;
}

.chart-title {
  margin: 0 0 8px;
  font-size: 13px;
  font-weight: 500;
  color: var(--oa-text-secondary);
}

.chart {
  height: 220px;
  width: 100%;
}

.quota-hint {
  margin-bottom: 12px;
}

.edit-hint {
  margin: 8px 0 0;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.45;
}

.thumb-img {
  width: 48px;
  height: 48px;
  border-radius: 6px;
  display: block;
}

.thumb-placeholder {
  width: 48px;
  height: 48px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--oa-bg-elevated);
  color: var(--oa-text-muted);
  border-radius: 6px;
}

@media (max-width: 1100px) {
  .charts-grid {
    grid-template-columns: 1fr;
  }
}
</style>
