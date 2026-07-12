<template>
  <div class="page-container messages-page">
    <div class="page-header">
      <h1 class="page-title">{{ t('messages.title') }}</h1>
    </div>

    <div class="oa-panel messages-shell">
      <aside class="messages-side">
        <div class="side-tabs">
          <button
            type="button"
            class="side-tab"
            :class="{ active: mainTab === 'chat' }"
            @click="mainTab = 'chat'"
          >
            {{ t('messages.tabChat') }}
          </button>
          <button
            type="button"
            class="side-tab"
            :class="{ active: mainTab === 'friends' }"
            @click="switchToFriends"
          >
            {{ t('messages.tabFriends') }}
          </button>
        </div>

        <!-- 私信会话列表 -->
        <div v-show="mainTab === 'chat'" v-loading="convLoading" class="side-list">
          <div v-if="!convLoading && conversations.length === 0" class="oa-empty-state side-empty">
            <p>{{ t('messages.emptyConversations') }}</p>
          </div>
          <button
            v-for="item in conversations"
            :key="item.conversationId"
            type="button"
            class="conv-item"
            :class="{ active: selectedId === item.conversationId }"
            @click="selectConversation(item)"
          >
            <div class="conv-avatar">{{ avatarLetter(item.peerNickname) }}</div>
            <div class="conv-body">
              <div class="conv-top">
                <span class="conv-name">{{ item.peerNickname || `#${item.peerUserId}` }}</span>
                <span class="conv-time">{{ formatTime(item.lastMessageAt) }}</span>
              </div>
              <div class="conv-bottom">
                <span class="conv-preview">{{ item.lastMsgPreview || t('messages.noPreview') }}</span>
                <span v-if="item.unreadCount > 0" class="conv-unread">{{ item.unreadCount > 99 ? '99+' : item.unreadCount }}</span>
              </div>
            </div>
          </button>
        </div>

        <!-- 好友 / 申请 -->
        <div v-show="mainTab === 'friends'" v-loading="friendsLoading" class="side-list friends-pane">
          <section class="friends-section">
            <h3 class="friends-section-title">{{ t('friends.pending') }}</h3>
            <div v-if="!friendsLoading && requests.length === 0" class="friends-empty">
              {{ t('friends.noPending') }}
            </div>
            <div v-for="req in requests" :key="req.id" class="friend-row">
              <div class="conv-avatar sm">{{ avatarLetter(req.nickname) }}</div>
              <div class="friend-info">
                <span class="conv-name">{{ req.nickname || `#${req.fromUserId}` }}</span>
                <span class="oa-meta">{{ formatTime(req.createTime) }}</span>
              </div>
              <div class="friend-actions">
                <el-button size="small" type="primary" :loading="actingRequestId === req.id" @click="onAccept(req)">
                  {{ t('friends.accept') }}
                </el-button>
                <el-button size="small" :loading="actingRequestId === req.id" @click="onReject(req)">
                  {{ t('friends.reject') }}
                </el-button>
              </div>
            </div>
          </section>

          <section class="friends-section">
            <h3 class="friends-section-title">{{ t('friends.list') }}</h3>
            <div v-if="!friendsLoading && friends.length === 0" class="friends-empty">
              {{ t('friends.empty') }}
            </div>
            <button
              v-for="f in friends"
              :key="f.userId"
              type="button"
              class="friend-row clickable"
              @click="openChatWith(f.userId)"
            >
              <div class="conv-avatar sm">{{ avatarLetter(f.nickname) }}</div>
              <div class="friend-info">
                <span class="conv-name">{{ f.nickname || `#${f.userId}` }}</span>
                <span class="oa-meta">{{ t('friends.message') }}</span>
              </div>
            </button>
          </section>
        </div>
      </aside>

      <!-- 聊天区 -->
      <section class="chat-pane">
        <template v-if="activeConv">
          <header class="chat-header">
            <div class="chat-peer">
              <div class="conv-avatar">{{ avatarLetter(activeConv.peerNickname) }}</div>
              <div>
                <div class="chat-peer-name">{{ activeConv.peerNickname || `#${activeConv.peerUserId}` }}</div>
              </div>
            </div>
            <div class="chat-header-actions">
              <el-button
                v-if="showAddFriend"
                size="small"
                :loading="friendActing"
                @click="onAddFriend"
              >
                {{ t('friends.add') }}
              </el-button>
              <el-button
                v-else-if="friendStatus?.pendingOutgoing"
                size="small"
                disabled
              >
                {{ t('friends.pendingOutgoing') }}
              </el-button>
              <span v-else-if="friendStatus?.friends || activeConv.friends" class="friend-badge">{{ t('friends.alreadyFriends') }}</span>
            </div>
          </header>

          <div
            v-if="activeConv.canMessageUnlimited === false"
            class="limit-hint"
          >
            {{ t('messages.limitHint') }}
          </div>

          <div ref="msgListRef" v-loading="msgLoading" class="chat-messages">
            <div v-if="!msgLoading && messages.length === 0" class="oa-empty-state side-empty">
              <p>{{ t('messages.emptyMessages') }}</p>
            </div>
            <div
              v-for="msg in messages"
              :key="msg.messageId"
              class="bubble-row"
              :class="{ mine: msg.mine }"
            >
              <div class="bubble">
                <p class="bubble-text">{{ msg.content }}</p>
                <span class="bubble-time">{{ formatTime(msg.createTime) }}</span>
              </div>
            </div>
          </div>

          <footer class="chat-composer">
            <el-input
              v-model="draft"
              type="textarea"
              :rows="2"
              maxlength="2000"
              :placeholder="t('messages.placeholder')"
              resize="none"
              @keydown.enter.exact.prevent="onSend"
            />
            <el-button type="primary" :loading="sending" :disabled="!draft.trim()" @click="onSend">
              {{ t('messages.send') }}
            </el-button>
          </footer>
        </template>

        <div v-else class="chat-placeholder">
          <p>{{ t('messages.selectHint') }}</p>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useI18n } from '@/i18n'
import {
  acceptFriendRequest,
  getFriendStatus,
  listConversations,
  listFriendRequests,
  listFriends,
  listMessages,
  openConversation,
  readConversation,
  rejectFriendRequest,
  requestFriend,
  sendMessage
} from '@/api/social'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()

const messageUnreadRefreshEvent = 'campus:message-unread-refresh'

const mainTab = ref('chat')
const convLoading = ref(false)
const friendsLoading = ref(false)
const msgLoading = ref(false)
const sending = ref(false)
const friendActing = ref(false)
const actingRequestId = ref(null)

const conversations = ref([])
const friends = ref([])
const requests = ref([])
const messages = ref([])
const selectedId = ref(null)
const friendStatus = ref(null)
const draft = ref('')
const msgListRef = ref(null)

const activeConv = computed(() =>
  conversations.value.find((c) => c.conversationId === selectedId.value) || null
)

const showAddFriend = computed(() => {
  const s = friendStatus.value
  if (!s || !activeConv.value) return false
  if (s.friends || s.pendingOutgoing) return false
  return true
})

function refreshMessageUnreadBadge() {
  window.dispatchEvent(new Event(messageUnreadRefreshEvent))
}

function avatarLetter(name) {
  const n = (name || '?').trim()
  return n.charAt(0).toUpperCase()
}

function formatTime(t) {
  if (!t) return ''
  const d = new Date(t)
  if (Number.isNaN(d.getTime())) return String(t).replace('T', ' ').slice(0, 16)
  const pad = (n) => String(n).padStart(2, '0')
  const now = new Date()
  const sameDay =
    d.getFullYear() === now.getFullYear() &&
    d.getMonth() === now.getMonth() &&
    d.getDate() === now.getDate()
  if (sameDay) return `${pad(d.getHours())}:${pad(d.getMinutes())}`
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

async function fetchConversations() {
  convLoading.value = true
  try {
    const res = await listConversations()
    conversations.value = Array.isArray(res.data) ? res.data : []
  } catch {
    conversations.value = []
  } finally {
    convLoading.value = false
  }
}

async function fetchFriendsData() {
  friendsLoading.value = true
  try {
    const [fr, rq] = await Promise.all([listFriends(), listFriendRequests()])
    friends.value = Array.isArray(fr.data) ? fr.data : []
    requests.value = Array.isArray(rq.data) ? rq.data : []
  } catch {
    friends.value = []
    requests.value = []
  } finally {
    friendsLoading.value = false
  }
}

function switchToFriends() {
  mainTab.value = 'friends'
  fetchFriendsData()
}

async function loadMessages(conversationId) {
  if (!conversationId) return
  msgLoading.value = true
  try {
    const res = await listMessages(conversationId, { pageNum: 1, pageSize: 100 })
    const page = res.data || {}
    messages.value = Array.isArray(page.list) ? page.list : []
    await nextTick()
    scrollToBottom()
  } catch {
    messages.value = []
  } finally {
    msgLoading.value = false
  }
}

function scrollToBottom() {
  const el = msgListRef.value
  if (el) el.scrollTop = el.scrollHeight
}

async function loadFriendStatus(peerUserId) {
  if (!peerUserId) {
    friendStatus.value = null
    return
  }
  try {
    const res = await getFriendStatus(peerUserId)
    friendStatus.value = res.data || null
  } catch {
    friendStatus.value = null
  }
}

async function selectConversation(item, { skipRead } = {}) {
  if (!item) return
  mainTab.value = 'chat'
  selectedId.value = item.conversationId
  draft.value = ''
  await Promise.all([
    loadMessages(item.conversationId),
    loadFriendStatus(item.peerUserId)
  ])
  if (!skipRead && item.unreadCount > 0) {
    try {
      await readConversation(item.conversationId)
      item.unreadCount = 0
      refreshMessageUnreadBadge()
    } catch {
      // interceptor
    }
  }
  // 同步 query，便于刷新/分享
  if (String(route.query.peerUserId || '') !== String(item.peerUserId)) {
    router.replace({ path: '/messages', query: { peerUserId: String(item.peerUserId) } })
  }
}

async function openChatWith(peerUserId) {
  if (!peerUserId) return
  try {
    const res = await openConversation({ peerUserId: Number(peerUserId) })
    const conv = res.data
    if (!conv) return
    const idx = conversations.value.findIndex((c) => c.conversationId === conv.conversationId)
    if (idx >= 0) {
      conversations.value[idx] = { ...conversations.value[idx], ...conv }
    } else {
      conversations.value = [conv, ...conversations.value]
    }
    await selectConversation(conversations.value.find((c) => c.conversationId === conv.conversationId) || conv)
  } catch {
    // interceptor
  }
}

async function handlePeerQuery() {
  const peer = route.query.peerUserId
  if (!peer) return
  await openChatWith(peer)
}

async function onSend() {
  const content = draft.value.trim()
  if (!content || !activeConv.value || sending.value) return
  sending.value = true
  try {
    const res = await sendMessage(activeConv.value.conversationId, { content })
    const msg = res.data
    if (msg) {
      messages.value = [...messages.value, msg]
      activeConv.value.lastMsgPreview = content
      activeConv.value.lastMessageAt = msg.createTime
      // 移到列表顶部
      const id = activeConv.value.conversationId
      conversations.value = [
        activeConv.value,
        ...conversations.value.filter((c) => c.conversationId !== id)
      ]
    }
    draft.value = ''
    await nextTick()
    scrollToBottom()
    // 重新拉取会话以刷新 canMessageUnlimited
    await fetchConversations()
    const updated = conversations.value.find((c) => c.conversationId === selectedId.value)
    if (updated) {
      await loadFriendStatus(updated.peerUserId)
    }
  } catch {
    // interceptor
  } finally {
    sending.value = false
  }
}

async function onAddFriend() {
  if (!activeConv.value || friendActing.value) return
  friendActing.value = true
  try {
    await requestFriend({ peerUserId: activeConv.value.peerUserId })
    ElMessage.success(t('friends.requestSent'))
    await loadFriendStatus(activeConv.value.peerUserId)
  } catch {
    // interceptor
  } finally {
    friendActing.value = false
  }
}

async function onAccept(req) {
  actingRequestId.value = req.id
  try {
    await acceptFriendRequest(req.id)
    ElMessage.success(t('friends.acceptDone'))
    await fetchFriendsData()
    if (activeConv.value?.peerUserId === req.fromUserId) {
      await loadFriendStatus(req.fromUserId)
      await fetchConversations()
    }
  } catch {
    // interceptor
  } finally {
    actingRequestId.value = null
  }
}

async function onReject(req) {
  actingRequestId.value = req.id
  try {
    await rejectFriendRequest(req.id)
    ElMessage.success(t('friends.rejectDone'))
    await fetchFriendsData()
  } catch {
    // interceptor
  } finally {
    actingRequestId.value = null
  }
}

watch(
  () => route.query.peerUserId,
  (peer, prev) => {
    if (!peer || String(peer) === String(prev || '')) return
    if (activeConv.value && String(activeConv.value.peerUserId) === String(peer)) return
    openChatWith(peer)
  }
)

onMounted(async () => {
  await fetchConversations()
  if (route.query.peerUserId) {
    await handlePeerQuery()
  } else if (conversations.value.length > 0) {
    // 默认不自动选中，展示占位提示
  }
})

onUnmounted(() => {
  refreshMessageUnreadBadge()
})
</script>

<style scoped>
.messages-shell {
  display: grid;
  grid-template-columns: 320px minmax(0, 1fr);
  min-height: 620px;
  padding: 0;
  overflow: hidden;
}

.messages-side {
  display: flex;
  flex-direction: column;
  border-right: 1px solid var(--oa-border-subtle);
  background: var(--oa-bg-sidebar);
  min-height: 0;
}

.side-tabs {
  display: flex;
  gap: 4px;
  padding: 12px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.side-tab {
  flex: 1;
  border: 0;
  background: transparent;
  color: var(--oa-text-secondary);
  font-size: 13px;
  font-weight: 500;
  padding: 8px 10px;
  border-radius: var(--oa-radius-sm);
  cursor: pointer;
  transition: background 0.15s, color 0.15s;
}

.side-tab:hover {
  background: var(--oa-bg-hover);
  color: var(--oa-text);
}

.side-tab.active {
  background: var(--oa-bg-elevated);
  color: var(--oa-text);
}

.side-list {
  flex: 1;
  overflow-y: auto;
  min-height: 0;
}

.side-empty {
  padding: 32px 16px;
}

.conv-item {
  display: flex;
  gap: 12px;
  width: 100%;
  border: 0;
  background: transparent;
  text-align: left;
  padding: 12px 14px;
  cursor: pointer;
  color: inherit;
  border-bottom: 1px solid var(--oa-border-subtle);
  transition: background 0.15s;
}

.conv-item:hover,
.conv-item.active {
  background: var(--oa-bg-hover);
}

.conv-avatar {
  width: 40px;
  height: 40px;
  border-radius: 50%;
  background: #555;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 15px;
  font-weight: 600;
  flex-shrink: 0;
}

.conv-avatar.sm {
  width: 32px;
  height: 32px;
  font-size: 13px;
}

.conv-body {
  flex: 1;
  min-width: 0;
}

.conv-top,
.conv-bottom {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
}

.conv-name {
  font-size: 14px;
  font-weight: 500;
  color: var(--oa-text);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.conv-time {
  font-size: 11px;
  color: var(--oa-text-muted);
  flex-shrink: 0;
}

.conv-preview {
  font-size: 12px;
  color: var(--oa-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 4px;
}

.conv-unread {
  flex-shrink: 0;
  min-width: 18px;
  height: 18px;
  padding: 0 5px;
  border-radius: 9px;
  background: #ef4444;
  color: #fff;
  font-size: 11px;
  line-height: 18px;
  text-align: center;
  margin-top: 4px;
}

.friends-pane {
  padding: 8px 0 16px;
}

.friends-section {
  padding: 8px 12px 4px;
}

.friends-section-title {
  margin: 0 0 8px;
  padding: 0 4px;
  font-size: 12px;
  font-weight: 600;
  color: var(--oa-text-muted);
  text-transform: uppercase;
  letter-spacing: 0.04em;
}

.friends-empty {
  font-size: 13px;
  color: var(--oa-text-muted);
  padding: 8px 4px 16px;
}

.friend-row {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-radius: var(--oa-radius-sm);
  margin-bottom: 4px;
}

.friend-row.clickable {
  width: 100%;
  border: 0;
  background: transparent;
  cursor: pointer;
  text-align: left;
  color: inherit;
}

.friend-row.clickable:hover {
  background: var(--oa-bg-hover);
}

.friend-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.friend-actions {
  display: flex;
  gap: 6px;
  flex-shrink: 0;
}

.chat-pane {
  display: flex;
  flex-direction: column;
  min-height: 0;
  background: var(--oa-bg);
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 18px;
  border-bottom: 1px solid var(--oa-border-subtle);
}

.chat-peer {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}

.chat-peer-name {
  font-size: 15px;
  font-weight: 560;
  color: var(--oa-text);
}

.friend-badge {
  font-size: 12px;
  color: var(--oa-text-muted);
}

.limit-hint {
  margin: 12px 18px 0;
  padding: 10px 12px;
  border-radius: var(--oa-radius-sm);
  border: none;
  background: var(--oa-bg-elevated);
  font-size: 13px;
  color: var(--oa-text-secondary);
  line-height: 1.5;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 18px;
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-height: 280px;
}

.bubble-row {
  display: flex;
  justify-content: flex-start;
}

.bubble-row.mine {
  justify-content: flex-end;
}

.bubble {
  max-width: min(72%, 480px);
  padding: 10px 12px;
  border-radius: 12px;
  background: var(--oa-bg-elevated);
  border: none;
}

.bubble-row.mine .bubble {
  background: var(--oa-bg-hover);
  border-color: var(--oa-border);
}

.bubble-text {
  margin: 0;
  font-size: 14px;
  line-height: 1.55;
  color: var(--oa-text);
  white-space: pre-wrap;
  word-break: break-word;
}

.bubble-time {
  display: block;
  margin-top: 6px;
  font-size: 11px;
  color: var(--oa-text-muted);
  text-align: right;
}

.chat-composer {
  display: flex;
  gap: 12px;
  align-items: flex-end;
  padding: 14px 18px;
  border-top: 1px solid var(--oa-border-subtle);
}

.chat-composer .el-textarea {
  flex: 1;
}

.chat-placeholder {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--oa-text-muted);
  font-size: 14px;
  padding: 40px;
}

@media (max-width: 900px) {
  .messages-shell {
    grid-template-columns: 1fr;
    min-height: auto;
  }

  .messages-side {
    border-right: 0;
    border-bottom: 1px solid var(--oa-border-subtle);
    max-height: 360px;
  }

  .chat-messages {
    min-height: 320px;
  }
}
</style>
