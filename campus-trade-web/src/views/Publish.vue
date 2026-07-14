<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">发布商品</h1>
      <p class="page-subtitle">填写商品信息并上传实拍图。AI 助手仅生成可编辑草稿，不会自动发布。</p>
    </div>

    <el-alert
      type="info"
      :closable="false"
      show-icon
      class="quota-alert"
      title="仅商家与管理员可发布商品。普通用户请先完成商家认证。"
    />

    <el-alert
      v-if="quota && !quota.unlimited && quota.remaining <= 0"
      title="个人账户发布数量已达上限，请前往个人中心申请成为商家"
      type="warning"
      :closable="false"
      show-icon
      class="quota-alert"
    />
    <el-alert
      v-else-if="quota && !quota.unlimited"
      :title="quotaHint"
      type="info"
      :closable="false"
      show-icon
      class="quota-alert"
    />

    <div class="oa-panel publish-panel">
      <div class="panel-toolbar">
        <el-button @click="aiDialogVisible = true">
          <el-icon><MagicStick /></el-icon>
          AI 图片助手
        </el-button>
        <span class="toolbar-hint">可根据实拍图生成标题与描述草稿</span>
      </div>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <div class="form-section">
          <label class="oa-form-label">标题</label>
          <el-form-item prop="title">
            <el-input
              v-model="form.title"
              placeholder="请输入商品标题"
              maxlength="50"
              show-word-limit
              clearable
            />
          </el-form-item>
        </div>

        <div class="form-row">
          <div class="form-section half">
            <label class="oa-form-label">分类</label>
            <el-form-item prop="category">
              <el-select v-model="form.category" placeholder="选择分类" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
              </el-select>
            </el-form-item>
          </div>
          <div class="form-section half">
            <label class="oa-form-label">价格（积分）</label>
            <el-form-item prop="price">
              <el-input-number
                v-model="form.price"
                :min="0"
                :precision="2"
                :step="1"
                controls-position="right"
                style="width: 100%"
              />
            </el-form-item>
          </div>
        </div>

        <div class="form-section">
          <label class="oa-form-label">库存</label>
          <el-form-item prop="stock">
            <el-input-number
              v-model="form.stock"
              :min="1"
              :precision="0"
              :step="1"
              controls-position="right"
              style="width: 200px"
            />
          </el-form-item>
        </div>

        <div class="form-section">
          <label class="oa-form-label">商品图片</label>
          <el-form-item prop="imageList">
            <ImageUploadGallery v-model="form.imageList" :max-count="5" />
          </el-form-item>
          <p class="oa-form-hint">仅支持本地上传，不可填写外链；可多选，单张超过 1MB 会自动压缩分辨率。</p>
        </div>

        <div class="form-section">
          <label class="oa-form-label">描述</label>
          <el-form-item prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="5"
              placeholder="成色、规格、交易方式等"
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </div>

        <hr class="oa-divider" />

        <div class="form-actions">
          <el-button type="primary" :loading="loading" @click="onSubmit">发布商品</el-button>
          <el-button @click="onReset">重置</el-button>
        </div>
      </el-form>
    </div>

    <el-dialog
      v-model="aiDialogVisible"
      title="AI 图片发布助手"
      width="560px"
      destroy-on-close
      class="ai-dialog"
    >
      <el-alert
        type="info"
        :closable="false"
        show-icon
        title="仅生成可编辑模版并回填到表单，不会自动发布商品。"
        class="ai-dialog-alert"
      />
      <p class="ai-dialog-desc">
        将基于你已上传的商品实拍图生成标题、描述、分类与参考价，生成后请自行检查再点击「发布商品」。
      </p>
      <el-input
        v-model="aiNotes"
        type="textarea"
        :rows="3"
        maxlength="500"
        show-word-limit
        placeholder="可补充型号、购买年份、配件、功能情况或已知瑕疵"
      />
      <p v-if="form.imageList.length === 0" class="ai-dialog-warn">请先在表单中上传至少一张实拍图。</p>
      <div v-if="draftInfo" class="draft-result">
        <div>
          <strong>建议售价 {{ formatPoints(draftInfo.suggestedPrice) }}</strong>
          <span v-if="draftInfo.marketPriceLow != null">
            站内参考 {{ formatPoints(draftInfo.marketPriceLow) }}–{{ formatPoints(draftInfo.marketPriceHigh) }}
          </span>
          <span>{{ draftInfo.pricingBasis }}</span>
        </div>
        <el-tag effect="plain">识别成色：{{ draftInfo.condition }}</el-tag>
        <ul v-if="draftInfo.warnings?.length">
          <li v-for="warning in draftInfo.warnings" :key="warning">请确认：{{ warning }}</li>
        </ul>
      </div>
      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
        <el-button
          type="primary"
          :loading="aiLoading"
          :disabled="form.imageList.length === 0"
          @click="generateDraft"
        >
          <el-icon><MagicStick /></el-icon>
          生成模版
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { MagicStick } from '@element-plus/icons-vue'
import { createProduct, getPublishQuota } from '@/api/product'
import { createAiListingDraft } from '@/api/ai'
import { CATEGORIES } from '@/constants/product'
import { useOnboarding } from '@/composables/useOnboarding'
import { useI18n } from '@/i18n'
import ImageUploadGallery from '@/components/ImageUploadGallery.vue'

const { t } = useI18n()
const router = useRouter()
const onboarding = useOnboarding()
const categories = CATEGORIES

const formRef = ref(null)
const loading = ref(false)
const aiLoading = ref(false)
const aiDialogVisible = ref(false)
const aiNotes = ref('')
const draftInfo = ref(null)
const quota = ref(null)

const quotaHint = computed(() => {
  if (!quota.value || quota.value.unlimited) return ''
  return t('merchant.quotaHint')
    .replace('{limit}', String(quota.value.limit))
    .replace('{used}', String(quota.value.used))
    .replace('{remaining}', String(quota.value.remaining))
})

onMounted(() => {
  getPublishQuota()
    .then((res) => { quota.value = res.data })
    .catch(() => { quota.value = null })
})

const form = reactive({
  title: '',
  category: '',
  price: 0,
  stock: 1,
  imageList: [],
  description: ''
})

const rules = {
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

async function onSubmit() {
  if (!formRef.value) return
  try {
    await formRef.value.validate()
  } catch {
    return
  }
  if (quota.value && !quota.value.unlimited && quota.value.remaining <= 0) {
    ElMessage.warning('个人账户发布数量已达上限，请前往个人中心申请成为商家')
    return
  }
  loading.value = true
  try {
    await createProduct({
      title: form.title,
      description: form.description,
      price: form.price,
      images: form.imageList.join(','),
      category: form.category,
      stock: form.stock
    })
    ElMessage.success('发布成功')
    await onboarding.refresh()
    router.push('/merchant')
  } finally {
    loading.value = false
  }
}

async function generateDraft() {
  if (!form.imageList.length || aiLoading.value) return
  aiLoading.value = true
  try {
    const res = await createAiListingDraft({
      images: form.imageList,
      notes: aiNotes.value.trim()
    })
    const draft = res.data
    form.title = draft.title || form.title
    form.description = draft.description || form.description
    form.category = categories.includes(draft.category) ? draft.category : (form.category || '其他')
    if (draft.suggestedPrice != null) form.price = Number(draft.suggestedPrice)
    draftInfo.value = draft
    ElMessage.success('草稿已填入表单，请核对后手动发布')
    aiDialogVisible.value = false
  } finally {
    aiLoading.value = false
  }
}

function formatPoints(price) {
  const value = Number(price)
  const n = Number.isFinite(value) ? (Number.isInteger(value) ? String(value) : value.toFixed(0)) : '--'
  return `${n} ${t('common.pointsUnit')}`
}

function onReset() {
  formRef.value?.resetFields()
  form.price = 0
  form.stock = 1
  form.imageList = []
  aiNotes.value = ''
  draftInfo.value = null
}
</script>

<style scoped>
.publish-panel {
  max-width: 720px;
}

.page-subtitle {
  margin: 6px 0 0;
  font-size: 13px;
  color: var(--oa-text-secondary);
}

.quota-alert {
  max-width: 920px;
  margin-bottom: 16px;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.toolbar-hint {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.form-section {
  margin-bottom: 4px;
}

.form-section :deep(.el-form-item) {
  margin-bottom: 16px;
}

.form-row {
  display: flex;
  gap: 16px;
}

.form-row .half {
  flex: 1;
}

.form-actions {
  display: flex;
  gap: 10px;
}

.oa-form-hint {
  margin: -8px 0 12px;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.45;
}

.ai-dialog-alert {
  margin-bottom: 12px;
}

.ai-dialog-desc {
  margin: 0 0 12px;
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
}

.ai-dialog-warn {
  margin: 10px 0 0;
  font-size: 13px;
  color: #b45309;
}

.draft-result {
  display: flex;
  flex-wrap: wrap;
  align-items: flex-start;
  gap: 12px;
  margin-top: 14px;
  padding: 13px 14px;
  border-radius: 9px;
  background: var(--oa-bg-elevated);
}

.draft-result > div {
  flex: 1;
  min-width: 240px;
  display: flex;
  flex-direction: column;
  gap: 3px;
  font-size: 12px;
  color: var(--oa-text-secondary);
}

.draft-result strong {
  color: var(--oa-text);
  font-size: 14px;
}

.draft-result ul {
  flex-basis: 100%;
  padding-left: 18px;
  color: #b45309;
  font-size: 12px;
}

@media (max-width: 760px) {
  .form-row {
    flex-direction: column;
  }
}
</style>
