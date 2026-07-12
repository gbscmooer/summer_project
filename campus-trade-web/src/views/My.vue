<template>
  <div class="page-container">
    <div class="page-header">
      <h1 class="page-title">{{ t('profile.title') }}</h1>
      <el-button type="primary" size="small" @click="openEdit">{{ t('profile.edit') }}</el-button>
    </div>

    <div class="oa-panel profile-panel" v-loading="infoLoading">
      <div v-if="info" class="profile-row">
        <el-avatar :size="56" :src="info.avatar" class="profile-avatar">
          {{ (info.nickname || info.username || 'U').charAt(0) }}
        </el-avatar>
        <div class="profile-fields">
          <div class="field-row">
            <div class="field">
              <span class="field-label">{{ t('profile.username') }}</span>
              <span class="field-value">{{ info.username }}</span>
            </div>
            <div class="field">
              <span class="field-label">{{ t('profile.nickname') }}</span>
              <span class="field-value">{{ info.nickname }}</span>
            </div>
          </div>
          <div class="field-row">
            <div class="field">
              <span class="field-label">{{ t('profile.phone') }}</span>
              <span class="field-value">{{ info.phone || t('profile.notSet') }}</span>
            </div>
            <div class="field">
              <span class="field-label">{{ t('profile.accountType') }}</span>
              <span class="field-value">
                <el-tag size="small" :type="roleTagType">{{ userStore.roleLabel }}</el-tag>
              </span>
            </div>
            <div class="field">
              <span class="field-label">{{ t('profile.joined') }}</span>
              <span class="field-value">{{ info.createTime }}</span>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div class="oa-panel quick-link-panel">
      <p class="quick-link-hint">{{ t('profile.activityHint') }}</p>
      <el-button @click="$router.push('/activity')">{{ t('profile.goActivity') }}</el-button>
    </div>

    <el-dialog v-model="editVisible" :title="t('profile.edit')" width="420px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item :label="t('profile.nickname')" prop="nickname">
          <el-input v-model="editForm.nickname" :placeholder="t('profile.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('profile.phone')" prop="phone">
          <el-input v-model="editForm.phone" :placeholder="t('profile.phonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('profile.avatarUrl')" prop="avatar">
          <el-input v-model="editForm.avatar" :placeholder="t('profile.avatarPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('settings.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">{{ t('settings.confirm') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getUserInfo, updateUserInfo } from '@/api/user'
import { useOnboarding } from '@/composables/useOnboarding'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'

const { t } = useI18n()
const onboarding = useOnboarding()
const userStore = useUserStore()

const info = ref(null)
const infoLoading = ref(false)

const roleTagType = computed(() => {
  if (userStore.isAdmin) return 'danger'
  if (userStore.isMerchant) return 'success'
  return 'info'
})

async function fetchInfo() {
  infoLoading.value = true
  try {
    const res = await getUserInfo()
    info.value = res.data
    userStore.setUserInfo(res.data)
  } catch {
    info.value = null
  } finally {
    infoLoading.value = false
  }
}

const editVisible = ref(false)
const saving = ref(false)
const editFormRef = ref(null)
const editForm = reactive({ nickname: '', phone: '', avatar: '' })

const editRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

function openEdit() {
  if (!info.value) return
  editForm.nickname = info.value.nickname || ''
  editForm.phone = info.value.phone || ''
  editForm.avatar = info.value.avatar || ''
  editVisible.value = true
}

async function onSaveEdit() {
  if (!editFormRef.value) return
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  saving.value = true
  try {
    await updateUserInfo({
      nickname: editForm.nickname,
      avatar: editForm.avatar,
      phone: editForm.phone
    })
    ElMessage.success(t('profile.updated'))
    editVisible.value = false
    await fetchInfo()
    onboarding.trackStep('profile')
  } finally {
    saving.value = false
  }
}

onMounted(fetchInfo)
</script>

<style scoped>
.profile-panel {
  margin-bottom: 16px;
}

.profile-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
}

.profile-avatar {
  flex-shrink: 0;
  font-size: 20px;
  font-weight: 600;
}

.profile-fields {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.field-row {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 24px;
}

.field-label {
  display: block;
  font-size: 12px;
  color: var(--oa-text-muted);
  margin-bottom: 4px;
}

.field-value {
  font-size: 14px;
  color: var(--oa-text);
}

.quick-link-panel {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
}

.quick-link-hint {
  margin: 0;
  font-size: 14px;
  color: var(--oa-text-secondary);
}

@media (max-width: 640px) {
  .field-row {
    grid-template-columns: 1fr;
    gap: 12px;
  }

  .profile-row {
    flex-direction: column;
  }

  .quick-link-panel {
    flex-direction: column;
    align-items: flex-start;
  }
}
</style>
