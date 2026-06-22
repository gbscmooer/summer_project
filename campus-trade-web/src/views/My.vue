<template>
  <div class="page-container">
    <!-- 个人信息 -->
    <el-card class="info-card" v-loading="infoLoading">
      <template #header>
        <div class="card-header">
          <span class="card-title">个人信息</span>
          <el-button text type="primary" @click="openEdit">编辑资料</el-button>
        </div>
      </template>

      <div v-if="info" class="user-info">
        <el-avatar :size="64" :src="info.avatar">
          {{ (info.nickname || info.username || 'U').charAt(0) }}
        </el-avatar>
        <el-descriptions :column="2" class="info-desc">
          <el-descriptions-item label="用户名">{{ info.username }}</el-descriptions-item>
          <el-descriptions-item label="昵称">{{ info.nickname }}</el-descriptions-item>
          <el-descriptions-item label="手机号">{{ info.phone || '未填写' }}</el-descriptions-item>
          <el-descriptions-item label="注册时间">{{ info.createTime }}</el-descriptions-item>
        </el-descriptions>
      </div>
    </el-card>

    <!-- 我发布的 -->
    <el-card class="list-card">
      <template #header>
        <span class="card-title">我发布的商品</span>
      </template>

      <div v-loading="listLoading">
        <el-empty v-if="!listLoading && list.length === 0" description="你还没有发布过商品">
          <el-button type="primary" @click="$router.push('/publish')">去发布</el-button>
        </el-empty>

        <el-table v-else :data="list" style="width: 100%">
          <el-table-column label="封面" width="90">
            <template #default="{ row }">
              <el-image
                :src="row.cover"
                fit="cover"
                style="width: 60px; height: 60px; border-radius: 4px"
              >
                <template #error>
                  <div class="thumb-placeholder">
                    <el-icon><Picture /></el-icon>
                  </div>
                </template>
              </el-image>
            </template>
          </el-table-column>
          <el-table-column prop="title" label="标题" min-width="180" show-overflow-tooltip />
          <el-table-column label="价格" width="120">
            <template #default="{ row }">
              <span class="price">¥{{ formatPrice(row.price) }}</span>
            </template>
          </el-table-column>
          <el-table-column prop="category" label="分类" width="90" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag size="small" :type="getStatusType(row.status)">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button size="small" @click="$router.push(`/product/${row.productId}`)">
                查看
              </el-button>
              <el-button
                size="small"
                type="danger"
                :disabled="row.status === 0"
                @click="onDelist(row)"
              >
                下架
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <div v-if="total > 0" class="pagination-wrapper">
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
    </el-card>

    <!-- 编辑资料弹窗 -->
    <el-dialog v-model="editVisible" title="编辑资料" width="420px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="70px">
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model="editForm.nickname" placeholder="请输入昵称" />
        </el-form-item>
        <el-form-item label="手机号" prop="phone">
          <el-input v-model="editForm.phone" placeholder="请输入手机号" />
        </el-form-item>
        <el-form-item label="头像URL" prop="avatar">
          <el-input v-model="editForm.avatar" placeholder="头像图片链接（可选）" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Picture } from '@element-plus/icons-vue'
import { getUserInfo, updateUserInfo } from '@/api/user'
import { getMyProducts, deleteProduct } from '@/api/product'
import { getStatusText, getStatusType } from '@/constants/product'
import { useUserStore } from '@/store/user'

const userStore = useUserStore()

// ---- 个人信息 ----
const info = ref(null)
const infoLoading = ref(false)

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

async function fetchInfo() {
  infoLoading.value = true
  try {
    const res = await getUserInfo()
    info.value = res.data
    // 同步最新昵称/头像到 store，保持导航栏一致
    userStore.setUserInfo({
      nickname: res.data.nickname,
      avatar: res.data.avatar
    })
  } catch (e) {
    info.value = null
  } finally {
    infoLoading.value = false
  }
}

// ---- 编辑资料 ----
const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const editForm = reactive({
  nickname: '',
  phone: '',
  avatar: ''
})

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
  } catch (e) {
    return
  }
  saving.value = true
  try {
    await updateUserInfo({
      nickname: editForm.nickname,
      avatar: editForm.avatar,
      phone: editForm.phone
    })
    ElMessage.success('资料已更新')
    editVisible.value = false
    await fetchInfo()
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    saving.value = false
  }
}

// ---- 我发布的 ----
const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const listLoading = ref(false)

async function fetchMyProducts() {
  listLoading.value = true
  try {
    const res = await getMyProducts({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = res.data.list || []
    total.value = res.data.total || 0
  } catch (e) {
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
  ElMessageBox.confirm(`确定要下架「${row.title}」吗？`, '下架确认', {
    confirmButtonText: '下架',
    cancelButtonText: '取消',
    type: 'warning'
  })
    .then(async () => {
      try {
        await deleteProduct(row.productId)
        ElMessage.success('已下架')
        // 若当前页删空且非首页，回退一页
        if (list.value.length === 1 && pageNum.value > 1) {
          pageNum.value -= 1
        }
        fetchMyProducts()
      } catch (e) {
        // 错误提示已由拦截器处理
      }
    })
    .catch(() => {})
}

onMounted(() => {
  fetchInfo()
  fetchMyProducts()
})
</script>

<style scoped>
.card-title {
  font-size: 16px;
  font-weight: 600;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.info-card {
  margin-bottom: 20px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 24px;
}

.info-desc {
  flex: 1;
}

.price {
  color: #f56c6c;
  font-weight: 600;
}

.thumb-placeholder {
  width: 60px;
  height: 60px;
  display: flex;
  align-items: center;
  justify-content: center;
  background-color: #f5f7fa;
  color: #c0c4cc;
  border-radius: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
