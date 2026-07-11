<template>
  <div class="page-container settings-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('settings.title') }}</h1>
    </div>

    <!-- 语言 -->
    <div class="oa-panel">
      <div class="oa-panel-header">
        <div>
          <div class="oa-panel-title">{{ t('settings.language') }}</div>
          <p class="setting-desc">{{ t('settings.languageDesc') }}</p>
        </div>
      </div>
      <el-radio-group :model-value="settings.locale" @change="onLocaleChange">
        <el-radio-button value="zh-CN">中文</el-radio-button>
        <el-radio-button value="en-US">English</el-radio-button>
      </el-radio-group>
    </div>

    <!-- 外观 -->
    <div class="oa-panel">
      <div class="oa-panel-header">
        <div>
          <div class="oa-panel-title">{{ t('settings.appearance') }}</div>
          <p class="setting-desc">{{ t('settings.appearanceDesc') }}</p>
        </div>
      </div>

      <div class="setting-row">
        <div class="setting-meta">
          <span class="setting-label">{{ t('settings.theme') }}</span>
          <span class="setting-hint">{{ t('settings.themeDesc') }}</span>
        </div>
        <el-radio-group :model-value="settings.theme" size="small" @change="onThemeChange">
          <el-radio-button value="light">{{ t('settings.themeLight') }}</el-radio-button>
          <el-radio-button value="dark">{{ t('settings.themeDark') }}</el-radio-button>
          <el-radio-button value="system">{{ t('settings.themeSystem') }}</el-radio-button>
        </el-radio-group>
      </div>

      <div class="setting-row">
        <div class="setting-meta">
          <span class="setting-label">{{ t('settings.compactMode') }}</span>
          <span class="setting-hint">{{ t('settings.compactModeDesc') }}</span>
        </div>
        <el-switch
          :model-value="settings.compactMode"
          @change="settings.setCompactMode"
        />
      </div>

      <div class="setting-row">
        <div class="setting-meta">
          <span class="setting-label">{{ t('settings.reduceMotion') }}</span>
          <span class="setting-hint">{{ t('settings.reduceMotionDesc') }}</span>
        </div>
        <el-switch
          :model-value="settings.reduceMotion"
          @change="settings.setReduceMotion"
        />
      </div>
    </div>

    <!-- 账户 -->
    <div class="oa-panel">
      <div class="oa-panel-header">
        <div>
          <div class="oa-panel-title">{{ t('settings.account') }}</div>
          <p class="setting-desc">{{ t('settings.accountDesc') }}</p>
        </div>
      </div>
      <div class="setting-actions">
        <el-button v-if="userStore.isLogin" @click="$router.push('/my')">
          {{ t('settings.goProfile') }}
        </el-button>
        <el-button v-else type="primary" @click="$router.push('/login')">
          {{ t('common.login') }}
        </el-button>
        <el-button
          v-if="userStore.isLogin"
          type="danger"
          @click="onLogout"
        >
          {{ t('settings.logout') }}
        </el-button>
      </div>
    </div>

    <!-- 管理员：AI API 配置 -->
    <div v-if="userStore.isAdmin" class="oa-panel">
      <div class="oa-panel-header">
        <div>
          <div class="oa-panel-title">{{ t('settings.aiAdmin') }}</div>
          <p class="setting-desc">{{ t('settings.aiAdminDesc') }}</p>
        </div>
        <el-tag effect="plain">{{ aiForm.activeSource === 'admin' ? t('settings.aiSourceAdmin') : t('settings.aiSourceEnv') }}</el-tag>
      </div>

      <el-form label-position="top" class="ai-admin-form" @submit.prevent>
        <el-form-item :label="t('settings.aiEnabled')">
          <el-switch v-model="aiForm.enabled" />
          <span class="setting-hint inline-hint">{{ t('settings.aiEnabledHint') }}</span>
        </el-form-item>
        <el-form-item :label="t('settings.aiBaseUrl')">
          <el-input v-model="aiForm.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item :label="t('settings.aiModel')">
          <el-input v-model="aiForm.model" placeholder="gpt-4.1-mini" />
        </el-form-item>
        <el-form-item :label="t('settings.aiApiKey')">
          <el-input
            v-model="aiForm.apiKey"
            type="password"
            show-password
            :placeholder="aiForm.apiKeyMasked || t('settings.aiApiKeyPlaceholder')"
          />
          <p class="setting-hint">{{ t('settings.aiApiKeyHint') }}</p>
        </el-form-item>
        <div class="ai-admin-row">
          <el-form-item :label="t('settings.aiTimeout')">
            <el-input-number v-model="aiForm.timeoutSeconds" :min="5" :max="180" />
          </el-form-item>
          <el-form-item :label="t('settings.aiVision')">
            <el-switch v-model="aiForm.supportsVision" />
          </el-form-item>
        </div>
        <div class="setting-actions">
          <el-button :loading="aiLoading" @click="loadAiConfig">{{ t('settings.aiReload') }}</el-button>
          <el-button type="primary" :loading="aiSaving" @click="saveAiConfig">{{ t('settings.aiSave') }}</el-button>
        </div>
        <p v-if="aiForm.envModel" class="setting-hint">
          {{ t('settings.aiEnvFallback') }}：{{ aiForm.envBaseUrl }} / {{ aiForm.envModel }}
        </p>
      </el-form>
    </div>

    <!-- 数据 -->
    <div class="oa-panel">
      <div class="oa-panel-header">
        <div>
          <div class="oa-panel-title">{{ t('settings.data') }}</div>
          <p class="setting-desc">{{ t('settings.dataDesc') }}</p>
        </div>
      </div>

      <div class="setting-row">
        <div class="setting-meta">
          <span class="setting-label">{{ t('settings.clearCache') }}</span>
          <span class="setting-hint">{{ t('settings.clearCacheDesc') }}</span>
        </div>
        <el-button size="small" @click="onClearCache">{{ t('settings.clearCache') }}</el-button>
      </div>

      <div class="setting-row">
        <div class="setting-meta">
          <span class="setting-label">{{ t('settings.resetPrefs') }}</span>
          <span class="setting-hint">{{ t('settings.resetPrefsDesc') }}</span>
        </div>
        <el-button size="small" type="danger" @click="onResetPrefs">
          {{ t('settings.resetPrefs') }}
        </el-button>
      </div>
    </div>

    <!-- 关于 -->
    <div class="oa-panel">
      <div class="oa-panel-header">
        <div>
          <div class="oa-panel-title">{{ t('settings.about') }}</div>
          <p class="setting-desc">{{ t('settings.aboutDesc') }}</p>
        </div>
      </div>
      <div class="about-grid">
        <div class="about-item">
          <span class="field-label">{{ t('settings.version') }}</span>
          <span class="field-value">1.0.0</span>
        </div>
        <div class="about-item">
          <span class="field-value muted">{{ t('settings.savedLocally') }}</span>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { useI18n } from '@/i18n'
import { useSettingsStore } from '@/store/settings'
import { useUserStore } from '@/store/user'
import { getUserInfo } from '@/api/user'
import { getAiAdminConfig, saveAiAdminConfig } from '@/api/admin'

const { t } = useI18n()
const settings = useSettingsStore()
const userStore = useUserStore()
const router = useRouter()

const aiLoading = ref(false)
const aiSaving = ref(false)
const aiForm = reactive({
  enabled: false,
  baseUrl: '',
  model: '',
  apiKey: '',
  apiKeyMasked: '',
  timeoutSeconds: 60,
  supportsVision: true,
  activeSource: 'env',
  envBaseUrl: '',
  envModel: ''
})

onMounted(async () => {
  if (!userStore.isLogin) return
  try {
    const res = await getUserInfo()
    userStore.setUserInfo(res.data)
  } catch (_) {
    /* 保持本地缓存 */
  }
  if (userStore.isAdmin) {
    await loadAiConfig()
  }
})

async function loadAiConfig() {
  aiLoading.value = true
  try {
    const res = await getAiAdminConfig()
    const data = res.data || {}
    aiForm.enabled = !!data.enabled
    aiForm.baseUrl = data.baseUrl || ''
    aiForm.model = data.model || ''
    aiForm.apiKey = ''
    aiForm.apiKeyMasked = data.apiKeyMasked || ''
    aiForm.timeoutSeconds = data.timeoutSeconds || 60
    aiForm.supportsVision = data.supportsVision !== false
    aiForm.activeSource = data.activeSource || 'env'
    aiForm.envBaseUrl = data.envBaseUrl || ''
    aiForm.envModel = data.envModel || ''
  } finally {
    aiLoading.value = false
  }
}

async function saveAiConfig() {
  aiSaving.value = true
  try {
    const res = await saveAiAdminConfig({
      enabled: aiForm.enabled,
      baseUrl: aiForm.baseUrl.trim(),
      model: aiForm.model.trim(),
      apiKey: aiForm.apiKey,
      timeoutSeconds: aiForm.timeoutSeconds,
      supportsVision: aiForm.supportsVision
    })
    const data = res.data || {}
    aiForm.enabled = !!data.enabled
    aiForm.baseUrl = data.baseUrl || aiForm.baseUrl
    aiForm.model = data.model || aiForm.model
    aiForm.apiKey = ''
    aiForm.apiKeyMasked = data.apiKeyMasked || ''
    aiForm.timeoutSeconds = data.timeoutSeconds || aiForm.timeoutSeconds
    aiForm.supportsVision = data.supportsVision !== false
    aiForm.activeSource = data.activeSource || aiForm.activeSource
    ElMessage.success(t('settings.aiSaveDone'))
  } finally {
    aiSaving.value = false
  }
}

function onLocaleChange(locale) {
  settings.setLocale(locale)
}

function onThemeChange(theme) {
  settings.setTheme(theme)
}

function onLogout() {
  ElMessageBox.confirm(t('settings.logoutConfirm'), t('settings.tip'), {
    confirmButtonText: t('settings.confirm'),
    cancelButtonText: t('settings.cancel'),
    type: 'warning'
  })
    .then(() => {
      userStore.logout()
      ElMessage.success(t('settings.logoutSuccess'))
      router.push('/login')
    })
    .catch(() => {})
}

function onClearCache() {
  const keep = new Set(['token', 'userInfo', 'campusTradeSettings'])
  const keys = []
  for (let i = 0; i < localStorage.length; i++) {
    const key = localStorage.key(i)
    if (key && !keep.has(key)) keys.push(key)
  }
  keys.forEach((key) => localStorage.removeItem(key))
  ElMessage.success(t('settings.clearCacheDone'))
}

function onResetPrefs() {
  ElMessageBox.confirm(t('settings.resetPrefsConfirm'), t('settings.tip'), {
    confirmButtonText: t('settings.confirm'),
    cancelButtonText: t('settings.cancel'),
    type: 'warning'
  })
    .then(() => {
      settings.resetPreferences()
      ElMessage.success(t('settings.resetPrefsDone'))
    })
    .catch(() => {})
}
</script>

<style scoped>
.setting-desc {
  margin-top: 4px;
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.45;
  font-weight: 400;
}

.setting-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 14px 0;
  border-top: 1px solid var(--oa-border-subtle);
}

.setting-row:first-of-type {
  border-top: none;
  padding-top: 0;
}

.setting-meta {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.setting-label {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
}

.setting-hint {
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.4;
}

.setting-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.ai-admin-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.ai-admin-row {
  display: flex;
  flex-wrap: wrap;
  gap: 24px;
}

.inline-hint {
  margin-left: 12px;
}

.about-grid {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.about-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.field-label {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.field-value {
  font-size: 14px;
  color: var(--oa-text);
}

.field-value.muted {
  color: var(--oa-text-secondary);
  font-size: 13px;
}

@media (max-width: 640px) {
  .setting-row {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
