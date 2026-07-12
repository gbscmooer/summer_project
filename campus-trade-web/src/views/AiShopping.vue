<template>
  <div class="ai-shopping-page">
    <div class="page-header">
      <div>
        <h1 class="page-title">AI 帮我找</h1>
        <p class="page-subtitle">像和同学聊天一样描述需求，AI 会理解预算、品类和排序并查找真实在售商品。</p>
      </div>
    </div>

    <section class="assistant-panel">
      <div class="assistant-mark"><el-icon><MagicStick /></el-icon></div>
      <div class="assistant-body">
        <el-input
          v-model="query"
          type="textarea"
          :rows="3"
          maxlength="500"
          resize="none"
          placeholder="例如：我需要一本高数课程教科书，预算不超过40元，便宜的优先"
          @keydown.meta.enter="runSearch"
          @keydown.ctrl.enter="runSearch"
        />
        <div class="assistant-actions">
          <div class="examples">
            <button v-for="example in examples" :key="example" @click="useExample(example)">
              {{ example }}
            </button>
          </div>
          <el-button type="primary" :loading="loading" :disabled="!query.trim()" @click="runSearch">
            <el-icon><Search /></el-icon>
            开始查找
          </el-button>
        </div>
      </div>
    </section>

    <template v-if="result">
      <section class="result-summary">
        <div>
          <span class="eyebrow">AI 理解</span>
          <p>{{ result.intent?.explanation || result.summary }}</p>
        </div>
        <div class="intent-tags">
          <el-tag effect="plain">关键词：{{ result.intent?.keyword }}</el-tag>
          <el-tag v-if="result.intent?.category" effect="plain">{{ result.intent.category }}</el-tag>
          <el-tag v-if="result.intent?.maxPrice" effect="plain">最高 {{ formatPoints(result.intent.maxPrice) }}</el-tag>
          <el-tag v-if="result.priceLow != null" effect="plain">
            结果价格 {{ formatPoints(result.priceLow) }}–{{ formatPoints(result.priceHigh) }}
          </el-tag>
        </div>
      </section>

      <div class="result-heading">
        <div>
          <h2>交付结果</h2>
          <p>{{ result.summary }}</p>
        </div>
        <span>{{ result.total || 0 }} 件</span>
      </div>

      <div v-if="result.products?.length" class="product-grid">
        <article
          v-for="item in result.products"
          :key="item.productId"
          class="product-card"
          @click="router.push(`/product/${item.productId}`)"
        >
          <div class="product-cover">
            <el-image :src="item.cover" fit="cover" class="product-image">
              <template #error>
                <div class="image-placeholder"><el-icon><Picture /></el-icon></div>
              </template>
            </el-image>
          </div>
          <div class="product-content">
            <h3>{{ item.title }}</h3>
            <p v-if="item.description">{{ item.description }}</p>
            <div class="product-footer">
              <strong>{{ formatPoints(item.price) }}</strong>
              <span>{{ item.category }}</span>
            </div>
          </div>
        </article>
      </div>
      <el-empty v-else description="没有符合当前条件的在售商品" />
    </template>

    <section v-else class="how-it-works">
      <div v-for="(step, index) in steps" :key="step.title" class="step-card">
        <span>{{ index + 1 }}</span>
        <h3>{{ step.title }}</h3>
        <p>{{ step.description }}</p>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { MagicStick, Picture, Search } from '@element-plus/icons-vue'
import { searchProductsByAi } from '@/api/ai'
import { useOnboarding } from '@/composables/useOnboarding'
import { useI18n } from '@/i18n'

const router = useRouter()
const onboarding = useOnboarding()
const { t } = useI18n()
const query = ref('')
const loading = ref(false)
const result = ref(null)

const examples = [
  '高数教材，40元以内',
  '想买一台便宜的二手显示器',
  '宿舍用台灯，成色好一点'
]

const steps = [
  { title: '描述需求', description: '可以写商品、用途、预算、成色和价格偏好。' },
  { title: '理解意图', description: 'AI 会提取检索词、分类、预算与排序方式。' },
  { title: '交付候选', description: '系统只展示商品库中真实存在且仍在售的结果。' }
]

function useExample(example) {
  query.value = example
  runSearch()
}

async function runSearch() {
  const value = query.value.trim()
  if (!value || loading.value) return
  loading.value = true
  try {
    const res = await searchProductsByAi({ query: value, pageSize: 12 })
    result.value = res.data
    onboarding.trackStep('ai')
  } finally {
    loading.value = false
  }
}

function formatPoints(price) {
  const value = Number(price)
  const n = Number.isFinite(value) ? (Number.isInteger(value) ? String(value) : value.toFixed(0)) : '--'
  return `${n} ${t('common.pointsUnit')}`
}
</script>

<style scoped>
.ai-shopping-page {
  max-width: 1100px;
}

.page-subtitle {
  margin-top: 8px;
  color: var(--oa-text-secondary);
  font-size: 14px;
}

.assistant-panel {
  display: flex;
  gap: 16px;
  padding: 22px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: 16px;
  background: linear-gradient(135deg, color-mix(in srgb, var(--oa-accent) 8%, var(--oa-bg-sidebar)), var(--oa-bg-sidebar) 55%);
}

.assistant-mark {
  width: 38px;
  height: 38px;
  border-radius: 10px;
  display: grid;
  place-items: center;
  background: var(--oa-text);
  color: var(--oa-on-primary);
  flex: 0 0 auto;
}

.assistant-body {
  flex: 1;
  min-width: 0;
}

.assistant-actions {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-top: 12px;
}

.examples {
  display: flex;
  flex-wrap: wrap;
  gap: 7px;
}

.examples button {
  border: 1px solid var(--oa-border-subtle);
  background: var(--oa-bg-input);
  color: var(--oa-text-secondary);
  border-radius: 20px;
  padding: 6px 11px;
  cursor: pointer;
}

.result-summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  margin: 22px 0;
  padding: 16px 18px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: 12px;
  background: var(--oa-bg-sidebar);
}

.eyebrow {
  display: block;
  color: var(--oa-text-muted);
  font-size: 11px;
  text-transform: uppercase;
  margin-bottom: 5px;
}

.result-summary p {
  font-size: 14px;
  line-height: 1.5;
}

.intent-tags {
  display: flex;
  justify-content: flex-end;
  flex-wrap: wrap;
  gap: 6px;
}

.result-heading {
  display: flex;
  justify-content: space-between;
  align-items: end;
  margin-bottom: 14px;
}

.result-heading h2 {
  font-size: 18px;
  margin-bottom: 5px;
}

.result-heading p,
.result-heading > span {
  color: var(--oa-text-secondary);
  font-size: 13px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.product-card {
  overflow: hidden;
  border: 1px solid var(--oa-border-subtle);
  border-radius: 12px;
  background: var(--oa-bg-sidebar);
  cursor: pointer;
  transition: transform .15s, border-color .15s;
}

.product-card:hover {
  transform: translateY(-2px);
  border-color: var(--oa-border);
}

.product-cover {
  aspect-ratio: 4 / 3;
  background: var(--oa-bg-elevated);
}

.product-image,
.image-placeholder {
  width: 100%;
  height: 100%;
}

.image-placeholder {
  display: grid;
  place-items: center;
  color: var(--oa-text-muted);
}

.product-content {
  padding: 12px;
}

.product-content h3 {
  min-height: 40px;
  font-size: 14px;
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.product-content > p {
  margin-top: 6px;
  color: var(--oa-text-secondary);
  font-size: 12px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.product-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 10px;
}

.product-footer strong {
  font-size: 16px;
}

.product-footer span {
  color: var(--oa-text-muted);
  font-size: 12px;
}

.how-it-works {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 14px;
  margin-top: 28px;
}

.step-card {
  padding: 18px;
  border: 1px solid var(--oa-border-subtle);
  border-radius: 12px;
}

.step-card > span {
  display: grid;
  place-items: center;
  width: 26px;
  height: 26px;
  border-radius: 50%;
  background: var(--oa-bg-elevated);
  font-size: 12px;
}

.step-card h3 {
  margin: 14px 0 6px;
  font-size: 14px;
}

.step-card p {
  color: var(--oa-text-secondary);
  font-size: 13px;
  line-height: 1.5;
}

@media (max-width: 900px) {
  .product-grid { grid-template-columns: repeat(2, 1fr); }
  .assistant-actions, .result-summary { align-items: stretch; flex-direction: column; }
  .intent-tags { justify-content: flex-start; }
}
</style>
