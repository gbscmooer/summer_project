<template>
  <div class="comment-bar" :class="{ focused: focused || modelValue || imageUrl }">
    <textarea
      ref="textareaRef"
      :value="modelValue"
      class="comment-input"
      rows="3"
      :placeholder="placeholder"
      maxlength="1000"
      @input="$emit('update:modelValue', $event.target.value)"
      @focus="focused = true"
    />
    <div v-if="imageUrl" class="image-preview">
      <el-image :src="imageUrl" fit="cover" class="preview-img" />
      <button type="button" class="preview-remove" @click="clearImage">×</button>
    </div>
    <div class="comment-toolbar">
      <div class="toolbar-left">
        <button
          type="button"
          class="tool-btn"
          :disabled="uploading || !canUpload"
          :title="t('topics.attachImage')"
          @click="pickImage"
        >
          <el-icon :size="18"><Picture /></el-icon>
        </button>
        <button type="button" class="tool-btn is-disabled" disabled :title="t('topics.mediaUnavailable')">
          <el-icon :size="18"><VideoCamera /></el-icon>
        </button>
        <button type="button" class="tool-btn is-disabled" disabled :title="t('topics.mediaUnavailable')">
          GIF
        </button>
        <button type="button" class="tool-btn is-disabled" disabled :title="t('topics.mediaUnavailable')">
          Aa
        </button>
      </div>
      <div class="toolbar-right">
        <el-button round @click="onCancel">{{ t('topics.cancel') }}</el-button>
        <el-button
          type="primary"
          round
          :loading="submitting"
          :disabled="!canSubmit"
          @click="$emit('submit')"
        >
          {{ submitLabel }}
        </el-button>
      </div>
    </div>
    <input
      ref="fileRef"
      type="file"
      accept="image/jpeg,image/png,image/webp"
      class="hidden-file"
      @change="onFileChange"
    />
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Picture, VideoCamera } from '@element-plus/icons-vue'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import { uploadProductImages } from '@/api/product'

const props = defineProps({
  modelValue: { type: String, default: '' },
  imageUrl: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  submitLabel: { type: String, default: '' },
  submitting: { type: Boolean, default: false }
})

const emit = defineEmits(['update:modelValue', 'update:imageUrl', 'submit', 'cancel'])

const { t } = useI18n()
const userStore = useUserStore()

const focused = ref(false)
const uploading = ref(false)
const textareaRef = ref(null)
const fileRef = ref(null)

const canUpload = computed(() => userStore.isLogin)
const canSubmit = computed(() => props.modelValue.trim().length > 0 && !props.submitting && !uploading.value)

function onCancel() {
  focused.value = false
  emit('cancel')
}

function clearImage() {
  emit('update:imageUrl', '')
}

function pickImage() {
  if (!userStore.isLogin) {
    ElMessage.warning(t('topics.loginToComment'))
    return
  }
  fileRef.value?.click()
}

async function onFileChange(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  uploading.value = true
  try {
    const res = await uploadProductImages([file])
    const url = res.data?.images?.[0]
    if (url) {
      emit('update:imageUrl', url)
    }
  } catch {
    // request interceptor shows error
  } finally {
    uploading.value = false
  }
}

defineExpose({
  focus() {
    focused.value = true
    textareaRef.value?.focus()
  }
})
</script>

<style scoped>
.comment-bar {
  border: 1px solid var(--oa-border-subtle);
  border-radius: 16px;
  background: var(--oa-bg-elevated, #fff);
  overflow: hidden;
  transition: border-color 0.15s ease, box-shadow 0.15s ease;
}

.comment-bar.focused {
  border-color: var(--oa-border-strong, var(--el-color-primary-light-5));
  box-shadow: 0 0 0 1px rgba(0, 0, 0, 0.02);
}

.comment-input {
  display: block;
  width: 100%;
  border: 0;
  outline: none;
  resize: vertical;
  min-height: 72px;
  padding: 14px 16px 8px;
  font: inherit;
  font-size: 14px;
  line-height: 1.55;
  color: var(--oa-text);
  background: transparent;
}

.comment-input::placeholder {
  color: var(--oa-text-muted);
}

.image-preview {
  position: relative;
  display: inline-block;
  margin: 0 16px 8px;
}

.preview-img {
  width: 96px;
  height: 96px;
  border-radius: 10px;
  border: 1px solid var(--oa-border-subtle);
}

.preview-remove {
  position: absolute;
  top: -6px;
  right: -6px;
  width: 22px;
  height: 22px;
  border: 0;
  border-radius: 50%;
  background: var(--oa-text);
  color: #fff;
  cursor: pointer;
  line-height: 1;
}

.comment-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 8px 12px 10px;
  border-top: 1px solid var(--oa-border-subtle);
}

.toolbar-left {
  display: flex;
  align-items: center;
  gap: 2px;
}

.tool-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 34px;
  height: 34px;
  border: 0;
  border-radius: 8px;
  background: transparent;
  color: var(--oa-text-secondary);
  cursor: pointer;
  font-size: 12px;
  font-weight: 600;
}

.tool-btn:hover:not(:disabled) {
  background: var(--oa-bg-elevated);
  color: var(--oa-text);
}

.tool-btn.is-disabled,
.tool-btn:disabled {
  opacity: 0.35;
  cursor: not-allowed;
}

.toolbar-right {
  display: flex;
  gap: 8px;
}

.hidden-file {
  display: none;
}

@media (max-width: 640px) {
  .comment-toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-right {
    justify-content: flex-end;
  }
}
</style>
