<template>
  <div class="page-container">
    <el-card class="notify-card">
      <template #header>
        <div class="card-header">
          <span class="card-title">消息通知</span>
          <el-button
            v-if="list.some((n) => n.isRead === 0)"
            text
            type="primary"
            :loading="markingAll"
            @click="onMarkAllRead"
          >
            全部已读
          </el-button>
        </div>
      </template>

      <div v-loading="loading">
        <el-empty v-if="!loading && list.length === 0" description="暂无通知" />

        <div v-else class="notify-list">
          <div
            v-for="item in list"
            :key="item.id"
            class="notify-item"
            :class="{ unread: item.isRead === 0 }"
            @click="onItemClick(item)"
          >
            <div class="notify-head">
              <span class="notify-title">{{ item.title }}</span>
              <el-tag v-if="item.isRead === 0" size="small" type="danger" effect="plain">未读</el-tag>
            </div>
            <p class="notify-content">{{ item.content }}</p>
            <div class="notify-meta">
              <span v-if="item.orderNo">订单号：{{ item.orderNo }}</span>
              <span>{{ formatTime(item.createTime) }}</span>
            </div>
          </div>
        </div>

        <div v-if="total > 0" class="pagination-wrapper">
          <el-pagination
            v-model:current-page="pageNum"
            v-model:page-size="pageSize"
            :total="total"
            :page-sizes="[10, 20, 30]"
            layout="total, sizes, prev, pager, next"
            background
            @current-change="fetchList"
            @size-change="onSizeChange"
          />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import {
  getNotificationList,
  markNotificationRead,
  markAllNotificationsRead
} from '@/api/notification'

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const markingAll = ref(false)

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return t
  const pad = (n) => String(n).padStart(2, '0')
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function fetchList() {
  loading.value = true
  try {
    const res = await getNotificationList({
      pageNum: pageNum.value,
      pageSize: pageSize.value
    })
    list.value = (res.data && res.data.list) || []
    total.value = (res.data && res.data.total) || 0
  } catch (e) {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

function onSizeChange() {
  pageNum.value = 1
  fetchList()
}

async function onItemClick(item) {
  if (item.isRead !== 0) return
  try {
    await markNotificationRead(item.id)
    item.isRead = 1
    ElMessage.success('已标记已读')
  } catch (e) {
    // 错误提示已由拦截器处理
  }
}

async function onMarkAllRead() {
  markingAll.value = true
  try {
    await markAllNotificationsRead()
    ElMessage.success('已全部标记已读')
    await fetchList()
  } catch (e) {
    // 错误提示已由拦截器处理
  } finally {
    markingAll.value = false
  }
}

onMounted(fetchList)
</script>

<style scoped>
.notify-card {
  min-height: 400px;
}

.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.card-title {
  font-size: 16px;
  font-weight: 600;
}

.notify-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.notify-item {
  padding: 14px 16px;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.2s;
}

.notify-item:hover {
  background-color: #f5f7fa;
}

.notify-item.unread {
  background-color: #ecf5ff;
  border-color: #d9ecff;
}

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.notify-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.notify-content {
  margin: 0 0 8px;
  color: #606266;
  font-size: 14px;
  line-height: 1.6;
}

.notify-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  color: #909399;
  font-size: 12px;
}

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}
</style>
