<template>
  <div class="page-container events-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('events.title') }}</h1>
      <p class="page-subtitle">{{ t('events.subtitle') }}</p>
    </div>

    <div class="points-hero oa-panel" v-loading="loading">
      <span class="points-label">{{ t('events.currentPoints') }}</span>
      <span class="points-big">{{ status.points ?? userStore.points }}</span>
      <span class="points-unit">{{ t('common.pointsUnit') }}</span>
    </div>

    <div class="tasks-grid">
      <!-- 每日签到 -->
      <div class="oa-panel task-card">
        <h2 class="task-title">{{ t('events.checkinTitle') }}</h2>
        <p class="task-desc">
          {{ t('events.checkinDesc').replace('{n}', String(status.checkinPoints ?? 10)) }}
        </p>
        <el-button
          type="primary"
          :disabled="status.checkedInToday"
          :loading="checkinLoading"
          @click="onCheckin"
        >
          {{ status.checkedInToday ? t('events.checkedIn') : t('events.checkin') }}
        </el-button>
      </div>

      <!-- 每日点赞 -->
      <div class="oa-panel task-card">
        <h2 class="task-title">{{ t('events.likeTitle') }}</h2>
        <p class="task-desc">
          {{ t('events.likeDesc').replace('{n}', String(status.likeRewardPoints ?? 20)) }}
        </p>
        <div class="progress-row">
          <el-progress
            :percentage="likePercent"
            :stroke-width="10"
            :format="() => likeProgressText"
          />
        </div>
        <el-button
          v-if="canClaimLike"
          type="primary"
          :loading="claimLoading"
          @click="onClaimLike"
        >
          {{ t('events.claimReward') }}
        </el-button>
        <el-button v-else-if="status.likeRewarded" disabled>
          {{ t('events.rewardClaimed') }}
        </el-button>
        <el-button v-else disabled>
          {{ t('events.likeIncomplete') }}
        </el-button>
      </div>
    </div>

    <div class="oa-panel tip-panel">
      <h2 class="task-title">{{ t('events.howtoTitle') }}</h2>
      <p class="task-desc">{{ t('events.howtoDesc') }}</p>
      <p class="task-desc">{{ t('events.merchantHint') }}</p>
      <div v-if="userStore.isPersonal" class="tip-actions">
        <el-button @click="$router.push('/my?panel=merchant')">{{ t('events.applyMerchant') }}</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'
import { getEventsStatus, checkin, claimLikeReward } from '@/api/points'

const userStore = useUserStore()
const { t } = useI18n()

const loading = ref(false)
const checkinLoading = ref(false)
const claimLoading = ref(false)

const status = reactive({
  checkedInToday: false,
  likeCount: 0,
  likeTarget: 5,
  likeRewarded: false,
  checkinPoints: 10,
  likeRewardPoints: 20,
  points: 0
})

const likePercent = computed(() => {
  const target = Number(status.likeTarget) || 5
  const count = Math.min(Number(status.likeCount) || 0, target)
  return Math.round((count / target) * 100)
})

const likeProgressText = computed(() => {
  const target = Number(status.likeTarget) || 5
  const count = Math.min(Number(status.likeCount) || 0, target)
  return `${count}/${target}`
})

const canClaimLike = computed(() => {
  const target = Number(status.likeTarget) || 5
  return !status.likeRewarded && (Number(status.likeCount) || 0) >= target
})

function applyStatus(data) {
  if (!data) return
  status.checkedInToday = !!data.checkedInToday
  // 兼容后端 likeCountToday / likeRewardClaimed
  status.likeCount = Number(data.likeCountToday ?? data.likeCount) || 0
  status.likeTarget = Number(data.likeTarget) || 5
  status.likeRewarded = !!(data.likeRewardClaimed ?? data.likeRewarded)
  status.checkinPoints = Number(data.checkinPoints) || 10
  status.likeRewardPoints = Number(data.likeRewardPoints) || 20
  status.points = data.points != null ? Number(data.points) : userStore.points
  if (data.points != null) {
    userStore.setUserInfo({ points: Number(data.points) })
  }
}

async function fetchStatus() {
  loading.value = true
  try {
    const res = await getEventsStatus()
    applyStatus(res.data)
  } catch {
    status.points = userStore.points
  } finally {
    loading.value = false
  }
}

async function onCheckin() {
  checkinLoading.value = true
  try {
    const res = await checkin()
    if (res.data?.points != null) {
      userStore.setUserInfo({ points: Number(res.data.points) })
    }
    status.checkedInToday = true
    await fetchStatus()
    ElMessage.success(t('events.checkinSuccess'))
  } catch {
    // request 拦截器已提示
  } finally {
    checkinLoading.value = false
  }
}

async function onClaimLike() {
  claimLoading.value = true
  try {
    const res = await claimLikeReward()
    if (res.data?.points != null) {
      userStore.setUserInfo({ points: Number(res.data.points) })
    }
    await fetchStatus()
    ElMessage.success(t('events.claimSuccess'))
  } catch {
    // request 拦截器已提示
  } finally {
    claimLoading.value = false
  }
}

onMounted(() => {
  fetchStatus()
  userStore.refreshPoints()
})
</script>

<style scoped>
.events-page {
  max-width: 720px;
}

.points-hero {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  padding: 28px 24px;
  margin-bottom: 16px;
}

.points-label {
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.points-big {
  font-size: 48px;
  font-weight: 600;
  letter-spacing: -0.03em;
  font-variant-numeric: tabular-nums;
  line-height: 1.1;
}

.points-unit {
  font-size: 14px;
  color: var(--oa-text-muted);
}

.tasks-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.task-card {
  padding: 20px;
}

.task-title {
  font-size: 15px;
  font-weight: 550;
  margin: 0 0 8px;
}

.task-desc {
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
  margin: 0 0 16px;
}

.progress-row {
  margin-bottom: 16px;
}

.tip-panel {
  padding: 20px;
}

.tip-actions {
  margin-top: 8px;
}

@media (max-width: 640px) {
  .tasks-grid {
    grid-template-columns: 1fr;
  }

  .points-big {
    font-size: 40px;
  }
}
</style>
