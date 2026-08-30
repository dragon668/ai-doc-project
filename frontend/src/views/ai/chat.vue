<template>
  <div style="display: flex; height: calc(100vh - 120px)">
    <!-- 左侧对话列表 -->
    <div style="width: 260px; border-right: 1px solid #eee; padding: 16px; overflow-y: auto">
      <el-button type="primary" style="width: 100%; margin-bottom: 16px" @click="createNewConv">
        <el-icon><Plus /></el-icon> 新建对话
      </el-button>
      <div v-for="conv in conversations" :key="conv.id"
           style="padding: 12px; cursor: pointer; border-radius: 8px; margin-bottom: 8px"
           :style="{ background: currentConvId === conv.id ? '#ecf5ff' : 'transparent' }"
           @click="selectConversation(conv)">
        <div style="font-weight: 500">{{ conv.title }}</div>
        <div style="font-size: 12px; color: #999">{{ conv.updateTime }}</div>
      </div>
    </div>

    <!-- 右侧聊天区域 -->
    <div style="flex: 1; display: flex; flex-direction: column">
      <div style="flex: 1; overflow-y: auto; padding: 20px" ref="chatContainer">
        <div v-for="(msg, idx) in messages" :key="idx"
             :style="{ textAlign: msg.role === 'user' ? 'right' : 'left', marginBottom: '16px' }">
          <el-tag :type="msg.role === 'user' ? 'primary' : 'success'" style="margin-bottom: 4px">
            {{ msg.role === 'user' ? '我' : 'AI' }}
          </el-tag>
          <div style="display: inline-block; padding: 12px 16px; border-radius: 12px; max-width: 70%; text-align: left"
               :style="{ background: msg.role === 'user' ? '#409eff' : '#f4f4f5', color: msg.role === 'user' ? '#fff' : '#333' }">
            {{ msg.content }}
          </div>
        </div>
        <!-- 流式输出中的AI回复 -->
        <div v-if="streamingContent" style="text-align: left; margin-bottom: 16px">
          <el-tag type="success" style="margin-bottom: 4px">AI</el-tag>
          <div style="display: inline-block; padding: 12px 16px; border-radius: 12px; max-width: 70%; background: #f4f4f5">
            {{ streamingContent }}
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div style="padding: 16px; border-top: 1px solid #eee; display: flex; gap: 12px">
        <el-input v-model="question" placeholder="输入你的问题..." @keyup.enter="sendQuestion" :disabled="!currentConvId" size="large" />
        <el-button type="primary" size="large" @click="sendQuestion" :loading="sending" :disabled="!currentConvId">发送</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { createConversation, listConversations, getMessages, getChatSseUrl } from '@/api/ai'

const route = useRoute()
const workspaceId = route.params.workspaceId
const conversations = ref([])
const currentConvId = ref(null)
const messages = ref([])
const question = ref('')
const sending = ref(false)
const streamingContent = ref('')
const chatContainer = ref(null)

onMounted(async () => {
  await loadConversations()
})

async function loadConversations() {
  const res = await listConversations(workspaceId)
  conversations.value = res.data
}

async function createNewConv() {
  const res = await createConversation({ workspaceId, title: '新对话' })
  conversations.value.unshift(res.data)
  currentConvId.value = res.data.id
  messages.value = []
}

function selectConversation(conv) {
  currentConvId.value = conv.id
  loadMessages()
}

async function loadMessages() {
  const res = await getMessages(currentConvId.value)
  messages.value = res.data
}

async function sendQuestion() {
  if (!question.value.trim() || !currentConvId.value) return

  const q = question.value
  question.value = ''
  sending.value = true
  streamingContent.value = ''

  // 添加用户消息到列表
  messages.value.push({ role: 'user', content: q })

  // 使用fetch发送SSE请求
  const token = localStorage.getItem('token')
  try {
    const response = await fetch(getChatSseUrl(), {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${token}`
      },
      body: JSON.stringify({
        conversationId: currentConvId.value,
        question: q
      })
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const text = decoder.decode(value)
      const lines = text.split('\n')
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.substring(6)
          if (data === '[DONE]') break
          streamingContent.value += data
          scrollToBottom()
        }
      }
    }

    // 完成后添加到消息列表
    messages.value.push({ role: 'assistant', content: streamingContent.value })
    streamingContent.value = ''
  } catch (e) {
    messages.value.push({ role: 'assistant', content: 'AI服务暂时不可用，请稍后重试' })
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}
</script>
