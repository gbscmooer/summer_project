<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ t('notifications.title') }}</h1>
      <el-button
        v-if="list.some((n) => n.isRead === 0)"
        type="primary"
        size="small"
        :loading="markingAll"
        @click="onMarkAllRead"
      >
        {{ t('notifications.markAllRead') }}
      </el-button>
    </div>

    <div class="oa-panel">
      <div v-loading="loading">
        <div v-if="!loading && list.length === 0" class="oa-empty-state">
          <p>{{ t('notifications.empty') }}</p>
        </div>

        <div v-else class="notify-list">
          <div
            v-for="item in list"
            :key="item.id"
            class="oa-list-item notify-item"
            :class="{ unread: item.isRead === 0 }"
            @click="onItemClick(item)"
          >
            <div class="notify-head">
              <span class="notify-title">{{ item.title }}</span>
              <span v-if="item.isRead === 0" class="oa-status oa-status-danger">{{ t('notifications.unread') }}</span>
            </div>
            <p class="notify-content">{{ item.content }}</p>
            <div class="notify-meta">
              <span v-if="item.orderNo" class="oa-meta">{{ t('notifications.orderNo').replace('{no}', item.orderNo) }}</span>
              <span class="oa-meta">{{ formatTime(item.createTime) }}</span>
            </div>
          </div>
        </div>

        <div v-if="total > 0" class="oa-pagination">
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
    </div>
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
import { useOnboarding } from '@/composables/useOnboarding'
import { useI18n } from '@/i18n'

const onboarding = useOnboarding()
const { t } = useI18n()

const list = ref([])
const total = ref(0)
const pageNum = ref(1)
const pageSize = ref(10)
const loading = ref(false)
const markingAll = ref(false)
const unreadRefreshEvent = 'campus:unread-count-refresh'

function refreshUnreadBadge() {
  window.dispatchEvent(new Event(unreadRefreshEvent))
}

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
  } catch {
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
    refreshUnreadBadge()
    ElMessage.success(t('notifications.markedRead'))
  } catch {
    // handled by interceptor
  }
}

async function onMarkAllRead() {
  markingAll.value = true
  try {
    await markAllNotificationsRead()
    ElMessage.success(t('notifications.allMarkedRead'))
    await fetchList()
    refreshUnreadBadge()
  } finally {
    markingAll.value = false
  }
}

onMounted(async () => {
  await fetchList()
  onboarding.trackStep('notify')
})
</script>

<style scoped>
.notify-list {
  display: flex;
  flex-direction: column;
}

.notify-item {
  cursor: pointer;
}

.notify-item.unread {
  border-color: rgba(239, 68, 68, 0.3);
  background: rgba(239, 68, 68, 0.05);
}

.notify-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}

.notify-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
}

.notify-content {
  margin: 0 0 8px;
  color: var(--oa-text-secondary);
  font-size: 13px;
  line-height: 1.6;
}

.notify-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
}
</style>
