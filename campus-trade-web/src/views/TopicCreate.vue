<template>
  <div class="page-container topic-create-page">
    <div class="page-header">
      <button type="button" class="oa-back-btn" @click="$router.push('/topics')">
        <el-icon><ArrowLeft /></el-icon>
        {{ t('topics.back') }}
      </button>
      <h1 class="page-title">{{ t('topics.composeTitle') }}</h1>
      <p class="page-subtitle">{{ t('topics.subtitle') }}</p>
    </div>

    <div class="oa-panel create-panel">
      <el-form label-position="top" @submit.prevent="onSubmit">
        <el-form-item :label="t('topics.postTitle')" required>
          <el-input
            v-model="form.title"
            :placeholder="t('topics.titlePlaceholder')"
            maxlength="100"
            show-word-limit
          />
        </el-form-item>
        <el-form-item :label="t('topics.postContent')" required>
          <el-input
            v-model="form.content"
            type="textarea"
            :rows="8"
            :placeholder="t('topics.postContentPlaceholder')"
            maxlength="5000"
            show-word-limit
          />
        </el-form-item>
        <el-form-item>
          <div class="tip-switch-row">
            <div class="tip-switch-text">
              <span class="tip-switch-label">{{ t('topics.enableTip') }}</span>
              <span class="tip-switch-hint">{{ t('topics.enableTipHint') }}</span>
            </div>
            <el-switch v-model="form.tipEnabled" />
          </div>
        </el-form-item>
        <div class="form-actions">
          <el-button @click="$router.push('/topics')">{{ t('topics.cancel') }}</el-button>
          <el-button type="primary" :loading="submitting" @click="onSubmit">
            {{ submitting ? t('topics.publishing') : t('topics.publish') }}
          </el-button>
        </div>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowLeft } from '@element-plus/icons-vue'
import { createTopicPost } from '@/api/topic'
import { useI18n } from '@/i18n'

const router = useRouter()
const { t } = useI18n()

const submitting = ref(false)
const form = reactive({
  title: '',
  content: '',
  tipEnabled: false
})

async function onSubmit() {
  const title = form.title.trim()
  const content = form.content.trim()
  if (!title) {
    ElMessage.warning(t('topics.titleRequired'))
    return
  }
  if (!content) {
    ElMessage.warning(t('topics.contentRequired'))
    return
  }
  submitting.value = true
  try {
    const res = await createTopicPost({
      title,
      content,
      tipEnabled: form.tipEnabled
    })
    ElMessage.success(t('topics.publishDone'))
    const id = res.data?.postId || res.data?.id
    if (id) router.push(`/topics/${id}`)
    else router.push('/topics')
  } catch {
    // request 拦截器已提示
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.topic-create-page {
  max-width: 720px;
}

.create-panel {
  padding: 24px;
}

.tip-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  width: 100%;
}

.tip-switch-text {
  display: flex;
  flex-direction: column;
  gap: 2px;
  min-width: 0;
}

.tip-switch-label {
  font-size: 14px;
  color: var(--oa-text);
}

.tip-switch-hint {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.form-actions {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 8px;
}

.oa-back-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  border: none;
  background: transparent;
  color: var(--oa-text-secondary);
  font-size: 13px;
  cursor: pointer;
  padding: 0;
  margin-bottom: 12px;
}

.oa-back-btn:hover {
  color: var(--oa-text);
}
</style>
