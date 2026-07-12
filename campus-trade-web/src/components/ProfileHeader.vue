<template>
  <div class="profile-header" v-loading="loading">
    <div class="cover-wrap">
      <div
        class="cover-image"
        :style="coverStyle"
        role="img"
        :aria-label="t('profile.coverAlt')"
      />
      <div v-if="profile?.ipLocation" class="ip-badge">
        {{ t('profile.ipPrefix') }}{{ profile.ipLocation }}
      </div>
      <button
        v-if="isOwner"
        type="button"
        class="cover-edit-btn"
        @click="$emit('edit-cover')"
      >
        <el-icon><Camera /></el-icon>
        {{ t('profile.editCover') }}
      </button>
    </div>

    <div class="identity-row">
      <div class="avatar-wrap">
        <img
          v-if="avatarSrc"
          :src="avatarSrc"
          alt=""
          class="avatar-img"
          referrerpolicy="no-referrer"
          @error="avatarErrored = true"
        />
        <div v-else class="avatar-fallback">{{ avatarLetter }}</div>
      </div>

      <div class="identity-main">
        <div class="name-line">
          <h1 class="display-name">{{ displayName }}</h1>
          <span v-if="bioText" class="bio-inline">{{ bioText }}</span>
          <span v-else-if="isOwner" class="bio-inline muted">{{ t('profilePage.noBio') }}</span>
        </div>
        <button
          v-if="showDetailsToggle"
          type="button"
          class="details-toggle"
          @click="detailsOpen = !detailsOpen"
        >
          {{ t('profile.viewDetails') }}
          <el-icon :class="{ open: detailsOpen }"><ArrowDown /></el-icon>
        </button>
        <div v-show="detailsOpen && $slots.details" class="details-panel">
          <slot name="details" />
        </div>
      </div>

      <div class="identity-actions">
        <slot name="actions" />
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue'
import { Camera, ArrowDown } from '@element-plus/icons-vue'
import { useI18n } from '@/i18n'

const props = defineProps({
  profile: { type: Object, default: null },
  loading: { type: Boolean, default: false },
  isOwner: { type: Boolean, default: false },
  showDetailsToggle: { type: Boolean, default: false }
})

defineEmits(['edit-cover'])

const { t } = useI18n()
const detailsOpen = ref(false)
const avatarErrored = ref(false)

const displayName = computed(() => {
  if (!props.profile) return ''
  return props.profile.nickname || props.profile.username || '用户'
})

const bioText = computed(() => (props.profile?.bio || '').trim())

const avatarSrc = computed(() => {
  if (avatarErrored.value) return ''
  return props.profile?.avatar || ''
})

const avatarLetter = computed(() => displayName.value.charAt(0).toUpperCase() || 'U')

const defaultCover = 'linear-gradient(135deg, #6b8cce 0%, #8ea8e8 35%, #c4b5fd 70%, #f0abfc 100%)'

const coverStyle = computed(() => {
  const url = (props.profile?.coverImage || '').trim()
  if (url) {
    return { backgroundImage: `url(${url})` }
  }
  return { backgroundImage: defaultCover }
})
</script>

<style scoped>
.profile-header {
  margin: 0 -40px;
}

.cover-wrap {
  position: relative;
  height: 220px;
  overflow: hidden;
}

.cover-image {
  width: 100%;
  height: 100%;
  background-size: cover;
  background-position: center;
  background-repeat: no-repeat;
}

.ip-badge {
  position: absolute;
  right: 16px;
  bottom: 12px;
  padding: 4px 10px;
  border-radius: 4px;
  font-size: 12px;
  color: rgba(255, 255, 255, 0.92);
  background: rgba(0, 0, 0, 0.35);
  backdrop-filter: blur(4px);
}

.cover-edit-btn {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 6px 12px;
  border: none;
  border-radius: 6px;
  font-size: 13px;
  color: #fff;
  background: rgba(0, 0, 0, 0.45);
  cursor: pointer;
  transition: background 0.15s;
}

.cover-edit-btn:hover {
  background: rgba(0, 0, 0, 0.6);
}

.identity-row {
  display: flex;
  align-items: flex-start;
  gap: 24px;
  padding: 0 40px 20px;
  margin-top: -48px;
  position: relative;
}

.avatar-wrap {
  flex-shrink: 0;
  width: 152px;
  height: 152px;
  border-radius: 10px;
  overflow: hidden;
  border: 4px solid var(--oa-bg-sidebar, #fff);
  background: var(--oa-bg-elevated);
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.avatar-img,
.avatar-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.avatar-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48px;
  font-weight: 600;
  color: var(--oa-text-secondary);
}

.identity-main {
  flex: 1;
  min-width: 0;
  padding-top: 56px;
}

.name-line {
  display: flex;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 10px;
}

.display-name {
  margin: 0;
  font-size: 26px;
  font-weight: 600;
  line-height: 1.3;
  color: var(--oa-text);
  letter-spacing: -0.02em;
}

.bio-inline {
  font-size: 15px;
  color: var(--oa-text-secondary);
}

.bio-inline.muted {
  color: var(--oa-text-muted);
}

.details-toggle {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  margin-top: 8px;
  padding: 0;
  border: none;
  background: transparent;
  font-size: 13px;
  color: var(--oa-text-muted);
  cursor: pointer;
}

.details-toggle .el-icon {
  transition: transform 0.2s;
}

.details-toggle .el-icon.open {
  transform: rotate(180deg);
}

.details-panel {
  margin-top: 12px;
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.7;
}

.identity-actions {
  flex-shrink: 0;
  padding-top: 56px;
}

@media (max-width: 768px) {
  .profile-header {
    margin: 0 -24px;
  }

  .identity-row {
    flex-direction: column;
    padding: 0 24px 16px;
    margin-top: -36px;
    gap: 12px;
  }

  .avatar-wrap {
    width: 108px;
    height: 108px;
  }

  .identity-main,
  .identity-actions {
    padding-top: 0;
  }

  .identity-actions {
    width: 100%;
  }
}
</style>
