<template>
  <div class="page-container activity-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('activity.title') }}</h1>
      <p class="page-subtitle">{{ t('activity.subtitle') }}</p>
      <p v-if="userStore.isMerchant" class="merchant-hint">
        <router-link to="/merchant">{{ t('activity.goMerchant') }}</router-link>
      </p>
    </div>

    <!-- 活跃度热力图 -->
    <div class="oa-panel heatmap-panel">
      <ActivityHeatmap
        :data="heatmap"
        :filter="heatmapFilter"
        :loading="heatmapLoading"
        @filter-change="onHeatmapFilterChange"
      />
    </div>

    <!-- 积分记录 -->
    <div class="oa-panel points-panel" v-loading="pointsLoading">
      <div class="oa-panel-header points-head">
        <div>
          <span class="oa-panel-title">{{ t('activity.pointsRecord') }}</span>
          <p class="panel-desc">{{ t('activity.pointsRecordDesc') }}</p>
        </div>
        <div class="points-head-actions">
          <div class="mode-pills">
            <button
              type="button"
              class="range-btn"
              :class="{ active: pointsViewMode === 'spend' }"
              @click="setPointsViewMode('spend')"
            >
              {{ t('activity.viewSpend') }}
            </button>
            <button
              type="button"
              class="range-btn"
              :class="{ active: pointsViewMode === 'earn' }"
              @click="setPointsViewMode('earn')"
            >
              {{ t('activity.viewEarn') }}
            </button>
          </div>
          <div class="range-pills">
            <button
              v-for="d in pointRanges"
              :key="d"
              type="button"
              class="range-btn"
              :class="{ active: pointsDays === d }"
              @click="setPointsDays(d)"
            >
              {{ t(`activity.range${d}d`) }}
            </button>
          </div>
        </div>
      </div>

      <div class="points-summary" :class="{ 'earn-mode': pointsViewMode === 'earn' }">
        <template v-if="pointsViewMode === 'spend'">
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.totalSpent') }}</span>
            <span class="sum-value">{{ pointsStats.totalSpent || 0 }}</span>
          </div>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.spentProducts') }}</span>
            <span class="sum-value">{{ pointsStats.spentProducts || 0 }}</span>
          </div>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.spentTips') }}</span>
            <span class="sum-value">{{ pointsStats.spentTips || 0 }}</span>
          </div>
        </template>
        <template v-else>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.totalEarned') }}</span>
            <span class="sum-value earn">{{ pointsStats.totalEarned || 0 }}</span>
          </div>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.earnedCheckin') }}</span>
            <span class="sum-value earn">{{ pointsStats.earnedCheckin || 0 }}</span>
          </div>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.earnedLike') }}</span>
            <span class="sum-value earn">{{ pointsStats.earnedLike || 0 }}</span>
          </div>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.earnedSales') }}</span>
            <span class="sum-value earn">{{ pointsStats.earnedSales || 0 }}</span>
          </div>
          <div class="sum-card">
            <span class="sum-label">{{ t('activity.earnedTips') }}</span>
            <span class="sum-value earn">{{ pointsStats.earnedTips || 0 }}</span>
          </div>
        </template>
      </div>

      <div class="points-charts">
        <div class="chart-block">
          <div class="chart-title">{{ pointsViewMode === 'spend' ? t('activity.usageTitle') : t('activity.earnTitle') }}</div>
          <p class="chart-sub">{{ pointsViewMode === 'spend' ? t('activity.usageSubtitle') : t('activity.earnSubtitle') }}</p>
          <v-chart class="chart" :option="pointsLineOption" autoresize />
        </div>
        <div class="chart-block pie-block">
          <div class="chart-title">{{ pointsViewMode === 'spend' ? t('activity.pieTitle') : t('activity.earnPieTitle') }}</div>
          <v-chart class="chart" :option="pointsPieOption" autoresize />
        </div>
      </div>

      <div class="ledger-block">
        <div class="chart-title">{{ t('activity.ledgerTitle') }}</div>
        <p class="ledger-hint">{{ t('activity.ledgerFilterHint') }}</p>
        <el-table :data="ledgerList" size="small" empty-text="">
          <el-table-column :label="t('activity.colDate')" min-width="160">
            <template #default="{ row }">{{ row.createTime }}</template>
          </el-table-column>
          <el-table-column :label="t('activity.colReason')" min-width="140">
            <template #default="{ row }">{{ row.reasonLabel || row.reason }}</template>
          </el-table-column>
          <el-table-column :label="t('activity.colDelta')" width="110">
            <template #default="{ row }">
              <span :class="Number(row.delta) >= 0 ? 'delta-pos' : 'delta-neg'">
                {{ Number(row.delta) >= 0 ? '+' : '' }}{{ row.delta }}
              </span>
            </template>
          </el-table-column>
          <el-table-column :label="t('activity.colBalance')" width="100" prop="balanceAfter" />
        </el-table>
        <el-empty v-if="!pointsLoading && ledgerList.length === 0" :description="t('activity.ledgerEmpty')" />
        <div v-if="ledgerTotal > ledgerPageSize" class="pager">
          <el-pagination
            v-model:current-page="ledgerPage"
            layout="prev, pager, next"
            :page-size="ledgerPageSize"
            :total="ledgerTotal"
            @current-change="fetchLedger"
          />
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { use } from 'echarts/core'
import { CanvasRenderer } from 'echarts/renderers'
import { PieChart, LineChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import VChart from 'vue-echarts'
import ActivityHeatmap from '@/components/ActivityHeatmap.vue'
import { getUserInfo } from '@/api/user'
import { getPointsStats, getPointsLedger } from '@/api/points'
import { getActivityHeatmap } from '@/api/product'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'

use([CanvasRenderer, PieChart, LineChart, GridComponent, TooltipComponent, LegendComponent])

const { t } = useI18n()
const userStore = useUserStore()

const heatmap = ref(null)
const heatmapFilter = ref('all')
const heatmapLoading = ref(false)

const pointRanges = [1, 7, 30]
const pointsDays = ref(7)
const pointsViewMode = ref('spend')
const pointsStats = ref({})
const pointsLoading = ref(false)
const ledgerList = ref([])
const ledgerTotal = ref(0)
const ledgerPage = ref(1)
const ledgerPageSize = 50

const pointsLineOption = computed(() => {
  const daily = pointsStats.value?.daily || []
  const xData = daily.map((p) => String(p.date || '').slice(5))
  const axisCommon = {
    grid: { left: 48, right: 16, top: 28, bottom: 32 },
    tooltip: { trigger: 'axis' },
    xAxis: {
      type: 'category',
      data: xData,
      axisLabel: { color: '#9b9b9b', fontSize: 10 },
      axisLine: { lineStyle: { color: '#424242' } }
    },
    yAxis: {
      type: 'value',
      axisLabel: { color: '#9b9b9b', fontSize: 10 },
      splitLine: { lineStyle: { color: '#333' } }
    }
  }

  if (pointsViewMode.value === 'earn') {
    const hasBreakdown = daily.some((p) =>
      Number(p.earnedCheckin || 0) > 0
      || Number(p.earnedLike || 0) > 0
      || Number(p.earnedSales || 0) > 0
      || Number(p.earnedTips || 0) > 0
    )
    if (hasBreakdown) {
      const legends = [
        t('activity.pieCheckin'),
        t('activity.pieLike'),
        t('activity.pieSales'),
        t('activity.pieTipsReceived')
      ]
      return {
        ...axisCommon,
        color: ['#10a37f', '#5b8def', '#f59e0b', '#a78bfa'],
        legend: { data: legends, bottom: 0, textStyle: { color: '#9b9b9b', fontSize: 11 } },
        series: [
          {
            name: legends[0],
            type: 'line',
            smooth: true,
            stack: 'earn',
            areaStyle: { opacity: 0.2 },
            data: daily.map((p) => Number(p.earnedCheckin || 0))
          },
          {
            name: legends[1],
            type: 'line',
            smooth: true,
            stack: 'earn',
            areaStyle: { opacity: 0.2 },
            data: daily.map((p) => Number(p.earnedLike || 0))
          },
          {
            name: legends[2],
            type: 'line',
            smooth: true,
            stack: 'earn',
            areaStyle: { opacity: 0.2 },
            data: daily.map((p) => Number(p.earnedSales || 0))
          },
          {
            name: legends[3],
            type: 'line',
            smooth: true,
            stack: 'earn',
            areaStyle: { opacity: 0.2 },
            data: daily.map((p) => Number(p.earnedTips || 0))
          }
        ]
      }
    }
    return {
      ...axisCommon,
      color: ['#10a37f'],
      legend: { data: [t('activity.totalEarned')], bottom: 0, textStyle: { color: '#9b9b9b', fontSize: 11 } },
      series: [{
        name: t('activity.totalEarned'),
        type: 'line',
        smooth: true,
        areaStyle: { opacity: 0.2 },
        data: daily.map((p) => Number(p.earned || 0))
      }]
    }
  }

  return {
    ...axisCommon,
    color: ['#10a37f', '#5b8def'],
    legend: { data: [t('activity.pieProducts'), t('activity.pieTips')], bottom: 0, textStyle: { color: '#9b9b9b', fontSize: 11 } },
    series: [
      {
        name: t('activity.pieProducts'),
        type: 'line',
        smooth: true,
        stack: 'spend',
        areaStyle: { opacity: 0.25 },
        data: daily.map((p) => Number(p.spentProducts || 0))
      },
      {
        name: t('activity.pieTips'),
        type: 'line',
        smooth: true,
        stack: 'spend',
        areaStyle: { opacity: 0.25 },
        data: daily.map((p) => Number(p.spentTips || 0))
      }
    ]
  }
})

const pointsPieOption = computed(() => {
  const isEarn = pointsViewMode.value === 'earn'
  const pie = isEarn ? (pointsStats.value?.pieEarn || []) : (pointsStats.value?.pieSpend || [])
  const labelMap = isEarn
    ? {
        checkin: t('activity.pieCheckin'),
        like: t('activity.pieLike'),
        sales: t('activity.pieSales'),
        tips: t('activity.pieTipsReceived'),
        other: t('activity.pieOther')
      }
    : {
        products: t('activity.pieProducts'),
        tips: t('activity.pieTips'),
        other: t('activity.pieOther')
      }
  const data = pie
    .map((x) => ({ name: labelMap[x.category] || x.category, value: Number(x.amount || 0) }))
    .filter((x) => x.value > 0)
  return {
    color: isEarn
      ? ['#10a37f', '#5b8def', '#f59e0b', '#a78bfa', '#9b9b9b']
      : ['#10a37f', '#5b8def', '#9b9b9b'],
    tooltip: { trigger: 'item' },
    series: [{
      type: 'pie',
      radius: ['42%', '68%'],
      data: data.length ? data : [{ name: '-', value: 1, itemStyle: { color: '#333' }, label: { show: false } }],
      label: { color: '#cfcfcf', fontSize: 11 }
    }]
  }
})

async function fetchPointsStats() {
  pointsLoading.value = true
  try {
    const res = await getPointsStats({ days: pointsDays.value })
    pointsStats.value = res.data || {}
  } catch {
    pointsStats.value = {}
  } finally {
    pointsLoading.value = false
  }
}

async function fetchLedger() {
  try {
    const res = await getPointsLedger({
      pageNum: ledgerPage.value,
      pageSize: ledgerPageSize,
      direction: pointsViewMode.value
    })
    ledgerList.value = res.data?.list || []
    ledgerTotal.value = res.data?.total || 0
  } catch {
    ledgerList.value = []
    ledgerTotal.value = 0
  }
}

function setPointsDays(d) {
  pointsDays.value = d
  fetchPointsStats()
}

function setPointsViewMode(mode) {
  if (pointsViewMode.value === mode) return
  pointsViewMode.value = mode
  ledgerPage.value = 1
  fetchLedger()
}

async function fetchHeatmap() {
  heatmapLoading.value = true
  try {
    const res = await getActivityHeatmap({ filter: heatmapFilter.value })
    heatmap.value = res.data
  } catch {
    heatmap.value = null
  } finally {
    heatmapLoading.value = false
  }
}

function onHeatmapFilterChange(filter) {
  heatmapFilter.value = filter
  fetchHeatmap()
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
  fetchHeatmap()
  fetchPointsStats()
  fetchLedger()
})
</script>

<style scoped>
.page-subtitle {
  margin: 4px 0 0;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

.merchant-hint {
  margin: 8px 0 0;
  font-size: 13px;
}

.merchant-hint a {
  color: var(--oa-accent, #10a37f);
  text-decoration: none;
}

.merchant-hint a:hover {
  text-decoration: underline;
}

.heatmap-panel {
  margin-bottom: 16px;
}

.points-panel {
  margin-bottom: 16px;
}

.points-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
  flex-wrap: wrap;
}

.points-head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.mode-pills,
.range-pills {
  display: flex;
  gap: 6px;
}

.panel-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.range-btn {
  border: 1px solid var(--oa-border);
  background: transparent;
  color: var(--oa-text-secondary);
  border-radius: 999px;
  padding: 4px 10px;
  font-size: 12px;
  cursor: pointer;
}

.range-btn.active {
  border-color: var(--oa-accent, #10a37f);
  color: var(--oa-accent, #10a37f);
}

.points-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
  margin: 16px 0;
}

.points-summary.earn-mode {
  grid-template-columns: repeat(5, minmax(0, 1fr));
}

.sum-card {
  border: none;
  border-radius: 10px;
  padding: 12px 14px;
}

.sum-label {
  display: block;
  font-size: 12px;
  color: var(--oa-text-muted);
  margin-bottom: 6px;
}

.sum-value {
  font-size: 22px;
  font-weight: 650;
  color: var(--oa-text);
}

.sum-value.earn {
  color: var(--oa-accent, #10a37f);
}

.points-charts {
  display: grid;
  grid-template-columns: 1.6fr 1fr;
  gap: 16px;
  margin-bottom: 16px;
}

.chart-block {
  border: none;
  border-radius: 10px;
  padding: 12px;
}

.chart-title {
  font-size: 14px;
  font-weight: 600;
}

.chart-sub {
  margin: 4px 0 8px;
  font-size: 12px;
  color: var(--oa-text-muted);
}

.chart {
  height: 260px;
  width: 100%;
}

.ledger-block {
  margin-top: 8px;
}

.ledger-hint {
  margin: 4px 0 10px;
  font-size: 12px;
  color: var(--oa-text-muted);
}

.delta-pos { color: #10a37f; }
.delta-neg { color: #e45757; }

.pager {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .points-summary.earn-mode { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 900px) {
  .points-summary,
  .points-summary.earn-mode { grid-template-columns: 1fr 1fr; }
  .points-charts { grid-template-columns: 1fr; }
}
</style>
