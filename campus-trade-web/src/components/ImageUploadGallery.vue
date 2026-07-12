<template>
  <div class="image-upload-gallery">
    <label
      class="upload-card"
      :class="{ disabled: uploading || modelValue.length >= maxCount }"
    >
      <input
        type="file"
        accept="image/jpeg,image/png,image/webp"
        multiple
        :disabled="uploading || modelValue.length >= maxCount"
        @change="onFilesSelected"
      />
      <el-icon :size="22"><UploadFilled /></el-icon>
      <span>{{ uploading ? uploadingText : uploadText }}</span>
      <small>{{ hintText }}</small>
    </label>

    <div v-for="(url, index) in modelValue" :key="url" class="preview-card">
      <el-image :src="url" fit="cover" />
      <button type="button" :aria-label="removeAria" @click="removeAt(index)">
        <el-icon><Close /></el-icon>
      </button>
      <span>{{ index + 1 }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Close, UploadFilled } from '@element-plus/icons-vue'
import { uploadProductImages, deleteProductImage } from '@/api/product'
import {
  prepareImageForUpload,
  compressErrorMessage,
  IMAGE_TARGET_MAX_BYTES
} from '@/utils/imageCompress'

const props = defineProps({
  modelValue: { type: Array, default: () => [] },
  maxCount: { type: Number, default: 5 },
  uploadText: { type: String, default: '上传图片' },
  uploadingText: { type: String, default: '处理中…' },
  hint: { type: String, default: '' },
  removeAria: { type: String, default: '移除图片' },
  deleteOnRemove: { type: Boolean, default: true }
})

const emit = defineEmits(['update:modelValue', 'change'])

const uploading = ref(false)

const hintText = computed(
  () =>
    props.hint ||
    `JPG / PNG / WEBP，最多 ${props.maxCount} 张；超过 ${Math.round(IMAGE_TARGET_MAX_BYTES / 1024 / 1024)}MB 自动压缩`
)

async function onFilesSelected(event) {
  const selected = Array.from(event.target.files || [])
  event.target.value = ''
  if (!selected.length) return

  const remaining = props.maxCount - props.modelValue.length
  if (remaining <= 0) {
    ElMessage.warning(`最多上传 ${props.maxCount} 张图片`)
    return
  }
  const batch = selected.slice(0, remaining)
  if (selected.length > remaining) {
    ElMessage.warning(`本次最多还能上传 ${remaining} 张，已截取前 ${remaining} 张`)
  }

  uploading.value = true
  let compressedCount = 0
  try {
    const prepared = []
    for (const raw of batch) {
      try {
        const { file, compressed } = await prepareImageForUpload(raw)
        if (compressed) compressedCount += 1
        prepared.push(file)
      } catch (err) {
        ElMessage.warning(compressErrorMessage(err?.message || 'type'))
      }
    }
    if (!prepared.length) return

    const res = await uploadProductImages(prepared)
    const urls = res.data?.images || []
    if (!urls.length) {
      ElMessage.error('上传失败，请重试')
      return
    }
    const next = [...props.modelValue, ...urls].slice(0, props.maxCount)
    emit('update:modelValue', next)
    emit('change', next)
    if (compressedCount > 0) {
      ElMessage.success(`已上传 ${urls.length} 张（其中 ${compressedCount} 张已压缩至 1MB 内）`)
    }
  } finally {
    uploading.value = false
  }
}

async function removeAt(index) {
  const removed = props.modelValue[index]
  const next = props.modelValue.filter((_, i) => i !== index)
  emit('update:modelValue', next)
  emit('change', next)
  if (props.deleteOnRemove && removed) {
    try {
      await deleteProductImage(removed)
    } catch {
      /* 本地已移除 */
    }
  }
}
</script>

<style scoped>
.image-upload-gallery {
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

.upload-card input {
  display: none;
}

.upload-card span {
  font-size: 13px;
  color: var(--oa-text);
}

.upload-card small {
  font-size: 10px;
  color: var(--oa-text-muted);
  text-align: center;
  padding: 0 6px;
  line-height: 1.3;
}

.upload-card.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.preview-card {
  border-style: solid;
}

.preview-card :deep(.el-image) {
  width: 100%;
  height: 100%;
}

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
  background: rgba(0, 0, 0, 0.65);
  cursor: pointer;
}

.preview-card > span {
  position: absolute;
  left: 6px;
  bottom: 6px;
  padding: 2px 6px;
  border-radius: 10px;
  color: #fff;
  background: rgba(0, 0, 0, 0.6);
  font-size: 10px;
}
</style>
