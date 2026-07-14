<template>
  <el-dialog
    :model-value="modelValue"
    :title="title"
    :width="dialogWidth"
    destroy-on-close
    append-to-body
    class="image-crop-dialog"
    @update:model-value="onVisibleChange"
  >
    <div class="crop-body">
      <div class="crop-stage" :class="{ round: aspectRatio === 1 }">
        <img ref="imageRef" class="crop-source" alt="" />
      </div>
      <div class="crop-toolbar">
        <span class="toolbar-label">{{ t('profile.cropZoom') }}</span>
        <el-slider
          v-model="zoom"
          :min="0"
          :max="100"
          :show-tooltip="false"
          class="zoom-slider"
          @update:model-value="onZoomInput"
        />
        <div class="toolbar-actions">
          <el-button size="small" @click="rotate(-90)">{{ t('profile.cropRotateLeft') }}</el-button>
          <el-button size="small" @click="rotate(90)">{{ t('profile.cropRotateRight') }}</el-button>
        </div>
      </div>
      <p class="crop-hint">{{ t('profile.cropHint') }}</p>
    </div>
    <template #footer>
      <el-button @click="close">{{ t('settings.cancel') }}</el-button>
      <el-button type="primary" :loading="confirming" @click="onConfirm">
        {{ t('profile.cropConfirm') }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, watch, nextTick, onBeforeUnmount } from 'vue'
import Cropper from 'cropperjs'
import 'cropperjs/dist/cropper.css'
import { useI18n } from '@/i18n'
import { getCroppedFile } from '@/utils/cropImage'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  /** Object URL or data URL of the source image */
  imageSrc: { type: String, default: '' },
  title: { type: String, default: '' },
  /** Aspect ratio width/height; 1 = square avatar, ~3 = cover */
  aspectRatio: { type: Number, default: 1 },
  outputMaxWidth: { type: Number, default: 1024 },
  outputMaxHeight: { type: Number, default: 1024 },
  fileName: { type: String, default: 'cropped.jpg' },
  dialogWidth: { type: String, default: '560px' }
})

const emit = defineEmits(['update:modelValue', 'confirm'])

const { t } = useI18n()
const imageRef = ref(null)
const zoom = ref(0)
const confirming = ref(false)

let cropper = null
let minZoomRatio = 0
let maxZoomRatio = 3

function destroyCropper() {
  if (cropper) {
    cropper.destroy()
    cropper = null
  }
}

async function initCropper() {
  destroyCropper()
  await nextTick()
  const img = imageRef.value
  if (!img || !props.imageSrc) return

  img.src = props.imageSrc
  await new Promise((resolve, reject) => {
    if (img.complete && img.naturalWidth) {
      resolve()
      return
    }
    img.onload = () => resolve()
    img.onerror = () => reject(new Error('decode'))
  })

  cropper = new Cropper(img, {
    aspectRatio: props.aspectRatio > 0 ? props.aspectRatio : NaN,
    viewMode: 1,
    dragMode: 'move',
    autoCropArea: 1,
    responsive: true,
    background: false,
    guides: true,
    center: true,
    highlight: false,
    cropBoxMovable: true,
    cropBoxResizable: true,
    toggleDragModeOnDblclick: false,
    ready() {
      try {
        const imageData = cropper.getImageData()
        minZoomRatio = imageData.width / imageData.naturalWidth
        maxZoomRatio = Math.max(minZoomRatio * 3, minZoomRatio + 0.01)
        zoom.value = 0
      } catch {
        // ignore
      }
    }
  })
}

function onZoomInput(value) {
  if (!cropper) return
  const ratio = minZoomRatio + ((maxZoomRatio - minZoomRatio) * Number(value)) / 100
  cropper.zoomTo(ratio)
}

function rotate(deg) {
  if (!cropper) return
  cropper.rotate(deg)
}

function close() {
  emit('update:modelValue', false)
}

function onVisibleChange(visible) {
  emit('update:modelValue', visible)
}

async function onConfirm() {
  if (!cropper || confirming.value) return
  confirming.value = true
  try {
    const file = await getCroppedFile(cropper, {
      fileName: props.fileName,
      maxWidth: props.outputMaxWidth,
      maxHeight: props.outputMaxHeight
    })
    emit('confirm', file)
    close()
  } catch {
    emit('confirm', null)
  } finally {
    confirming.value = false
  }
}

watch(
  () => [props.modelValue, props.imageSrc],
  async ([visible, src]) => {
    if (visible && src) {
      try {
        await initCropper()
      } catch {
        destroyCropper()
      }
    } else {
      destroyCropper()
    }
  }
)

onBeforeUnmount(() => {
  destroyCropper()
})
</script>

<style scoped>
.crop-body {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.crop-stage {
  height: 360px;
  background: #1a1a1a;
  border-radius: 10px;
  overflow: hidden;
}

.crop-stage.round :deep(.cropper-view-box),
.crop-stage.round :deep(.cropper-face) {
  border-radius: 50%;
}

.crop-source {
  display: block;
  max-width: 100%;
}

.crop-toolbar {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
}

.toolbar-label {
  font-size: 13px;
  color: var(--oa-text-secondary);
  flex-shrink: 0;
}

.zoom-slider {
  flex: 1;
  min-width: 120px;
}

.toolbar-actions {
  display: flex;
  gap: 6px;
}

.crop-hint {
  margin: 0;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.5;
}
</style>
