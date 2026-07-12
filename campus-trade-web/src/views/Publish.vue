<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">Publish</h1>
    </div>

    <p class="oa-section-desc">上传几张商品实拍图，让 AI 先生成标题、描述、成色和站内参考价；所有内容都可以修改后再发布。</p>

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

    <div class="oa-panel ai-draft-panel">
      <div class="ai-panel-heading">
        <div class="ai-icon"><el-icon><MagicStick /></el-icon></div>
        <div>
          <h2>AI 图片发布助手</h2>
          <p>建议上传正面、背面和瑕疵细节，识别与估价会更准确。</p>
        </div>
        <el-tag effect="plain">草稿不会自动发布</el-tag>
      </div>

      <div class="ai-upload-row">
        <label class="upload-card" :class="{ disabled: uploading || uploadedUrls.length >= 5 }">
          <input
            type="file"
            accept="image/jpeg,image/png,image/webp"
            multiple
            :disabled="uploading || uploadedUrls.length >= 5"
            @change="onFilesSelected"
          />
          <el-icon :size="22"><UploadFilled /></el-icon>
          <span>{{ uploading ? '上传中…' : '选择实拍图' }}</span>
          <small>JPG / PNG / WEBP，最多5张</small>
        </label>

        <div v-for="(url, index) in uploadedUrls" :key="url" class="preview-card">
          <el-image :src="url" fit="cover" />
          <button type="button" aria-label="移除图片" @click="removeUploadedImage(index)">
            <el-icon><Close /></el-icon>
          </button>
          <span>{{ index + 1 }}</span>
        </div>
      </div>

      <div class="ai-notes-row">
        <el-input
          v-model="aiNotes"
          maxlength="500"
          placeholder="可选：补充型号、购买年份、配件、功能情况或你知道的瑕疵"
        />
        <el-button
          type="primary"
          :loading="aiLoading"
          :disabled="uploadedUrls.length === 0"
          @click="generateDraft"
        >
          <el-icon><MagicStick /></el-icon>
          生成发布草稿
        </el-button>
      </div>

      <div v-if="draftInfo" class="draft-result">
        <div>
          <strong>建议售价 ¥{{ formatPrice(draftInfo.suggestedPrice) }}</strong>
          <span v-if="draftInfo.marketPriceLow != null">
            站内参考 ¥{{ formatPrice(draftInfo.marketPriceLow) }}–{{ formatPrice(draftInfo.marketPriceHigh) }}
          </span>
          <span>{{ draftInfo.pricingBasis }}</span>
        </div>
        <el-tag effect="plain">识别成色：{{ draftInfo.condition }}</el-tag>
        <ul v-if="draftInfo.warnings?.length">
          <li v-for="warning in draftInfo.warnings" :key="warning">请确认：{{ warning }}</li>
        </ul>
      </div>
    </div>

    <div class="oa-panel publish-panel">
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-position="top"
        @submit.prevent="onSubmit"
      >
        <div class="form-section">
          <label class="oa-form-label">Title</label>
          <el-form-item prop="title">
            <el-input
              v-model="form.title"
              placeholder="Describe your item in one line"
              maxlength="50"
              show-word-limit
              clearable
            />
          </el-form-item>
        </div>

        <div class="form-row">
          <div class="form-section half">
            <label class="oa-form-label">Category</label>
            <el-form-item prop="category">
              <el-select v-model="form.category" placeholder="Select category" style="width: 100%">
                <el-option v-for="cat in categories" :key="cat" :label="cat" :value="cat" />
              </el-select>
            </el-form-item>
          </div>
          <div class="form-section half">
            <label class="oa-form-label">Price (CNY)</label>
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
          <label class="oa-form-label">Stock</label>
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
          <el-form-item prop="images">
            <el-input
              v-model="form.images"
              type="textarea"
              :rows="3"
              placeholder="上传实拍图后会自动填写；也可补充逗号分隔的 https 图片地址"
            />
          </el-form-item>
          <p class="oa-form-hint">图片地址使用英文逗号分隔。AI 只分析通过上方上传的站内图片。</p>
        </div>

        <div class="form-section">
          <label class="oa-form-label">Description</label>
          <el-form-item prop="description">
            <el-input
              v-model="form.description"
              type="textarea"
              :rows="5"
              placeholder="Condition, specs, pickup method, etc."
              maxlength="500"
              show-word-limit
            />
          </el-form-item>
        </div>

        <hr class="oa-divider" />

        <div class="form-actions">
          <el-button type="primary" :loading="loading" @click="onSubmit">Publish listing</el-button>
          <el-button @click="onReset">Reset</el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Close, MagicStick, UploadFilled } from '@element-plus/icons-vue'
import { createProduct, uploadProductImages, deleteProductImage, getPublishQuota } from '@/api/product'
import { createAiListingDraft } from '@/api/ai'
import { CATEGORIES } from '@/constants/product'
import { useOnboarding } from '@/composables/useOnboarding'
import { useI18n } from '@/i18n'

const { t } = useI18n()
const router = useRouter()
const onboarding = useOnboarding()
const categories = CATEGORIES

const formRef = ref(null)
const loading = ref(false)
const uploading = ref(false)
const aiLoading = ref(false)
const aiNotes = ref('')
const uploadedUrls = ref([])
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
  images: '',
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
  images: [{ required: true, message: '请至少填写一张图片URL', trigger: 'blur' }]
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
    const cleanImages = form.images
      .split(',')
      .map((s) => s.trim())
      .filter(Boolean)
      .join(',')

    await createProduct({
      title: form.title,
      description: form.description,
      price: form.price,
      images: cleanImages,
      category: form.category,
      stock: form.stock
    })
    ElMessage.success('发布成功')
    await onboarding.refresh()
    router.push('/activity')
  } finally {
    loading.value = false
  }
}

async function onFilesSelected(event) {
  const selected = Array.from(event.target.files || [])
  event.target.value = ''
  if (!selected.length) return
  const remaining = 5 - uploadedUrls.value.length
  if (selected.length > remaining) {
    ElMessage.warning(`还可以上传 ${remaining} 张图片`)
    return
  }
  uploading.value = true
  try {
    const res = await uploadProductImages(selected)
    uploadedUrls.value.push(...(res.data.images || []))
    syncUploadedImagesToForm()
    draftInfo.value = null
  } finally {
    uploading.value = false
  }
}

async function removeUploadedImage(index) {
  const removed = uploadedUrls.value[index]
  uploadedUrls.value.splice(index, 1)
  const current = splitImages(form.images).filter((url) => url !== removed)
  form.images = current.join(',')
  draftInfo.value = null
  if (removed) {
    try {
      await deleteProductImage(removed)
    } catch (_) {
      /* 本地已移除；服务端清理失败不阻断发布 */
    }
  }
}

function syncUploadedImagesToForm() {
  const merged = [...uploadedUrls.value, ...splitImages(form.images).filter((url) => !url.startsWith('/api/product/image/'))]
  form.images = [...new Set(merged)].join(',')
}

async function generateDraft() {
  if (!uploadedUrls.value.length || aiLoading.value) return
  aiLoading.value = true
  try {
    const res = await createAiListingDraft({
      images: uploadedUrls.value,
      notes: aiNotes.value.trim()
    })
    const draft = res.data
    form.title = draft.title || ''
    form.description = draft.description || ''
    form.category = categories.includes(draft.category) ? draft.category : '其他'
    if (draft.suggestedPrice != null) form.price = Number(draft.suggestedPrice)
    draftInfo.value = draft
    ElMessage.success('AI 草稿已回填，请检查并修改后发布')
  } finally {
    aiLoading.value = false
  }
}

function splitImages(images) {
  return (images || '').split(',').map((value) => value.trim()).filter(Boolean)
}

function formatPrice(price) {
  const value = Number(price)
  return Number.isFinite(value) ? value.toFixed(2) : '--'
}

function onReset() {
  formRef.value?.resetFields()
  form.price = 0
  form.stock = 1
  uploadedUrls.value = []
  aiNotes.value = ''
  draftInfo.value = null
}
</script>

<style scoped>
.publish-panel {
  max-width: 640px;
}

.ai-draft-panel {
  max-width: 920px;
  margin-bottom: 16px;
}

.quota-alert {
  max-width: 920px;
  margin-bottom: 16px;
}

.ai-panel-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 18px;
}

.ai-panel-heading > div:nth-child(2) {
  flex: 1;
}

.ai-panel-heading h2 {
  font-size: 15px;
  font-weight: 600;
  margin-bottom: 3px;
}

.ai-panel-heading p {
  color: var(--oa-text-secondary);
  font-size: 12px;
}

.ai-icon {
  width: 36px;
  height: 36px;
  display: grid;
  place-items: center;
  border-radius: 10px;
  background: var(--oa-text);
  color: var(--oa-on-primary);
}

.ai-upload-row {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.upload-card,
.preview-card {
  position: relative;
  width: 126px;
  height: 112px;
  border: 1px dashed var(--oa-border);
  border-radius: 10px;
  overflow: hidden;
}

.upload-card {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 5px;
  cursor: pointer;
  color: var(--oa-text-secondary);
  background: var(--oa-bg);
}

.upload-card input { display: none; }
.upload-card span { font-size: 13px; color: var(--oa-text); }
.upload-card small { font-size: 10px; color: var(--oa-text-muted); }
.upload-card.disabled { opacity: .55; cursor: not-allowed; }

.preview-card { border-style: solid; }
.preview-card :deep(.el-image) { width: 100%; height: 100%; }
.preview-card button {
  position: absolute;
  right: 6px;
  top: 6px;
  width: 24px;
  height: 24px;
  border: 0;
  border-radius: 50%;
  display: grid;
  place-items: center;
  color: #fff;
  background: rgba(0, 0, 0, .65);
  cursor: pointer;
}
.preview-card > span {
  position: absolute;
  left: 6px;
  bottom: 6px;
  padding: 2px 6px;
  border-radius: 10px;
  color: #fff;
  background: rgba(0, 0, 0, .6);
  font-size: 10px;
}

.ai-notes-row {
  display: flex;
  gap: 10px;
  margin-top: 14px;
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

.draft-result strong { color: var(--oa-text); font-size: 14px; }
.draft-result ul { flex-basis: 100%; padding-left: 18px; color: #b45309; font-size: 12px; }

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

@media (max-width: 760px) {
  .ai-panel-heading { align-items: flex-start; flex-wrap: wrap; }
  .ai-notes-row, .form-row { flex-direction: column; }
}
</style>
