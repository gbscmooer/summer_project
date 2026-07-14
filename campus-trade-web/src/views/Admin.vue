<template>
  <div class="page-container admin-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('admin.title') }}</h1>
      <p class="page-subtitle">{{ pageSubtitle }}</p>
    </div>

    <div class="oa-panel admin-panel">
      <el-tabs v-model="activeTab" class="admin-tabs">
        <!-- 发送通知（管理员与特殊认证均可） -->
        <el-tab-pane :label="t('admin.tabNotify')" name="notify">
          <p class="tab-desc">{{ t('admin.notifyDesc') }}</p>
          <div class="notify-layout">
            <el-form label-position="top" class="admin-form notify-form" @submit.prevent>
              <el-form-item :label="t('admin.notifyTitle')">
                <el-input
                  v-model="notifyForm.title"
                  maxlength="100"
                  show-word-limit
                  :placeholder="t('admin.notifyTitlePlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="t('admin.notifyContent')">
                <el-input
                  v-model="notifyForm.content"
                  type="textarea"
                  :rows="5"
                  maxlength="500"
                  show-word-limit
                  :placeholder="t('admin.notifyContentPlaceholder')"
                />
              </el-form-item>
              <el-form-item :label="t('admin.notifyTarget')">
                <el-radio-group v-model="notifyForm.targetType">
                  <el-radio-button value="ALL">{{ t('admin.notifyTargetAll') }}</el-radio-button>
                  <el-radio-button value="SPECIFIC">{{ t('admin.notifyTargetSpecific') }}</el-radio-button>
                </el-radio-group>
              </el-form-item>
              <el-form-item v-if="notifyForm.targetType === 'SPECIFIC'" :label="t('admin.notifyUsernames')">
                <el-input
                  v-model="notifyForm.usernames"
                  type="textarea"
                  :rows="2"
                  placeholder="user01,user02"
                />
                <p class="setting-hint">{{ t('admin.notifyUsernamesHint') }}</p>
              </el-form-item>
              <el-button type="primary" :loading="notifySending" @click="sendNotification">
                {{ t('admin.notifySend') }}
              </el-button>
            </el-form>

            <aside class="notify-preview" aria-label="preview">
              <p class="preview-label">{{ t('admin.notifyPreview') }}</p>
              <div class="preview-card">
                <div class="preview-title">{{ notifyForm.title.trim() || t('admin.notifyPreviewEmptyTitle') }}</div>
                <p class="preview-body">{{ notifyForm.content.trim() || t('admin.notifyPreviewEmptyContent') }}</p>
                <p class="preview-meta">
                  {{
                    notifyForm.targetType === 'ALL'
                      ? t('admin.notifyTargetAll')
                      : t('admin.notifyTargetSpecific')
                  }}
                </p>
              </div>
            </aside>
          </div>
        </el-tab-pane>

        <!-- 用户管理（仅管理员） -->
        <el-tab-pane v-if="userStore.isAdmin" :label="t('admin.tabUsers')" name="users">
          <div class="tab-toolbar">
            <el-input
              v-model="userKeyword"
              :placeholder="t('admin.userSearchPlaceholder')"
              clearable
              class="user-search"
              @keyup.enter="onUserSearch"
            />
            <el-button :loading="usersLoading" @click="onUserSearch">{{ t('admin.userSearch') }}</el-button>
          </div>

          <el-table v-loading="usersLoading" :data="userList" style="width: 100%">
            <el-table-column prop="userId" label="ID" width="72" />
            <el-table-column :label="t('admin.userAccount')" min-width="140">
              <template #default="{ row }">
                {{ row.nickname || row.username }}
                <span class="muted-inline">@{{ row.username }}</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('admin.userRole')" width="110">
              <template #default="{ row }">
                <el-tag size="small" :type="roleTagType(row.role)">{{ row.roleLabel }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('admin.userStatus')" width="100">
              <template #default="{ row }">
                <el-tag size="small" :type="row.status === 1 ? 'danger' : 'success'">
                  {{ row.status === 1 ? t('admin.userBanned') : t('admin.userActive') }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column :label="t('admin.userBanReason')" min-width="140" show-overflow-tooltip>
              <template #default="{ row }">
                <span v-if="row.status === 1">{{ row.banReason || '—' }}</span>
                <span v-else class="muted-inline">—</span>
              </template>
            </el-table-column>
            <el-table-column :label="t('admin.userBanUntil')" width="170">
              <template #default="{ row }">
                <span v-if="row.status === 1">{{ formatBanUntil(row.banUntil) }}</span>
                <span v-else class="muted-inline">—</span>
              </template>
            </el-table-column>
            <el-table-column prop="createTime" :label="t('admin.userJoined')" width="170" />
            <el-table-column :label="t('admin.userActions')" width="200" fixed="right">
              <template #default="{ row }">
                <el-button size="small" @click="onEditPermissions(row)">
                  {{ t('admin.userPermissions') }}
                </el-button>
                <el-button
                  v-if="row.status !== 1 && row.role !== 1"
                  size="small"
                  type="danger"
                  @click="onBanUser(row)"
                >
                  {{ t('admin.userBan') }}
                </el-button>
                <el-button
                  v-else-if="row.status === 1"
                  size="small"
                  type="success"
                  @click="onUnbanUser(row)"
                >
                  {{ t('admin.userUnban') }}
                </el-button>
              </template>
            </el-table-column>
          </el-table>

          <div v-if="userTotal > 0" class="oa-pagination">
            <el-pagination
              v-model:current-page="userPageNum"
              v-model:page-size="userPageSize"
              :total="userTotal"
              :page-sizes="[10, 20, 50]"
              layout="total, sizes, prev, pager, next"
              background
              @current-change="loadUsers"
              @size-change="onUserSizeChange"
            />
          </div>
        </el-tab-pane>

        <!-- 商家审核（仅管理员） -->
        <el-tab-pane v-if="userStore.isAdmin" :label="t('admin.tabMerchant')" name="merchant">
          <div class="tab-toolbar">
            <p class="tab-desc">{{ t('admin.merchantDesc') }}</p>
            <el-button size="small" :loading="merchantLoading" @click="loadMerchantApplications">
              {{ t('admin.merchantRefresh') }}
            </el-button>
          </div>

          <div v-loading="merchantLoading">
            <div v-if="!merchantLoading && merchantApps.length === 0" class="oa-empty-state">
              <p>{{ t('admin.merchantNoPending') }}</p>
            </div>
            <el-table v-else :data="merchantApps" style="width: 100%">
              <el-table-column prop="shopName" :label="t('admin.merchantShopName')" min-width="120" />
              <el-table-column :label="t('admin.merchantApplicant')" min-width="140">
                <template #default="{ row }">
                  {{ row.nickname || row.username }}
                  <span class="muted-inline">@{{ row.username }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="reason" :label="t('admin.merchantReason')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="contactPhone" :label="t('admin.merchantContact')" width="120" />
              <el-table-column prop="createTime" :label="t('admin.merchantAppliedAt')" width="170" />
              <el-table-column label="" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="success" @click="onApproveMerchant(row)">
                    {{ t('admin.merchantApprove') }}
                  </el-button>
                  <el-button size="small" type="danger" @click="onRejectMerchant(row)">
                    {{ t('admin.merchantReject') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>

        <!-- 特殊认证审核（仅管理员） -->
        <el-tab-pane v-if="userStore.isAdmin" :label="t('admin.tabSpecialCert')" name="specialCert">
          <div class="tab-toolbar">
            <p class="tab-desc">{{ t('admin.specialCertDesc') }}</p>
            <el-button size="small" :loading="specialCertLoading" @click="loadSpecialCertApplications">
              {{ t('admin.specialCertRefresh') }}
            </el-button>
          </div>

          <div v-loading="specialCertLoading">
            <div v-if="!specialCertLoading && specialCertApps.length === 0" class="oa-empty-state">
              <p>{{ t('admin.specialCertNoPending') }}</p>
            </div>
            <el-table v-else :data="specialCertApps" style="width: 100%">
              <el-table-column prop="displayName" :label="t('admin.specialCertDisplayName')" min-width="140" />
              <el-table-column :label="t('admin.specialCertApplicant')" min-width="140">
                <template #default="{ row }">
                  {{ row.nickname || row.username }}
                  <span class="muted-inline">@{{ row.username }}</span>
                </template>
              </el-table-column>
              <el-table-column prop="reason" :label="t('admin.specialCertReason')" min-width="180" show-overflow-tooltip />
              <el-table-column prop="contactPhone" :label="t('admin.specialCertContact')" width="120" />
              <el-table-column prop="createTime" :label="t('admin.specialCertAppliedAt')" width="170" />
              <el-table-column label="" width="180" fixed="right">
                <template #default="{ row }">
                  <el-button size="small" type="success" @click="onApproveSpecialCert(row)">
                    {{ t('admin.specialCertApprove') }}
                  </el-button>
                  <el-button size="small" type="danger" @click="onRejectSpecialCert(row)">
                    {{ t('admin.specialCertReject') }}
                  </el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </el-tab-pane>
      </el-tabs>
    </div>

    <el-dialog
      v-model="banDialogVisible"
      :title="t('admin.userBanDialogTitle')"
      width="480px"
      destroy-on-close
      @closed="resetBanForm"
    >
      <p v-if="banTarget" class="ban-target-hint">
        {{ banTarget.nickname || banTarget.username }}
        <span class="muted-inline">@{{ banTarget.username }}</span>
      </p>
      <el-form label-position="top" class="admin-form">
        <el-form-item :label="t('admin.userBanReason')" required>
          <el-input
            v-model="banForm.reason"
            type="textarea"
            :rows="3"
            maxlength="500"
            show-word-limit
            :placeholder="t('admin.userBanReasonPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('admin.userBanDuration')">
          <el-select v-model="banForm.durationDays" style="width: 100%">
            <el-option :label="t('admin.userBanDurationPermanent')" :value="0" />
            <el-option :label="t('admin.userBanDuration1')" :value="1" />
            <el-option :label="t('admin.userBanDuration7')" :value="7" />
            <el-option :label="t('admin.userBanDuration30')" :value="30" />
            <el-option :label="t('admin.userBanDuration90')" :value="90" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="banDialogVisible = false">{{ t('admin.cancel') }}</el-button>
        <el-button type="danger" :loading="banSubmitting" @click="submitBan">
          {{ t('admin.userBan') }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="permDialogVisible"
      :title="t('admin.userPermDialogTitle')"
      width="480px"
      destroy-on-close
      @closed="resetPermForm"
    >
      <p v-if="permTarget" class="ban-target-hint">
        {{ permTarget.nickname || permTarget.username }}
        <span class="muted-inline">@{{ permTarget.username }}</span>
      </p>
      <p class="tab-desc">{{ t('admin.userPermDesc') }}</p>
      <el-form label-position="top" class="admin-form perm-form">
        <el-form-item :label="t('admin.userPermPost')">
          <el-switch v-model="permForm.canPost" />
        </el-form-item>
        <el-form-item :label="t('admin.userPermComment')">
          <el-switch v-model="permForm.canComment" />
        </el-form-item>
        <el-form-item :label="t('admin.userPermOrder')">
          <el-switch v-model="permForm.canOrder" />
        </el-form-item>
        <el-form-item :label="t('admin.userPermBroadcast')">
          <el-switch v-model="permForm.canBroadcast" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="permDialogVisible = false">{{ t('admin.cancel') }}</el-button>
        <el-button type="primary" :loading="permSubmitting" @click="submitPermissions">
          {{ t('admin.userPermSave') }}
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useI18n } from '@/i18n'
import { useUserStore } from '@/store/user'
import {
  sendAdminNotification,
  listMerchantApplications,
  approveMerchantApplication,
  rejectMerchantApplication,
  listSpecialCertApplications,
  approveSpecialCertApplication,
  rejectSpecialCertApplication,
  listAdminUsers,
  banUser,
  unbanUser
} from '@/api/admin'

const { t } = useI18n()
const userStore = useUserStore()

const pageSubtitle = computed(() =>
  userStore.isAdmin ? t('admin.subtitle') : t('admin.subtitleOfficial')
)

const activeTab = ref('notify')
const notifySending = ref(false)
const notifyForm = reactive({
  title: '',
  content: '',
  targetType: 'ALL',
  usernames: ''
})

const usersLoading = ref(false)
const userList = ref([])
const userTotal = ref(0)
const userPageNum = ref(1)
const userPageSize = ref(20)
const userKeyword = ref('')

const merchantLoading = ref(false)
const merchantApps = ref([])

const specialCertLoading = ref(false)
const specialCertApps = ref([])

const banDialogVisible = ref(false)
const banSubmitting = ref(false)
const banTarget = ref(null)
const banForm = reactive({
  reason: '',
  durationDays: 7
})

onMounted(() => {
  if (userStore.isAdmin) {
    loadUsers()
    loadMerchantApplications()
    loadSpecialCertApplications()
  }
})

watch(activeTab, (tab) => {
  if (!userStore.isAdmin) return
  if (tab === 'users' && userList.value.length === 0) {
    loadUsers()
  }
  if (tab === 'merchant' && merchantApps.value.length === 0) {
    loadMerchantApplications()
  }
  if (tab === 'specialCert' && specialCertApps.value.length === 0) {
    loadSpecialCertApplications()
  }
})

function roleTagType(role) {
  if (role === 1) return 'danger'
  if (role === 2) return 'warning'
  if (role === 3) return 'success'
  return 'info'
}

async function sendNotification() {
  const title = notifyForm.title.trim()
  const content = notifyForm.content.trim()
  if (!title) {
    ElMessage.warning(t('admin.notifyTitleRequired'))
    return
  }
  if (!content) {
    ElMessage.warning(t('admin.notifyContentRequired'))
    return
  }
  if (notifyForm.targetType === 'SPECIFIC' && !notifyForm.usernames.trim()) {
    ElMessage.warning(t('admin.notifyUsernamesRequired'))
    return
  }

  try {
    await ElMessageBox.confirm(t('admin.notifySendConfirm'), t('admin.tip'), {
      confirmButtonText: t('admin.confirm'),
      cancelButtonText: t('admin.cancel'),
      type: 'warning'
    })
  } catch {
    return
  }

  notifySending.value = true
  try {
    const payload = { title, content, targetType: notifyForm.targetType }
    if (notifyForm.targetType === 'SPECIFIC') {
      payload.usernames = notifyForm.usernames
        .split(',')
        .map((name) => name.trim())
        .filter(Boolean)
    }
    const res = await sendAdminNotification(payload)
    const count = res.data?.recipientCount ?? 0
    ElMessage.success(t('admin.notifySendDone').replace('{count}', String(count)))
    notifyForm.title = ''
    notifyForm.content = ''
    notifyForm.usernames = ''
    notifyForm.targetType = 'ALL'
  } finally {
    notifySending.value = false
  }
}

async function loadUsers() {
  usersLoading.value = true
  try {
    const res = await listAdminUsers({
      pageNum: userPageNum.value,
      pageSize: userPageSize.value,
      keyword: userKeyword.value.trim() || undefined
    })
    const data = res.data || {}
    userList.value = data.list || []
    userTotal.value = data.total || 0
  } catch {
    userList.value = []
    userTotal.value = 0
  } finally {
    usersLoading.value = false
  }
}

function onUserSearch() {
  userPageNum.value = 1
  loadUsers()
}

function onUserSizeChange() {
  userPageNum.value = 1
  loadUsers()
}

function formatBanUntil(value) {
  if (!value) return t('admin.userBanPermanent')
  return String(value).replace('T', ' ')
}

function resetBanForm() {
  banTarget.value = null
  banForm.reason = ''
  banForm.durationDays = 7
}

async function onBanUser(row) {
  banTarget.value = row
  banForm.reason = ''
  banForm.durationDays = 7
  banDialogVisible.value = true
}

async function submitBan() {
  if (!banTarget.value) return
  const reason = banForm.reason.trim()
  if (!reason) {
    ElMessage.warning(t('admin.userBanReasonRequired'))
    return
  }
  banSubmitting.value = true
  try {
    await banUser(banTarget.value.userId, {
      reason,
      durationDays: banForm.durationDays
    })
    ElMessage.success(t('admin.userBanDone'))
    banDialogVisible.value = false
    await loadUsers()
  } catch (_) { /* request 已提示 */ }
  finally {
    banSubmitting.value = false
  }
}

async function onUnbanUser(row) {
  try {
    await ElMessageBox.confirm(
      t('admin.userUnbanConfirm').replace('{name}', row.username),
      t('admin.tip'),
      { confirmButtonText: t('admin.confirm'), cancelButtonText: t('admin.cancel'), type: 'info' }
    )
  } catch {
    return
  }
  try {
    await unbanUser(row.userId)
    ElMessage.success(t('admin.userUnbanDone'))
    await loadUsers()
  } catch (_) { /* request 已提示 */ }
}

async function loadMerchantApplications() {
  merchantLoading.value = true
  try {
    const res = await listMerchantApplications()
    merchantApps.value = res.data || []
  } catch {
    merchantApps.value = []
  } finally {
    merchantLoading.value = false
  }
}

async function onApproveMerchant(row) {
  try {
    await ElMessageBox.confirm(
      t('admin.merchantApproveConfirm').replace('{shop}', row.shopName).replace('{user}', row.username),
      t('admin.tip'),
      { confirmButtonText: t('admin.confirm'), cancelButtonText: t('admin.cancel'), type: 'info' }
    )
  } catch {
    return
  }
  try {
    await approveMerchantApplication(row.id)
    ElMessage.success(t('admin.merchantApproveDone'))
    await loadMerchantApplications()
  } catch (_) { /* request 已提示 */ }
}

async function onRejectMerchant(row) {
  let adminNote = ''
  try {
    const { value } = await ElMessageBox.prompt(
      t('admin.merchantRejectNote'),
      t('admin.merchantReject'),
      { confirmButtonText: t('admin.confirm'), cancelButtonText: t('admin.cancel'), inputType: 'textarea' }
    )
    adminNote = value || ''
  } catch {
    return
  }
  try {
    await rejectMerchantApplication(row.id, { adminNote })
    ElMessage.success(t('admin.merchantRejectDone'))
    await loadMerchantApplications()
  } catch (_) { /* request 已提示 */ }
}

async function loadSpecialCertApplications() {
  specialCertLoading.value = true
  try {
    const res = await listSpecialCertApplications()
    specialCertApps.value = res.data || []
  } catch {
    specialCertApps.value = []
  } finally {
    specialCertLoading.value = false
  }
}

async function onApproveSpecialCert(row) {
  try {
    await ElMessageBox.confirm(
      t('admin.specialCertApproveConfirm')
        .replace('{name}', row.displayName)
        .replace('{user}', row.username),
      t('admin.tip'),
      { confirmButtonText: t('admin.confirm'), cancelButtonText: t('admin.cancel'), type: 'info' }
    )
  } catch {
    return
  }
  try {
    await approveSpecialCertApplication(row.id)
    ElMessage.success(t('admin.specialCertApproveDone'))
    await loadSpecialCertApplications()
  } catch (_) { /* request 已提示 */ }
}

async function onRejectSpecialCert(row) {
  let adminNote = ''
  try {
    const { value } = await ElMessageBox.prompt(
      t('admin.specialCertRejectNote'),
      t('admin.specialCertReject'),
      { confirmButtonText: t('admin.confirm'), cancelButtonText: t('admin.cancel'), inputType: 'textarea' }
    )
    adminNote = value || ''
  } catch {
    return
  }
  try {
    await rejectSpecialCertApplication(row.id, { adminNote })
    ElMessage.success(t('admin.specialCertRejectDone'))
    await loadSpecialCertApplications()
  } catch (_) { /* request 已提示 */ }
}
</script>

<style scoped>
.page-subtitle {
  margin-top: 6px;
  font-size: 14px;
  color: var(--oa-text-secondary);
  font-weight: 400;
}

.admin-panel {
  padding-top: 8px;
}

.admin-tabs :deep(.el-tabs__header) {
  margin-bottom: 20px;
}

.tab-desc {
  margin: 0 0 16px;
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.45;
}

.tab-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.user-search {
  max-width: 280px;
}

.admin-form :deep(.el-form-item) {
  margin-bottom: 14px;
}

.notify-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(220px, 280px);
  gap: 24px;
  align-items: start;
}

@media (max-width: 800px) {
  .notify-layout {
    grid-template-columns: 1fr;
  }
}

.notify-preview {
  padding: 14px 16px;
  border: 1px solid var(--oa-border, #e5e7eb);
  border-radius: 10px;
  background: var(--oa-surface-muted, #f8fafc);
}

.preview-label {
  margin: 0 0 10px;
  font-size: 12px;
  color: var(--oa-text-muted);
  letter-spacing: 0.02em;
}

.preview-card {
  padding: 12px 14px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid var(--oa-border, #e5e7eb);
}

.preview-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--oa-text);
  margin-bottom: 8px;
  word-break: break-word;
}

.preview-body {
  margin: 0;
  font-size: 13px;
  line-height: 1.5;
  color: var(--oa-text-secondary);
  white-space: pre-wrap;
  word-break: break-word;
  min-height: 3.2em;
}

.preview-meta {
  margin: 12px 0 0;
  font-size: 12px;
  color: var(--oa-text-muted);
}

.setting-hint {
  margin-top: 6px;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.4;
}

.muted-inline {
  color: var(--oa-text-muted);
  font-size: 12px;
}

.oa-pagination {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.ban-target-hint {
  margin: 0 0 12px;
  font-size: 14px;
  color: var(--oa-text);
}
</style>
