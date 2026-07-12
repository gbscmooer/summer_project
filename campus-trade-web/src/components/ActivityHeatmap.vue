<template>
  <div class="activity-heatmap" v-loading="loading">
    <div class="heatmap-header">
      <div class="heatmap-title-block">
        <h2 class="heatmap-title">{{ t('activity.heatmapTitle') }}</h2>
        <p class="heatmap-total">{{ formatNumber(data?.totalCount ?? 0) }}</p>
      </div>
      <div class="heatmap-filters">
        <button
          v-for="tab in filterTabs"
          :key="tab.value"
          type="button"
          class="filter-tab"
          :class="{ active: filter === tab.value }"
          @click="$emit('filter-change', tab.value)"
        >
          {{ tab.label }}
        </button>
      </div>
    </div>

    <div v-if="data" class="heatmap-body">
      <div class="heatmap-grid-wrap">
        <div class="weekday-labels">
          <span v-for="(label, i) in weekdayLabels" :key="i" class="weekday-label">{{ label }}</span>
        </div>
        <div class="heatmap-scroll">
          <div class="month-labels" :style="{ width: gridWidth + 'px' }">
            <span
              v-for="(month, i) in monthLabels"
              :key="i"
              class="month-label"
              :style="{ left: month.left + 'px' }"
            >
              {{ month.text }}
            </span>
          </div>
          <div class="heatmap-grid" :style="{ width: gridWidth + 'px', height: gridHeight + 'px' }">
            <div
              v-for="(cell, idx) in cells"
              :key="idx"
              class="heatmap-cell"
              :class="levelClass(cell.count)"
              :style="cellStyle(cell)"
              :title="cell.date ? `${cell.date}: ${cell.count}` : ''"
            />
          </div>
        </div>
      </div>

      <div class="heatmap-legend">
        <span class="legend-label">{{ t('activity.fewer') }}</span>
        <span v-for="lvl in 4" :key="lvl" class="heatmap-cell legend-cell" :class="levelClass(lvl - 1)" />
        <span class="legend-label">{{ t('activity.more') }}</span>
      </div>

      <div class="heatmap-stats">
        <div class="stat-item">
          <span class="stat-label">{{ t('activity.mostActiveMonth') }}</span>
          <span class="stat-value">{{ data.mostActiveMonth || '—' }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ t('activity.mostActiveDay') }}</span>
          <span class="stat-value">{{ data.mostActiveDay || '—' }}</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ t('activity.longestStreak') }}</span>
          <span class="stat-value">{{ data.longestStreak }}d</span>
        </div>
        <div class="stat-item">
          <span class="stat-label">{{ t('activity.currentStreak') }}</span>
          <span class="stat-value">{{ data.currentStreak }}d</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from '@/i18n'

const props = defineProps({
  data: { type: Object, default: null },
  filter: { type: String, default: 'all' },
  loading: { type: Boolean, default: false }
})

defineEmits(['filter-change'])

const { t } = useI18n()

const CELL = 12
const GAP = 3

const filterTabs = computed(() => [
  { value: 'all', label: t('activity.filterAll') },
  { value: 'posts', label: t('activity.filterPosts') },
  { value: 'comments', label: t('activity.filterComments') }
])

const weekdayLabels = computed(() => ['', 'M', '', 'W', '', 'F', ''])

const cells = computed(() => {
  if (!props.data?.days?.length) return []
  const days = props.data.days
  const first = new Date(days[0].date + 'T00:00:00')
  const startDow = (first.getDay() + 6) % 7 // Mon=0
  const result = []
  for (let i = 0; i < startDow; i++) {
    result.push({ date: null, count: 0, week: 0, row: i })
  }
  days.forEach((day, index) => {
    const pos = startDow + index
    const week = Math.floor(pos / 7)
    const row = pos % 7
    result.push({ date: day.date, count: day.count, week, row })
  })
  return result
})

const weekCount = computed(() => {
  if (!cells.value.length) return 0
  return Math.max(...cells.value.map((c) => c.week)) + 1
})

const gridWidth = computed(() => weekCount.value * (CELL + GAP) - GAP)
const gridHeight = computed(() => 7 * (CELL + GAP) - GAP)

const monthLabels = computed(() => {
  if (!props.data?.days?.length) return []
  const labels = []
  const seen = new Set()
  const first = new Date(props.data.days[0].date + 'T00:00:00')
  const startDow = (first.getDay() + 6) % 7
  props.data.days.forEach((day, index) => {
    const d = new Date(day.date + 'T00:00:00')
    if (d.getDate() === 1 || (index === 0 && !seen.has(d.getMonth()))) {
      const pos = startDow + index
      const week = Math.floor(pos / 7)
      const key = `${d.getFullYear()}-${d.getMonth()}`
      if (!seen.has(key)) {
        seen.add(key)
        labels.push({
          text: d.toLocaleString(undefined, { month: 'short' }).charAt(0).toUpperCase(),
          left: week * (CELL + GAP)
        })
      }
    }
  })
  return labels
})

function cellStyle(cell) {
  return {
    width: CELL + 'px',
    height: CELL + 'px',
    left: cell.week * (CELL + GAP) + 'px',
    top: cell.row * (CELL + GAP) + 'px'
  }
}

function levelClass(count) {
  if (count <= 0) return 'level-0'
  if (count === 1) return 'level-1'
  if (count === 2) return 'level-2'
  if (count === 3) return 'level-3'
  return 'level-4'
}

function formatNumber(n) {
  return Number(n || 0).toLocaleString()
}
</script>

<style scoped>
.activity-heatmap {
  padding: 4px 0;
}

.heatmap-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}

.heatmap-title {
  margin: 0;
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text-secondary);
}

.heatmap-total {
  margin: 4px 0 0;
  font-size: 32px;
  font-weight: 600;
  color: var(--oa-text);
  line-height: 1.1;
}

.heatmap-filters {
  display: flex;
  gap: 4px;
  flex-shrink: 0;
}

.filter-tab {
  padding: 4px 10px;
  border: none;
  border-radius: 6px;
  background: transparent;
  color: var(--oa-text-muted);
  font-size: 12px;
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.filter-tab:hover {
  color: var(--oa-text-secondary);
}

.filter-tab.active {
  background: var(--oa-bg-elevated);
  color: var(--oa-text);
}

.heatmap-body {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.heatmap-grid-wrap {
  display: flex;
  gap: 8px;
}

.weekday-labels {
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  height: 105px;
  padding-top: 18px;
  flex-shrink: 0;
}

.weekday-label {
  font-size: 10px;
  color: var(--oa-text-muted);
  line-height: 12px;
  height: 12px;
}

.heatmap-scroll {
  overflow-x: auto;
  flex: 1;
  min-width: 0;
}

.month-labels {
  position: relative;
  height: 16px;
  margin-bottom: 4px;
}

.month-label {
  position: absolute;
  top: 0;
  font-size: 10px;
  color: var(--oa-text-muted);
}

.heatmap-grid {
  position: relative;
}

.heatmap-cell {
  position: absolute;
  border-radius: 2px;
  background: var(--oa-bg-elevated);
}

.heatmap-cell.level-0 { background: var(--oa-bg-elevated); }
.heatmap-cell.level-1 { background: #0e4429; }
.heatmap-cell.level-2 { background: #006d32; }
.heatmap-cell.level-3 { background: #26a641; }
.heatmap-cell.level-4 { background: #39d353; }

.heatmap-legend {
  display: flex;
  align-items: center;
  gap: 3px;
}

.legend-cell {
  position: static;
  display: inline-block;
  width: 12px;
  height: 12px;
}

.legend-label {
  font-size: 11px;
  color: var(--oa-text-muted);
  margin: 0 4px;
}

.heatmap-stats {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 16px;
  padding-top: 8px;
  border-top: 1px solid var(--oa-border-subtle);
}

.stat-label {
  display: block;
  font-size: 12px;
  color: var(--oa-text-muted);
  margin-bottom: 4px;
}

.stat-value {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
}

@media (max-width: 768px) {
  .heatmap-stats {
    grid-template-columns: repeat(2, 1fr);
  }

  .heatmap-header {
    flex-direction: column;
  }
}
</style>
