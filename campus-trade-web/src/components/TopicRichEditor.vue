<template>
  <div class="rich-editor" :class="{ focused }">
    <div class="rich-toolbar" role="toolbar">
      <button type="button" class="tb-btn" :title="t('topics.editorUndo')" @mousedown.prevent @click="run('undo')">
        <el-icon><RefreshLeft /></el-icon>
      </button>
      <button type="button" class="tb-btn" :title="t('topics.editorRedo')" @mousedown.prevent @click="run('redo')">
        <el-icon><RefreshRight /></el-icon>
      </button>
      <span class="tb-sep" />
      <button type="button" class="tb-btn" :title="t('topics.editorHeading')" @mousedown.prevent @click="run('formatBlock', 'H2')">H</button>
      <button type="button" class="tb-btn" :title="t('topics.editorBold')" @mousedown.prevent @click="run('bold')"><b>B</b></button>
      <button type="button" class="tb-btn" :title="t('topics.editorItalic')" @mousedown.prevent @click="run('italic')"><i>I</i></button>
      <button type="button" class="tb-btn" :title="t('topics.editorStrike')" @mousedown.prevent @click="run('strikeThrough')"><s>S</s></button>
      <span class="tb-sep" />
      <button type="button" class="tb-btn" :title="t('topics.editorBullet')" @mousedown.prevent @click="run('insertUnorderedList')">
        <el-icon><List /></el-icon>
      </button>
      <button type="button" class="tb-btn" :title="t('topics.editorOrdered')" @mousedown.prevent @click="run('insertOrderedList')">1.</button>
      <button type="button" class="tb-btn" :title="t('topics.editorQuote')" @mousedown.prevent @click="run('formatBlock', 'BLOCKQUOTE')">❝</button>
      <button type="button" class="tb-btn" :title="t('topics.editorHr')" @mousedown.prevent @click="insertHr">―</button>
      <button type="button" class="tb-btn code-btn" :title="t('topics.editorCode')" @mousedown.prevent @click="insertCode">&lt;/&gt;</button>
      <span class="tb-sep" />
      <button type="button" class="tb-btn" :title="t('topics.editorLink')" @mousedown.prevent @click="insertLink">
        <el-icon><Link /></el-icon>
      </button>
      <button
        type="button"
        class="tb-btn"
        :title="t('topics.editorImage')"
        :disabled="uploading"
        @mousedown.prevent
        @click="pickImage"
      >
        <el-icon><Picture /></el-icon>
      </button>
      <button type="button" class="tb-btn" :title="t('topics.editorVideo')" @mousedown.prevent @click="insertVideo">
        <el-icon><VideoCamera /></el-icon>
      </button>
      <span class="tb-sep" />
      <el-popover
        :visible="productMenuOpen"
        placement="bottom-start"
        :width="320"
        trigger="click"
        @update:visible="productMenuOpen = $event"
      >
        <template #reference>
          <button
            type="button"
            class="tb-btn tb-attach"
            :title="t('topics.attachProducts')"
            @mousedown.prevent
            @click="productMenuOpen = !productMenuOpen"
          >
            <el-icon><Goods /></el-icon>
            <span>{{ t('topics.attachProducts') }}</span>
            <span v-if="selectedCount > 0" class="attach-count">{{ selectedCount }}</span>
          </button>
        </template>
        <div class="product-menu">
          <p class="product-menu-hint">{{ t('topics.attachProductsHint') }}</p>
          <div v-loading="productsLoading" class="product-menu-list">
            <el-empty
              v-if="!productsLoading && products.length === 0"
              :description="t('topics.noProducts')"
              :image-size="48"
            />
            <label
              v-for="item in products"
              :key="item.productId"
              class="product-menu-item"
            >
              <el-checkbox
                :model-value="selectedIds.includes(item.productId)"
                :disabled="!selectedIds.includes(item.productId) && selectedIds.length >= 5"
                @change="(val) => toggleProduct(item.productId, val)"
              />
              <el-image v-if="item.cover" :src="item.cover" fit="cover" class="product-thumb" />
              <div v-else class="product-thumb placeholder" />
              <div class="product-meta">
                <span class="product-title">{{ item.title }}</span>
                <span class="product-price">¥{{ formatPrice(item.price) }}</span>
              </div>
            </label>
          </div>
        </div>
      </el-popover>

      <input
        ref="fileInput"
        type="file"
        accept="image/*"
        class="file-input"
        @change="onImageSelected"
      />
    </div>

    <div
      ref="editorEl"
      class="rich-body"
      :class="{ 'is-empty': isEmpty }"
      contenteditable="true"
      role="textbox"
      :data-placeholder="placeholder"
      @input="onInput"
      @focus="focused = true"
      @blur="focused = false"
      @paste="onPaste"
    />
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { ElMessage } from 'element-plus'
import {
  RefreshLeft,
  RefreshRight,
  List,
  Link,
  Picture,
  VideoCamera,
  Goods
} from '@element-plus/icons-vue'
import { useI18n } from '@/i18n'
import { uploadProductImages } from '@/api/product'
import { sanitizeHtml, isRichContentEmpty } from '@/utils/sanitizeHtml'

const props = defineProps({
  modelValue: { type: String, default: '' },
  productIds: { type: Array, default: () => [] },
  products: { type: Array, default: () => [] },
  productsLoading: { type: Boolean, default: false },
  placeholder: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue', 'update:productIds'])

const { t } = useI18n()
const editorEl = ref(null)
const fileInput = ref(null)
const focused = ref(false)
const uploading = ref(false)
const productMenuOpen = ref(false)
const selectedIds = ref([...(props.productIds || [])])

const selectedCount = computed(() => selectedIds.value.length)
const isEmpty = computed(() => isRichContentEmpty(props.modelValue))

watch(
  () => props.productIds,
  (ids) => {
    selectedIds.value = [...(ids || [])]
  }
)

watch(selectedIds, (ids) => {
  emit('update:productIds', [...ids])
}, { deep: true })

onMounted(() => {
  if (editorEl.value && props.modelValue) {
    editorEl.value.innerHTML = sanitizeHtml(props.modelValue)
  }
})

function formatPrice(price) {
  const n = Number(price)
  return Number.isFinite(n) ? n.toFixed(2) : price
}

function emitHtml() {
  const html = editorEl.value?.innerHTML || ''
  const cleaned = isRichContentEmpty(html) ? '' : sanitizeHtml(html)
  emit('update:modelValue', cleaned)
}

function onInput() {
  emitHtml()
}

function restoreSelection() {
  editorEl.value?.focus()
}

function run(command, value = null) {
  restoreSelection()
  document.execCommand(command, false, value)
  emitHtml()
}

function insertHr() {
  restoreSelection()
  document.execCommand('insertHorizontalRule')
  emitHtml()
}

function insertCode() {
  restoreSelection()
  const selection = window.getSelection()?.toString() || ''
  const code = selection || 'code'
  document.execCommand('insertHTML', false, `<pre><code>${escapeText(code)}</code></pre>`)
  emitHtml()
}

function insertLink() {
  restoreSelection()
  const url = window.prompt(t('topics.editorLinkPrompt'), 'https://')
  if (!url) return
  const trimmed = url.trim()
  if (!/^https?:\/\//i.test(trimmed) && !trimmed.startsWith('/')) {
    ElMessage.warning(t('topics.editorInvalidUrl'))
    return
  }
  const selection = window.getSelection()?.toString()
  if (selection) {
    document.execCommand('createLink', false, trimmed)
  } else {
    document.execCommand(
      'insertHTML',
      false,
      `<a href="${escapeAttr(trimmed)}" target="_blank" rel="noopener noreferrer">${escapeText(trimmed)}</a>`
    )
  }
  emitHtml()
}

function insertVideo() {
  restoreSelection()
  const url = window.prompt(t('topics.editorVideoPrompt'), 'https://')
  if (!url) return
  const trimmed = url.trim()
  if (!/^https?:\/\//i.test(trimmed)) {
    ElMessage.warning(t('topics.editorInvalidUrl'))
    return
  }
  document.execCommand(
    'insertHTML',
    false,
    `<p><video src="${escapeAttr(trimmed)}" controls preload="metadata"></video></p><p><br></p>`
  )
  emitHtml()
}

function pickImage() {
  fileInput.value?.click()
}

async function onImageSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  if (!file.type.startsWith('image/')) {
    ElMessage.warning(t('topics.editorImageOnly'))
    return
  }
  uploading.value = true
  try {
    const res = await uploadProductImages([file])
    const url = res.data?.images?.[0]
    if (!url) {
      ElMessage.error(t('topics.editorUploadFail'))
      return
    }
    restoreSelection()
    await nextTick()
    document.execCommand(
      'insertHTML',
      false,
      `<p><img src="${escapeAttr(url)}" alt="" /></p><p><br></p>`
    )
    emitHtml()
  } catch {
    ElMessage.error(t('topics.editorUploadFail'))
  } finally {
    uploading.value = false
  }
}

function onPaste(event) {
  const items = event.clipboardData?.items
  if (!items) return
  for (const item of items) {
    if (item.type.startsWith('image/')) {
      event.preventDefault()
      const file = item.getAsFile()
      if (file) {
        const dt = new DataTransfer()
        dt.items.add(file)
        onImageSelected({ target: { files: dt.files, value: '' } })
      }
      return
    }
  }
}

function toggleProduct(id, checked) {
  if (checked) {
    if (selectedIds.value.length >= 5) return
    if (!selectedIds.value.includes(id)) selectedIds.value = [...selectedIds.value, id]
  } else {
    selectedIds.value = selectedIds.value.filter((x) => x !== id)
  }
}

function escapeText(text) {
  return String(text)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
}

function escapeAttr(text) {
  return escapeText(text).replace(/"/g, '&quot;')
}
</script>

<style scoped>
.rich-editor {
  border-radius: 16px;
  background: var(--oa-bg-elevated, #f4f5f7);
  overflow: hidden;
  transition: box-shadow 0.2s ease, background 0.2s ease;
}

.rich-editor.focused {
  background: var(--oa-bg-elevated, #f7f8fa);
  box-shadow: 0 0 0 2px color-mix(in srgb, var(--el-color-primary) 28%, transparent);
}

.rich-toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 2px;
  padding: 8px 10px;
  background: transparent;
}

.tb-btn {
  border: 0;
  background: transparent;
  color: var(--oa-text-secondary);
  min-width: 32px;
  height: 32px;
  padding: 0 8px;
  border-radius: 10px;
  cursor: pointer;
  font-size: 13px;
  line-height: 1;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 4px;
}

.tb-btn:hover:not(:disabled) {
  background: color-mix(in srgb, var(--oa-text) 6%, transparent);
  color: var(--oa-text);
}

.tb-btn:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.tb-btn .el-icon {
  font-size: 16px;
}

.code-btn {
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 12px;
}

.tb-attach {
  font-size: 12px;
  font-weight: 600;
  padding: 0 10px;
}

.attach-count {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 999px;
  background: var(--el-color-primary);
  color: #fff;
  font-size: 11px;
}

.tb-sep {
  width: 1px;
  height: 18px;
  margin: 0 4px;
  background: color-mix(in srgb, var(--oa-text) 12%, transparent);
}

.rich-body.is-empty::before {
  content: attr(data-placeholder);
  color: var(--oa-text-muted);
  pointer-events: none;
  position: absolute;
}

.rich-body {
  position: relative;
  min-height: 220px;
  max-height: 520px;
  overflow: auto;
  padding: 14px 18px 20px;
  outline: none;
  font-size: 15px;
  line-height: 1.7;
  color: var(--oa-text);
}

.rich-body :deep(img),
.rich-body :deep(video) {
  max-width: 100%;
  border-radius: 12px;
  display: block;
  margin: 10px 0;
}

.rich-body :deep(blockquote) {
  margin: 10px 0;
  padding: 8px 14px;
  border-left: 3px solid var(--el-color-primary);
  color: var(--oa-text-secondary);
  background: color-mix(in srgb, var(--oa-text) 4%, transparent);
  border-radius: 0 10px 10px 0;
}

.rich-body :deep(pre) {
  padding: 12px 14px;
  border-radius: 12px;
  background: color-mix(in srgb, var(--oa-text) 6%, transparent);
  overflow: auto;
  font-size: 13px;
}

.rich-body :deep(a) {
  color: var(--el-color-primary);
}

.file-input {
  display: none;
}

.product-menu-hint {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.45;
}

.product-menu-list {
  max-height: 260px;
  overflow: auto;
}

.product-menu-item {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 8px;
  border-radius: 12px;
  cursor: pointer;
}

.product-menu-item:hover {
  background: color-mix(in srgb, var(--oa-text) 5%, transparent);
}

.product-thumb {
  width: 44px;
  height: 44px;
  border-radius: 10px;
  flex-shrink: 0;
  background: color-mix(in srgb, var(--oa-text) 6%, transparent);
}

.product-thumb.placeholder {
  display: block;
}

.product-meta {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.product-title {
  font-size: 13px;
  color: var(--oa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  font-size: 12px;
  color: var(--oa-text-secondary);
}
</style>
