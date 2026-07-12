<template>
  <div class="profile-page" v-loading="loading">
    <el-empty v-if="!loading && !profile" :description="t('profilePage.notFound')" />

    <template v-if="profile">
      <ProfileHeader :profile="profile" :loading="loading" :is-owner="isSelf">
        <template #actions>
          <template v-if="isSelf">
            <el-button plain @click="$router.push('/my')">{{ t('profile.editProfile') }}</el-button>
          </template>
          <template v-else-if="userStore.isLogin">
            <el-button
              v-if="!profile.following"
              type="primary"
              :loading="followLoading"
              @click="onFollow"
            >
              {{ t('profilePage.follow') }}
            </el-button>
            <el-button
              v-else
              plain
              :loading="followLoading"
              @click="onUnfollow"
            >
              {{ t('profilePage.unfollow') }}
            </el-button>
            <el-button plain @click="onMessage">{{ t('profilePage.sendDm') }}</el-button>
          </template>
        </template>
      </ProfileHeader>

      <nav class="profile-tabs">
        <button type="button" class="tab-btn active">
          {{ t('profilePage.postsTab') }}
        </button>
      </nav>

      <div class="profile-body">
        <section class="main-column">
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
        </section>

        <aside class="side-column">
          <div class="stat-block">
            <div class="stat-item">
              <span class="stat-num">{{ profile.followingCount || 0 }}</span>
              <span class="stat-label">{{ t('profilePage.following') }}</span>
            </div>
            <div class="stat-item">
              <span class="stat-num">{{ profile.followersCount || 0 }}</span>
              <span class="stat-label">{{ t('profilePage.followers') }}</span>
            </div>
          </div>
        </aside>
      </div>
    </template>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import ProfileHeader from '@/components/ProfileHeader.vue'
import { getPublicProfile, followUser, unfollowUser } from '@/api/profile'
import { listPostsByUser } from '@/api/topic'
import { useUserStore } from '@/store/user'
import { useI18n } from '@/i18n'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()
const { t } = useI18n()

const userId = computed(() => Number(route.params.id))
const isSelf = computed(() => userStore.userInfo?.userId === userId.value)

const profile = ref(null)
const loading = ref(false)
const followLoading = ref(false)

const posts = ref([])
const postsLoading = ref(false)
const postsPage = ref(1)
const postsTotal = ref(0)

function formatTime(value) {
  if (!value) return ''
  return String(value).replace('T', ' ').slice(0, 16)
}

async function fetchProfile() {
  if (!userId.value) return
  loading.value = true
  try {
    const res = await getPublicProfile(userId.value)
    profile.value = res.data
  } catch {
    profile.value = null
  } finally {
    loading.value = false
  }
}

async function fetchPosts(reset = false) {
  if (!userId.value) return
  if (reset) {
    postsPage.value = 1
    posts.value = []
  }
  postsLoading.value = true
  try {
    const res = await listPostsByUser(userId.value, { pageNum: postsPage.value, pageSize: 10 })
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
  postsPage.value += 1
  fetchPosts(false)
}

async function onFollow() {
  followLoading.value = true
  try {
    await followUser(userId.value)
    profile.value = { ...profile.value, following: true, followersCount: (profile.value.followersCount || 0) + 1 }
    ElMessage.success(t('profilePage.followDone'))
  } finally {
    followLoading.value = false
  }
}

async function onUnfollow() {
  followLoading.value = true
  try {
    await unfollowUser(userId.value)
    profile.value = {
      ...profile.value,
      following: false,
      followersCount: Math.max(0, (profile.value.followersCount || 0) - 1)
    }
    ElMessage.success(t('profilePage.unfollowDone'))
  } finally {
    followLoading.value = false
  }
}

function onMessage() {
  router.push({ path: '/messages', query: { userId: userId.value } })
}

watch(userId, async () => {
  await fetchProfile()
  await fetchPosts(true)
})

onMounted(async () => {
  if (isSelf.value) {
    router.replace('/my')
    return
  }
  await fetchProfile()
  await fetchPosts(true)
})
</script>

<style scoped>
.profile-page {
  max-width: 1100px;
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
}

.profile-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 280px;
  gap: 40px;
  padding-top: 24px;
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
}

.stat-label {
  font-size: 13px;
  color: var(--oa-text-muted);
}

@media (max-width: 900px) {
  .profile-body {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 768px) {
  .profile-tabs {
    margin: 0 -24px;
    padding: 0 24px;
  }
}
</style>
