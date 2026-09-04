<template>
  <div class="friends-page">
    <div class="page-heading">
      <div><span class="eyebrow">PEOPLE & MESSAGES</span><h2>好友与私信</h2><p>先建立好友关系，再开始安全对话。</p></div>
      <el-tag type="success" effect="light">{{ friends.length }} 位好友</el-tag>
    </div>
    <el-row :gutter="20" class="friend-grid">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-heading"><span>搜索用户</span><el-input v-model="keyword" placeholder="用户名 / 昵称" style="width: 190px" clearable @keyup.enter="search" />
            </div>
          </template>
          <el-button type="primary" @click="search" style="margin-bottom: 12px">搜索</el-button>
          <div v-if="searchResults.length">
            <div v-for="user in searchResults" :key="user.id" class="person-row">
              <div>
                <div>{{ user.nickname || user.username }}</div>
                <small>{{ user.username }}</small>
              </div>
              <el-button size="small" @click="handleAdd(user.id)">添加好友</el-button>
            </div>
          </div>
          <div v-else style="color:#999">暂无搜索结果</div>
        </el-card>
      </el-col>
      <el-col :span="24" class="request-column" v-if="requests.length">
        <el-card>
          <template #header>好友申请</template>
          <div v-for="item in requests" :key="item.id" class="person-row">
            <span>{{ item.nickname || item.username }} 请求添加你为好友</span>
            <el-button size="small" type="primary" @click="approve(item.id)">同意</el-button>
          </div>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>我的好友</template>
          <div v-if="friends.length">
            <div v-for="friend in friends" :key="friend.id" class="person-row">
              <div><strong>{{ friend.nickname || friend.username }}</strong><div class="muted">{{ friend.username }}</div></div>
              <el-button type="primary" plain size="small" @click="openChat(friend)">聊天</el-button>
            </div>
          </div>
          <div v-else style="color:#999">你还没有好友</div>
        </el-card>
      </el-col>
    </el-row>
    <el-dialog v-model="chatVisible" :title="`与 ${selectedFriend?.nickname || selectedFriend?.username || ''} 聊天`" width="620px" class="chat-dialog" destroy-on-close>
      <div class="chat-history" ref="chatHistory">
        <div v-if="!chatMessages.length" class="chat-empty">还没有消息，打个招呼吧。</div>
        <div v-for="message in chatMessages" :key="message.id" class="chat-message" :class="{ mine: message.senderId === currentUserId }">
          <div class="chat-bubble">{{ message.content }}</div>
          <small>{{ formatTime(message.createTime) }}</small>
        </div>
      </div>
      <div class="chat-composer">
        <el-input v-model="chatDraft" placeholder="输入消息，Enter 发送" @keyup.enter="sendMessage" :disabled="sendingMessage" />
        <el-button type="primary" @click="sendMessage" :loading="sendingMessage">发送</el-button>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { listFriends, searchUsers, addFriend, listFriendRequests, approveFriendRequest, listChatMessages, sendChatMessage } from '@/api/friend'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/stores/user'

const friends = ref([])
const requests = ref([])
const searchResults = ref([])
const keyword = ref('')
const userStore = useUserStore()
const selectedFriend = ref(null)
const chatVisible = ref(false)
const chatMessages = ref([])
const chatDraft = ref('')
const sendingMessage = ref(false)
const chatHistory = ref(null)
const currentUserId = Number(userStore.userId)

onMounted(async () => {
  try {
    await Promise.all([loadFriends(), loadRequests()])
  } catch (error) {
    console.error('[FRIEND PAGE ERROR]', error)
  }
})

async function loadFriends() {
  const res = await listFriends()
  friends.value = res.data || []
}

async function loadRequests() {
  const res = await listFriendRequests()
  requests.value = res.data || []
}

async function search() {
  if (!keyword.value.trim()) return
  const res = await searchUsers(keyword.value)
  searchResults.value = res.data || []
}

async function handleAdd(friendId) {
  try {
    await addFriend(friendId)
    ElMessage.success('好友申请已发送')
  } catch (error) {
    console.error('[ADD FRIEND ERROR]', error)
  }
}

async function approve(requestId) {
  try {
    await approveFriendRequest(requestId)
    ElMessage.success('已同意好友申请')
    await Promise.all([loadFriends(), loadRequests()])
  } catch (error) {
    console.error('[APPROVE FRIEND ERROR]', error)
  }
}

async function openChat(friend) {
  selectedFriend.value = friend
  chatVisible.value = true
  try {
    const res = await listChatMessages(friend.id)
    chatMessages.value = res.data || []
    scrollChat()
  } catch (error) {
    chatVisible.value = false
  }
}

async function sendMessage() {
  const text = chatDraft.value.trim()
  if (!text || !selectedFriend.value || sendingMessage.value) return
  sendingMessage.value = true
  try {
    const res = await sendChatMessage(selectedFriend.value.id, text)
    chatMessages.value.push(res.data)
    chatDraft.value = ''
    scrollChat()
  } catch (error) {
    console.error('[CHAT SEND ERROR]', error)
  } finally {
    sendingMessage.value = false
  }
}

function scrollChat() {
  nextTick(() => { if (chatHistory.value) chatHistory.value.scrollTop = chatHistory.value.scrollHeight })
}

function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour: '2-digit', minute: '2-digit' }) : ''
}
</script>

<style scoped>
.friends-page { min-height: 100%; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 22px; }
.eyebrow { color: #8aa29a; font-size: 10px; letter-spacing: 1.5px; }
.page-heading h2 { margin: 5px 0; color: #173b3d; font: 600 28px Georgia, serif; }
.page-heading p { margin: 0; color: #78918a; }
.friend-grid :deep(.el-card) { height: 100%; border: 1px solid #e1ebe7; box-shadow: 0 10px 25px rgba(35, 74, 70, .05); }
.card-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; color: #173b3d; font-weight: 700; }
.person-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 12px 0; border-bottom: 1px solid #edf2f0; }
.person-row:last-child { border-bottom: 0; }
.muted { margin-top: 3px; color: #91a39d; font-size: 12px; }
.request-column { margin-top: 20px; }
.chat-history { height: 360px; padding: 14px; overflow-y: auto; background: #f5f9f7; border-radius: 10px; }
.chat-empty { padding-top: 145px; color: #91a39d; text-align: center; }
.chat-message { display: flex; flex-direction: column; align-items: flex-start; margin-bottom: 13px; }
.chat-message.mine { align-items: flex-end; }
.chat-bubble { max-width: 78%; padding: 10px 13px; color: #294b4b; background: #fff; border-radius: 12px 12px 12px 3px; box-shadow: 0 3px 10px rgba(35, 74, 70, .06); white-space: pre-wrap; }
.chat-message.mine .chat-bubble { color: #fff; background: #236d69; border-radius: 12px 12px 3px 12px; }
.chat-message small { margin-top: 4px; color: #9badA7; font-size: 10px; }
.chat-composer { display: flex; gap: 10px; margin-top: 14px; }
</style>
