<template>
  <div class="profile-page" v-loading="infoLoading">
    <ProfileHeader
      :profile="info"
      :loading="infoLoading"
      is-owner
      show-details-toggle
      @edit-cover="openCoverEdit"
    >
      <template #details>
        <div class="detail-grid">
          <div><span class="detail-label">{{ t('profile.username') }}</span> {{ info?.username }}</div>
          <div><span class="detail-label">{{ t('profile.phone') }}</span> {{ info?.phone || t('profile.notSet') }}</div>
          <div>
            <span class="detail-label">{{ t('profile.accountType') }}</span>
            <el-tag size="small" :type="roleTagType">{{ userStore.roleLabel }}</el-tag>
          </div>
          <div><span class="detail-label">{{ t('profile.joined') }}</span> {{ info?.createTime }}</div>
          <div>
            <span class="detail-label">{{ t('common.points') }}</span>
            <span class="points-value">{{ userStore.points }} {{ t('common.pointsUnit') }}</span>
          </div>
        </div>
      </template>
      <template #actions>
        <el-button plain @click="openEdit">{{ t('profile.editProfile') }}</el-button>
      </template>
    </ProfileHeader>

    <nav class="profile-tabs">
      <button
        v-for="tab in tabs"
        :key="tab.key"
        type="button"
        class="tab-btn"
        :class="{ active: activeTab === tab.key }"
        @click="activeTab = tab.key"
      >
        {{ tab.label }}
      </button>
    </nav>

    <div class="profile-body">
      <section class="main-column">
        <div v-if="activeTab === 'posts'" class="tab-panel">
          <h2 class="section-heading">{{ t('profile.myPosts') }}</h2>
          <div v-loading="postsLoading">
            <el-empty v-if="!postsLoading && posts.length === 0" :description="t('profilePage.postsEmpty')" />
            <div v-else class="post-list">
              <article
                v-for="item in posts"
                :key="item.postId"
                class="post-item"
                @click="$router.push(`/topics/${item.postId}`)"
              >
                <h3 class="post-title">{{ item.title }}</h3>
                <p v-if="item.content" class="post-summary">{{ item.content }}</p>
                <div class="post-meta">
                  <span>{{ formatTime(item.createTime) }}</span>
                  <span>{{ t('topics.upvoteCount', { n: item.upvoteCount || 0 }) }}</span>
                </div>
              </article>
            </div>
            <div v-if="postsTotal > posts.length" class="load-more-wrap">
              <el-button :loading="postsLoading" @click="loadMorePosts">{{ t('profile.loadMore') }}</el-button>
            </div>
          </div>
        </div>

        <div v-else-if="activeTab === 'activity'" class="tab-panel">
          <p class="section-desc">{{ t('profile.activityHint') }}</p>
          <div class="quick-actions">
            <el-button @click="$router.push('/activity')">{{ t('profile.goActivity') }}</el-button>
            <el-button @click="$router.push('/favorites')">{{ t('profile.goFavorites') }}</el-button>
            <el-button
              v-if="userStore.isMerchant || userStore.isAdmin"
              type="primary"
              @click="$router.push('/merchant')"
            >
              {{ t('profile.goMerchant') }}
            </el-button>
          </div>
        </div>
      </section>

      <aside class="side-column">
        <div class="stat-block">
          <div class="stat-item">
            <span class="stat-num">{{ followStats.following }}</span>
            <span class="stat-label">{{ t('profilePage.following') }}</span>
          </div>
          <div class="stat-item">
            <span class="stat-num">{{ followStats.followers }}</span>
            <span class="stat-label">{{ t('profilePage.followers') }}</span>
          </div>
        </div>
        <div class="side-links">
          <button type="button" class="side-link" @click="$router.push('/activity')">
            {{ t('profile.goActivity') }}
          </button>
          <button type="button" class="side-link" @click="$router.push('/favorites')">
            {{ t('profile.goFavorites') }}
          </button>
          <button
            v-if="userStore.isMerchant || userStore.isAdmin"
            type="button"
            class="side-link"
            @click="$router.push('/merchant')"
          >
            {{ t('profile.goMerchant') }}
          </button>
        </div>
      </aside>
    </div>

    <!-- 商家入驻（无边框折叠区） -->
    <section v-if="userStore.isPersonal" class="merchant-section">
      <button type="button" class="section-toggle" @click="merchantOpen = !merchantOpen">
        <div>
          <h3 class="section-heading">{{ t('merchant.applyTitle') }}</h3>
          <p class="section-desc">{{ t('merchant.applyDesc') }}</p>
        </div>
        <el-icon class="chevron" :class="{ open: merchantOpen }"><ArrowDown /></el-icon>
      </button>
      <div v-show="merchantOpen" class="merchant-body">
        <div v-if="merchantApp && merchantApp.status === 0" class="apply-status">
          <el-alert :title="t('merchant.statusPending')" type="warning" :closable="false" show-icon>
            <template #default>
              <p>{{ merchantApp.shopName }} · {{ merchantApp.createTime }}</p>
            </template>
          </el-alert>
        </div>
        <div v-else-if="merchantApp && merchantApp.status === 1" class="apply-status">
          <el-alert :title="t('merchant.statusApproved')" type="success" :closable="false" show-icon>
            <template #default>
              <p>{{ merchantApp.shopName }} · {{ t('merchant.approvedRefresh') }}</p>
            </template>
          </el-alert>
        </div>
        <div v-else-if="merchantApp && merchantApp.status === 2" class="apply-status">
          <el-alert :title="t('merchant.statusRejected')" type="error" :closable="false" show-icon>
            <template #default>
              <p v-if="merchantApp.adminNote">{{ merchantApp.adminNote }}</p>
            </template>
          </el-alert>
        </div>
        <el-form
          v-if="!merchantApp || merchantApp.status === 2"
          ref="applyFormRef"
          :model="applyForm"
          :rules="applyRules"
          label-position="top"
          class="apply-form"
        >
          <el-form-item :label="t('merchant.shopName')" prop="shopName">
            <el-input v-model="applyForm.shopName" maxlength="100" show-word-limit />
          </el-form-item>
          <el-form-item :label="t('merchant.reason')" prop="reason">
            <el-input v-model="applyForm.reason" type="textarea" :rows="3" maxlength="500" show-word-limit />
          </el-form-item>
          <el-form-item :label="t('merchant.contactPhone')" prop="contactPhone">
            <el-input v-model="applyForm.contactPhone" />
          </el-form-item>
          <el-button type="primary" :loading="applying" @click="onApplyMerchant">
            {{ t('merchant.submitApply') }}
          </el-button>
        </el-form>
      </div>
    </section>

    <!-- 编辑资料 -->
    <el-dialog v-model="editVisible" :title="t('profile.editProfile')" width="480px">
      <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-position="top">
        <el-form-item :label="t('profile.nickname')" prop="nickname">
          <el-input v-model="editForm.nickname" :placeholder="t('profile.nicknamePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('profile.bio')" prop="bio">
          <el-input
            v-model="editForm.bio"
            type="textarea"
            :rows="2"
            maxlength="120"
            show-word-limit
            :placeholder="t('profile.bioPlaceholder')"
          />
        </el-form-item>
        <el-form-item :label="t('profile.ipLocation')" prop="ipLocation">
          <el-input v-model="editForm.ipLocation" maxlength="50" :placeholder="t('profile.ipPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('profile.phone')" prop="phone">
          <el-input v-model="editForm.phone" :placeholder="t('profile.phonePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('profile.avatar')" prop="avatar">
          <div class="avatar-edit">
            <div class="avatar-edit-preview">
              <img v-if="avatarPreviewSrc" :src="avatarPreviewSrc" alt="" class="avatar-edit-img" />
              <span v-else class="avatar-edit-letter">{{ avatarEditLetter }}</span>
            </div>
            <div class="avatar-edit-actions">
              <label class="cover-upload-btn" :class="{ disabled: avatarUploading }">
                <input
                  type="file"
                  accept="image/jpeg,image/png,image/webp"
                  :disabled="avatarUploading"
                  @change="onAvatarFileSelected"
                />
                {{ avatarUploading ? t('profile.avatarUploading') : t('profile.avatarUpload') }}
              </label>
              <el-button v-if="editForm.avatar" text type="danger" @click="clearAvatar">
                {{ t('profile.avatarClear') }}
              </el-button>
            </div>
            <p class="form-hint">{{ t('profile.avatarHint') }}</p>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">{{ t('settings.cancel') }}</el-button>
        <el-button type="primary" :loading="saving" @click="onSaveEdit">{{ t('settings.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑封面 -->
    <el-dialog v-model="coverVisible" :title="t('profile.editCover')" width="480px">
      <div class="cover-edit">
        <div class="cover-preview" :style="coverPreviewStyle">
          <span v-if="!coverDraft" class="cover-preview-empty">{{ t('profile.coverPreviewEmpty') }}</span>
        </div>
        <div class="cover-actions">
          <label class="cover-upload-btn" :class="{ disabled: coverUploading }">
            <input
              type="file"
              accept="image/jpeg,image/png,image/webp"
              :disabled="coverUploading"
              @change="onCoverFileSelected"
            />
            {{ coverUploading ? t('profile.coverUploading') : t('profile.coverUpload') }}
          </label>
          <el-button v-if="coverDraft" text type="danger" @click="coverDraft = ''">
            {{ t('profile.coverClear') }}
          </el-button>
        </div>
        <p class="form-hint">{{ t('profile.coverHint') }}</p>
      </div>
      <template #footer>
        <el-button @click="coverVisible = false">{{ t('settings.cancel') }}</el-button>
        <el-button type="primary" :loading="savingCover" :disabled="coverUploading" @click="onSaveCover">
          {{ t('settings.confirm') }}
        </el-button>
      </template>
    </el-dialog>

    <ImageCropDialog
      v-model="cropVisible"
      :image-src="cropImageSrc"
      :title="cropTitle"
      :aspect-ratio="cropAspect"
      :output-max-width="cropMaxWidth"
      :output-max-height="cropMaxHeight"
      :file-name="cropFileName"
      @confirm="onCropConfirmed"
    />
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted, watch, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
import ProfileHeader from '@/components/ProfileHeader.vue'
import ImageCropDialog from '@/components/ImageCropDialog.vue'
import { getUserInfo, updateUserInfo, applyMerchant, getMyMerchantApplication } from '@/api/user'
import { uploadProductImages } from '@/api/product'
import { getPublicProfile } from '@/api/profile'
import { listPostsByUser } from '@/api/topic'
import { useOnboarding } from '@/composables/useOnboarding'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'
import { isSafeCoverUrl, isSafeAvatarUrl, validateCoverImageFile } from '@/utils/validateImage'
import { prepareImageForUpload, compressErrorMessage, IMAGE_TARGET_MAX_BYTES } from '@/utils/imageCompress'
import { resolveAvatarSrc } from '@/utils/avatar'

const { t } = useI18n()
const route = useRoute()
const onboarding = useOnboarding()
const userStore = useUserStore()

const info = ref(null)
const infoLoading = ref(false)
const activeTab = ref('posts')
const followStats = ref({ following: 0, followers: 0 })

const tabs = computed(() => [
  { key: 'posts', label: t('profilePage.postsTab') },
  { key: 'activity', label: t('nav.activity') }
])

const posts = ref([])
const postsLoading = ref(false)
const postsPage = ref(1)
const postsTotal = ref(0)

const merchantOpen = ref(false)
const merchantApp = ref(null)
const applying = ref(false)
const applyFormRef = ref(null)
const applyForm = reactive({ shopName: '', reason: '', contactPhone: '' })
const applyRules = {
  shopName: [{ required: true, message: '请填写店铺名称', trigger: 'blur' }],
  reason: [{ required: true, message: '请填写申请说明', trigger: 'blur' }],
  contactPhone: [
    { required: true, message: '请填写联系电话', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ]
}

const roleTagType = computed(() => {
  if (userStore.isAdmin) return 'danger'
  if (userStore.isMerchant) return 'success'
  return 'info'
})

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function fetchInfo() {
  infoLoading.value = true
  try {
    const res = await getUserInfo()
    info.value = res.data
    userStore.setUserInfo(res.data)
    if (res.data?.userId) {
      await fetchFollowStats(res.data.userId)
      await fetchPosts(res.data.userId, true)
    }
  } catch {
    info.value = null
  } finally {
    infoLoading.value = false
  }
}

async function fetchFollowStats(userId) {
  try {
    const res = await getPublicProfile(userId)
    followStats.value = {
      following: res.data?.followingCount || 0,
      followers: res.data?.followersCount || 0
    }
  } catch {
    followStats.value = { following: 0, followers: 0 }
  }
}

async function fetchPosts(userId, reset = false) {
  if (!userId) return
  if (reset) {
    postsPage.value = 1
    posts.value = []
  }
  postsLoading.value = true
  try {
    const res = await listPostsByUser(userId, { pageNum: postsPage.value, pageSize: 10 })
    const list = res.data?.list || []
    postsTotal.value = res.data?.total || 0
    posts.value = reset ? list : [...posts.value, ...list]
  } catch {
    if (reset) posts.value = []
  } finally {
    postsLoading.value = false
  }
}

function loadMorePosts() {
  if (!info.value?.userId) return
  postsPage.value += 1
  fetchPosts(info.value.userId, false)
}

async function fetchMerchantApplication() {
  if (!userStore.isPersonal) return
  try {
    const res = await getMyMerchantApplication()
    merchantApp.value = res.data
    if (res.data && res.data.status === 1) {
      await fetchInfo()
    }
  } catch {
    merchantApp.value = null
  }
}

async function onApplyMerchant() {
  if (!applyFormRef.value) return
  try {
    await applyFormRef.value.validate()
  } catch {
    return
  }
  applying.value = true
  try {
    await applyMerchant({
      shopName: applyForm.shopName.trim(),
      reason: applyForm.reason.trim(),
      contactPhone: applyForm.contactPhone.trim()
    })
    ElMessage.success(t('merchant.applySuccess'))
    await fetchMerchantApplication()
  } finally {
    applying.value = false
  }
}

const editVisible = ref(false)
const coverVisible = ref(false)
const saving = ref(false)
const savingCover = ref(false)
const coverUploading = ref(false)
const avatarUploading = ref(false)
const editFormRef = ref(null)
const coverDraft = ref('')
const editForm = reactive({
  nickname: '',
  phone: '',
  avatar: '',
  bio: '',
  ipLocation: ''
})

const cropVisible = ref(false)
const cropImageSrc = ref('')
const cropTarget = ref('avatar') // 'avatar' | 'cover'
const cropAspect = ref(1)
const cropMaxWidth = ref(512)
const cropMaxHeight = ref(512)
const cropFileName = ref('avatar.jpg')
const avatarTouched = ref(false)
const cropTitle = computed(() =>
  cropTarget.value === 'cover' ? t('profile.cropCoverTitle') : t('profile.cropAvatarTitle')
)

const avatarPreviewSrc = computed(() =>
  resolveAvatarSrc(editForm.avatar, editForm.nickname || info.value?.nickname || info.value?.username)
)
const avatarEditLetter = computed(() => {
  const name = editForm.nickname || info.value?.nickname || info.value?.username || 'U'
  return String(name).charAt(0).toUpperCase() || 'U'
})

const coverPreviewStyle = computed(() => {
  const url = (coverDraft.value || '').trim()
  if (!url || !isSafeCoverUrl(url)) return {}
  return { backgroundImage: `url(${url})` }
})

const editRules = {
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  phone: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确', trigger: 'blur' }
  ],
  bio: [{ max: 120, message: '签名最多 120 字', trigger: 'blur' }],
  ipLocation: [{ max: 50, message: 'IP 属地最多 50 字', trigger: 'blur' }]
}

function revokeCropSrc() {
  if (cropImageSrc.value && cropImageSrc.value.startsWith('blob:')) {
    URL.revokeObjectURL(cropImageSrc.value)
  }
  cropImageSrc.value = ''
}

function openCropDialog(file, target) {
  revokeCropSrc()
  cropTarget.value = target
  if (target === 'cover') {
    cropAspect.value = 3
    cropMaxWidth.value = 1920
    cropMaxHeight.value = 640
    cropFileName.value = 'cover.jpg'
  } else {
    cropAspect.value = 1
    cropMaxWidth.value = 512
    cropMaxHeight.value = 512
    cropFileName.value = 'avatar.jpg'
  }
  cropImageSrc.value = URL.createObjectURL(file)
  cropVisible.value = true
}

function openEdit() {
  if (!info.value) return
  editForm.nickname = info.value.nickname || ''
  editForm.phone = info.value.phone || ''
  // 仅保留站内安全路径；legacy 外链不写入表单，避免再次提交
  editForm.avatar = isSafeAvatarUrl(info.value.avatar) ? info.value.avatar : ''
  editForm.bio = info.value.bio || ''
  editForm.ipLocation = info.value.ipLocation || ''
  avatarTouched.value = false
  editVisible.value = true
}

function openCoverEdit() {
  const cover = info.value?.coverImage || ''
  coverDraft.value = isSafeCoverUrl(cover) ? cover : ''
  coverVisible.value = true
}

function clearAvatar() {
  editForm.avatar = ''
  avatarTouched.value = true
}

async function uploadCroppedImage(file) {
  let prepared
  try {
    prepared = await prepareImageForUpload(file)
  } catch (err) {
    ElMessage.warning(compressErrorMessage(err?.message || 'type'))
    return null
  }

  const check = await validateCoverImageFile(prepared.file, { maxBytes: IMAGE_TARGET_MAX_BYTES })
  if (!check.ok) {
    const map = {
      type: 'profile.coverUploadType',
      size: 'profile.coverUploadSize',
      signature: 'profile.coverUploadSignature',
      decode: 'profile.coverUploadDecode',
      dimension: 'profile.coverUploadDimension'
    }
    ElMessage.warning(t(map[check.reason] || 'profile.coverUploadFail'))
    return null
  }

  const res = await uploadProductImages([prepared.file])
  const url = res.data?.images?.[0]
  if (!isSafeCoverUrl(url)) throw new Error('unsafe')
  return url
}

async function onAvatarFileSelected(event) {
  const raw = event.target.files?.[0]
  event.target.value = ''
  if (!raw) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(raw.type) && !/\.(jpe?g|png|webp)$/i.test(raw.name || '')) {
    ElMessage.warning(t('profile.coverUploadType'))
    return
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.warning(compressErrorMessage('size'))
    return
  }
  openCropDialog(raw, 'avatar')
}

async function onCoverFileSelected(event) {
  const raw = event.target.files?.[0]
  event.target.value = ''
  if (!raw) return
  if (!['image/jpeg', 'image/png', 'image/webp'].includes(raw.type) && !/\.(jpe?g|png|webp)$/i.test(raw.name || '')) {
    ElMessage.warning(t('profile.coverUploadType'))
    return
  }
  if (raw.size > 20 * 1024 * 1024) {
    ElMessage.warning(compressErrorMessage('size'))
    return
  }
  openCropDialog(raw, 'cover')
}

async function onCropConfirmed(file) {
  revokeCropSrc()
  if (!file) {
    ElMessage.error(t('profile.cropFail'))
    return
  }
  const isCover = cropTarget.value === 'cover'
  if (isCover) {
    coverUploading.value = true
  } else {
    avatarUploading.value = true
  }
  try {
    const url = await uploadCroppedImage(file)
    if (!url) return
    if (isCover) {
      coverDraft.value = url
      ElMessage.success(t('profile.coverUploadOk'))
    } else {
      editForm.avatar = url
      avatarTouched.value = true
      ElMessage.success(t('profile.avatarUploadOk'))
    }
  } catch {
    ElMessage.error(isCover ? t('profile.coverUploadFail') : t('profile.avatarUploadFail'))
  } finally {
    coverUploading.value = false
    avatarUploading.value = false
  }
}

async function saveProfile(payload) {
  await updateUserInfo(payload)
  ElMessage.success(t('profile.updated'))
  await fetchInfo()
  onboarding.trackStep('profile')
}

async function onSaveEdit() {
  if (!editFormRef.value) return
  try {
    await editFormRef.value.validate()
  } catch {
    return
  }
  const avatar = (editForm.avatar || '').trim()
  if (avatar && !isSafeAvatarUrl(avatar)) {
    ElMessage.warning(t('profile.avatarUnsafe'))
    return
  }
  saving.value = true
  try {
    const payload = {
      nickname: editForm.nickname,
      phone: editForm.phone,
      bio: editForm.bio,
      ipLocation: editForm.ipLocation
    }
    // 仅在用户上传/清除头像时提交；legacy 外链保留在库中但禁止再经表单写入
    if (avatarTouched.value || isSafeAvatarUrl(avatar)) {
      payload.avatar = avatar
    }
    await saveProfile(payload)
    editVisible.value = false
  } finally {
    saving.value = false
  }
}

async function onSaveCover() {
  const value = (coverDraft.value || '').trim()
  if (value && !isSafeCoverUrl(value)) {
    ElMessage.warning(t('profile.coverUnsafe'))
    return
  }
  savingCover.value = true
  try {
    await saveProfile({ coverImage: value })
    coverVisible.value = false
  } finally {
    savingCover.value = false
  }
}

function syncPanelFromQuery() {
  if (route.query.panel === 'merchant' && userStore.isPersonal) {
    merchantOpen.value = true
  }
}

watch(() => route.query.panel, syncPanelFromQuery)

watch(cropVisible, (visible) => {
  if (!visible) revokeCropSrc()
})

onMounted(async () => {
  await fetchInfo()
  await fetchMerchantApplication()
  syncPanelFromQuery()
  userStore.refreshPoints()
})

onBeforeUnmount(() => {
  revokeCropSrc()
})
</script>

<style scoped>
.profile-page {
  max-width: 1100px;
}

.detail-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 24px;
}

.detail-label {
  color: var(--oa-text-muted);
  margin-right: 6px;
}

.points-value {
  font-weight: 600;
  color: var(--oa-accent, #10a37f);
}

.profile-tabs {
  display: flex;
  gap: 28px;
  margin: 0 -40px;
  padding: 0 40px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.tab-btn {
  position: relative;
  padding: 14px 0;
  border: none;
  background: transparent;
  font-size: 15px;
  color: var(--oa-text-secondary);
  cursor: pointer;
}

.tab-btn.active {
  color: var(--oa-text);
  font-weight: 500;
}

.tab-btn.active::after {
  content: '';
  position: absolute;
  left: 0;
  right: 0;
  bottom: -1px;
  height: 2px;
  background: var(--oa-text);
  border-radius: 1px;
}

.profile-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 40px;
  padding-top: 24px;
}

.section-heading {
  margin: 0 0 16px;
  font-size: 16px;
  font-weight: 500;
  color: var(--oa-text);
}

.section-desc {
  margin: 0 0 16px;
  font-size: 14px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
}

.post-list {
  display: flex;
  flex-direction: column;
}

.post-item {
  padding: 18px 0;
  border-bottom: 1px solid var(--oa-border-subtle);
  cursor: pointer;
  transition: opacity 0.15s;
}

.post-item:hover {
  opacity: 0.85;
}

.post-title {
  margin: 0 0 6px;
  font-size: 16px;
  font-weight: 500;
  color: var(--oa-text);
}

.post-summary {
  margin: 0 0 8px;
  font-size: 14px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.post-meta {
  display: flex;
  gap: 16px;
  font-size: 13px;
  color: var(--oa-text-muted);
}

.load-more-wrap {
  padding-top: 16px;
  text-align: center;
}

.quick-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.stat-block {
  display: flex;
  gap: 32px;
  padding-bottom: 20px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.stat-item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.stat-num {
  font-size: 20px;
  font-weight: 600;
  color: var(--oa-text);
}

.stat-label {
  font-size: 13px;
  color: var(--oa-text-muted);
}

.side-links {
  display: flex;
  flex-direction: column;
  gap: 4px;
  padding-top: 16px;
}

.side-link {
  padding: 8px 0;
  border: none;
  background: transparent;
  text-align: left;
  font-size: 14px;
  color: var(--oa-text-secondary);
  cursor: pointer;
}

.side-link:hover {
  color: var(--oa-text);
}

.merchant-section {
  margin-top: 40px;
  padding-top: 24px;
  border-top: 1px solid var(--oa-border-subtle);
}

.section-toggle {
  width: 100%;
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  padding: 0;
  border: none;
  background: transparent;
  text-align: left;
  cursor: pointer;
}

.chevron {
  margin-top: 4px;
  transition: transform 0.2s ease;
  color: var(--oa-text-muted);
}

.chevron.open {
  transform: rotate(180deg);
}

.merchant-body {
  padding-top: 16px;
}

.apply-status {
  margin-bottom: 16px;
}

.apply-form {
  max-width: 480px;
}

.form-hint {
  margin: 0;
  font-size: 12px;
  color: var(--oa-text-muted);
  line-height: 1.5;
}

.cover-edit {
  display: flex;
  flex-direction: column;
  gap: 14px;
}

.cover-preview {
  height: 140px;
  border-radius: 10px;
  background: var(--oa-bg-elevated);
  background-size: cover;
  background-position: center;
  display: flex;
  align-items: center;
  justify-content: center;
}

.cover-preview-empty {
  font-size: 13px;
  color: var(--oa-text-muted);
}

.cover-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}

.cover-upload-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 8px 14px;
  border-radius: 8px;
  background: var(--oa-text);
  color: var(--oa-on-primary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
}

.cover-upload-btn input {
  display: none;
}

.cover-upload-btn.disabled {
  opacity: 0.55;
  cursor: not-allowed;
}

.avatar-edit {
  display: flex;
  flex-direction: column;
  gap: 12px;
  width: 100%;
}

.avatar-edit-preview {
  width: 96px;
  height: 96px;
  border-radius: 50%;
  overflow: hidden;
  background: var(--oa-bg-elevated);
  display: flex;
  align-items: center;
  justify-content: center;
}

.avatar-edit-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-edit-letter {
  font-size: 36px;
  font-weight: 600;
  color: var(--oa-text-secondary);
}

.avatar-edit-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}

@media (max-width: 900px) {
  .profile-body {
    grid-template-columns: 1fr;
  }

  .side-column {
    order: -1;
    padding-bottom: 8px;
    border-bottom: 1px solid var(--oa-border-subtle);
  }

  .stat-block {
    border-bottom: none;
    padding-bottom: 0;
  }

  .side-links {
    flex-direction: row;
    flex-wrap: wrap;
    gap: 12px;
    padding-top: 8px;
  }
}

@media (max-width: 768px) {
  .profile-tabs {
    margin: 0 -24px;
    padding: 0 24px;
    overflow-x: auto;
  }

  .detail-grid {
    grid-template-columns: 1fr;
  }
}
</style>
