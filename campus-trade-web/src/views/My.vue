<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">Profile</h1>
      <el-button type="primary" size="small" @click="openEdit">Edit profile</el-button>
    </div>

    <!-- 个人信息 -->
    <div class="oa-panel profile-panel" v-loading="infoLoading">
      <div v-if="info" class="profile-row">
        <el-avatar :size="56" :src="info.avatar" class="profile-avatar">
          {{ (info.nickname || info.username || 'U').charAt(0) }}
        </el-avatar>
        <div class="profile-fields">
          <div class="field-row">
            <div class="field">
              <span class="field-label">Username</span>
              <span class="field-value">{{ info.username }}</span>
            </div>
            <div class="field">
              <span class="field-label">Nickname</span>
              <span class="field-value">{{ info.nickname }}</span>
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <span class="field-label">Phone</span>
              <span class="field-value">{{ info.phone || 'Not set' }}</span>
            </div>
            <div class="field">
              <span class="field-label">Joined</span>
              <span class="field-value">{{ info.createTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <!-- 我发布的 -->
    <div class="oa-panel listings-panel">
      <div class="oa-panel-header">
        <span class="oa-panel-title">My listings</span>
        <el-button type="primary" size="small" @click="$router.push('/publish')">
          New listing
        </el-button>
      </div>

      <div v-loading="listLoading">
        <div v-if="!listLoading && list.length === 0" class="oa-empty-state">
          <p>You haven't published any listings yet</p>
          <el-button type="primary" @click="$router.push('/publish')">Create listing</el-button>
        </div>

        <el-table v-else :data="list" style="width: 100%">
          <el-table-column label="Cover" width="80">
            <template #default="{ row }">
              <el-image
                :src="row.cover"
                fit="cover"
                class="thumb-img"
              >
                <template #error>
                  <div class="thumb-placeholder">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="Title" min-width="180" show-overflow-tooltip />
          <el-table-column label="Price" width="110">
            <template #default="{ row }">
              <span class="oa-price">¥{{ formatPrice(row.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="Category" width="100" />
          <el-table-column label="Status" width="100">
            <template #default="{ row }">
              <span class="oa-status" :class="listingStatusClass(row.status)">
                {{ getStatusText(row.status) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="Actions" width="220" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/product/${row.productId}`)">
                View
              </el-button>
              <el-button
                size="small"
                type="primary"
                :disabled="row.status === 0"
                @click="openEditProduct(row)"
              >
                Edit
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="row.status === 0"
                @click="onDelist(row)"
              >
                Delist
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

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editVisible" title="Edit profile" width="420px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item label="Nickname" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="Display name" />
        </el-form-item>
        <el-form-item label="Phone" prop="phone">
          <el-input v-model="editForm.phone" placeholder="Phone number" />
        </el-form-item>
        <el-form-item label="Avatar URL" prop="avatar">
          <el-input v-model="editForm.avatar" placeholder="Optional avatar image URL" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">Save</el-button>
      </template>
    </el-dialog>

    <!-- 编辑商品弹窗 -->
    <el-dialog v-model="productEditVisible" title="Edit listing" width="640px">
      <el-form
        ref="productEditFormRef"
        :model="productEditForm"
        :rules="productEditRules"
        label-position="top"
        v-loading="productEditLoading"
      >
        <el-form-item label="Title" prop="title">
          <el-input v-model="productEditForm.title" maxlength="50" show-word-limit />
        </el-form-item>
        <el-form-item label="Category" prop="category">
          <el-select v-model="productEditForm.category" placeholder="Select category" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="Price" prop="price">
          <el-input-number
            v-model="productEditForm.price"
            :min="0"
            :precision="2"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="Stock" prop="stock">
          <el-input-number
            v-model="productEditForm.stock"
            :min="1"
            :precision="0"
            :step="1"
            controls-position="right"
            style="width: 200px"
          />
        </el-form-item>
        <el-form-item label="Image URLs" prop="images">
          <el-input v-model="productEditForm.images" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item label="Description" prop="description">
          <el-input v-model="productEditForm.description" type="textarea" :rows="4" maxlength="500" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="productEditVisible = false">Cancel</el-button>
        <el-button type="primary" :loading="productSaving" @click="onSaveProductEdit">Save</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { getUserInfo, updateUserInfo } from '@/api/user'
import { getMyProducts, getProductDetail, updateProduct, deleteProduct } from '@/api/product'
import { getStatusText, CATEGORIES } from '@/constants/product'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()
const categories = CATEGORIES

const info = ref(null)
const infoLoading = ref(false)

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

function listingStatusClass(status) {
  const map = { 0: 'oa-status-info', 1: 'oa-status-success', 2: 'oa-status-danger' }
  return map[status] || 'oa-status-info'
}

async function fetchInfo() {
  infoLoading.value = true
  try {
    const res = await getUserInfo()
    info.value = res.data
    userStore.setUserInfo({
      nickname: res.data.nickname,
      avatar: res.data.avatar
    })
  } catch {
    info.value = null
  } finally {
    infoLoading.value = false
  }
}

const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const editForm = reactive({ nickname: '', phone: '', avatar: '' })

const editRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

function openEdit() {
  if (!info.value) return
  editForm.nickname = info.value.nickname || ''
  editForm.phone = info.value.phone || ''
  editForm.avatar = info.value.avatar || ''
  editVisible.value = true
}

async function onSaveEdit() {
  if (!editFormRef.value) return
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await updateUserInfo({
      nickname: editForm.nickname,
      avatar: editForm.avatar,
      phone: editForm.phone
    })
    ElMessage.success('Profile updated')
    editVisible.value = false
    await fetchInfo()
  } finally {
    saving.value = false
  }
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
  images: '',
  description: ''
})

const productEditRules = {
  title: [{ required: true, message: '请输入标题', trigger: 'blur' }],
  category: [{ required: true, message: '请选择分类', trigger: 'change' }],
  price: [{ required: true, message: '请输入价格', trigger: 'blur' }],
  stock: [{ required: true, message: '请输入库存', trigger: 'blur' }],
  images: [{ required: true, message: '请至少填写一张图片URL', trigger: 'blur' }]
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
    productEditForm.images = Array.isArray(data.images) ? data.images.join(',') : ''
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
    const cleanImages = productEditForm.images
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .join(',')
    await updateProduct(editingProductId.value, {
      title: productEditForm.title,
      description: productEditForm.description,
      price: productEditForm.price,
      images: cleanImages,
      category: productEditForm.category,
      stock: productEditForm.stock
    })
    ElMessage.success('Listing updated')
    productEditVisible.value = false
    fetchMyProducts()
  } finally {
    productSaving.value = false
  }
}

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const listLoading = ref(false)

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

function onDelist(row) {
  ElMessageBox.confirm(`Delist "${row.title}"?`, 'Confirm delist', {
    confirmButtonText: 'Delist',
    cancelButtonText: 'Cancel',
    type: 'warning'
  })
    .then(async () => {
      await deleteProduct(row.productId)
      ElMessage.success('Delisted')
      if (list.value.length === 1 && pageNum.value > 1) pageNum.value -= 1
      fetchMyProducts()
    })
    .catch(() => {})
}

onMounted(() => {
  fetchInfo()
  fetchMyProducts()
})
</script>

<style scoped>
.profile-panel {
  margin-bottom: 16px;
}

.profile-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.profile-avatar {
  flex-shrink: 0;
  font-size: 20px;
  font-weight: 600;
}

.profile-fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--oa-text-muted);
  margin-bottom: 4px;
}

.field-value {
  font-size: 14px;
  color: var(--oa-text);
}

.listings-panel {
  margin-top: 0;
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

@media (max-width: 640px) {
  .field-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .profile-row {
    flex-direction: column;
  }
}
</style>
