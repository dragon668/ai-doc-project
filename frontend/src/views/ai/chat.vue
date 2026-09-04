<template>
  <div class="chat-page">
    <!-- 左侧对话列表 -->
    <aside class="conversation-rail">
      <div class="rail-heading"><span>AI WORKSPACE</span><strong>对话记录</strong></div>
      <el-button type="primary" class="new-conversation" @click="createNewConv">
        <el-icon><Plus /></el-icon> 新建对话
      </el-button>
      <div v-for="conv in conversations" :key="conv.id" class="conversation-item"
           :style="{ background: currentConvId === conv.id ? '#ecf5ff' : 'transparent' }"
           @click="selectConversation(conv)">
        <div style="font-weight: 500">{{ conv.title }}</div>
        <div style="font-size: 12px; color: #999">{{ conv.updateTime }}</div>
      </div>
    </aside>

    <!-- 右侧聊天区域 -->
    <section class="chat-panel">
      <div class="chat-scroll" ref="chatContainer">
        <div v-if="!currentConvId" class="empty-chat">
          <div class="empty-orbit">✦</div>
          <h2>把问题交给你的知识库</h2>
          <p>选择或新建一个对话，开始检索工作区内的文档。</p>
        </div>
        <transition-group name="message-rise" tag="div" class="message-list">
        <div v-for="(msg, idx) in messages" :key="`${msg.role}-${idx}`" class="message-row"
             :class="msg.role === 'user' ? 'is-user' : 'is-assistant'">
          <el-tag :type="msg.role === 'user' ? 'primary' : 'success'" style="margin-bottom: 4px">
            {{ msg.role === 'user' ? '我' : 'AI' }}
          </el-tag>
          <div class="message-bubble"
               :class="msg.role === 'user' ? 'user-bubble' : 'assistant-bubble'">
            {{ msg.content }}
          </div>
        </div>
        </transition-group>
        <!-- 流式输出中的AI回复 -->
        <div v-if="streamingContent" class="message-row is-assistant streaming-message">
          <el-tag type="success" style="margin-bottom: 4px">AI</el-tag>
          <div class="message-bubble assistant-bubble">
            {{ streamingContent }}
          </div>
        </div>
      </div>

      <!-- 输入区域 -->
      <div class="composer">
        <el-input v-model="question" placeholder="输入你的问题..." @keyup.enter="sendQuestion" :disabled="!currentConvId" size="large" />
        <el-button type="primary" size="large" @click="sendQuestion" :loading="sending" :disabled="!currentConvId">发送</el-button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { useRoute } from 'vue-router'
import { createConversation, listConversations, getMessages, getChatSseUrl } from '@/api/ai'
import { updateEditableContent } from '@/api/document'

const route = useRoute()
const workspaceId = route.params.workspaceId
const conversations = ref([])
const currentConvId = ref(null)
const messages = ref([])
const question = ref('')
const sending = ref(false)
const streamingContent = ref('')
const chatContainer = ref(null)
const routeDocumentId = route.query.documentId
const routePrompt = route.query.prompt

onMounted(async () => {
  await loadConversations()
  if (routePrompt && !question.value) question.value = routePrompt
})

async function loadConversations() {
  const res = await listConversations(workspaceId)
  conversations.value = res.data || []
  if (conversations.value.length) selectConversation(conversations.value[0])
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
  messages.value = res.data || []
  scrollToBottom()
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

    if (!response.ok || !response.body) throw new Error(`AI request failed: ${response.status}`)

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''
      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.substring(6)
          if (data === '[DONE]') continue
          streamingContent.value += data
          scrollToBottom()
        }
      }
    }

    // 完成后添加到消息列表
    messages.value.push({ role: 'assistant', content: streamingContent.value })
    if (routeDocumentId && streamingContent.value.trim()) {
      await updateEditableContent(routeDocumentId, streamingContent.value)
      messages.value.push({ role: 'system', content: 'AI 改写结果已保存回当前文档。' })
    }
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

<style scoped>
.chat-page { display: flex; min-height: calc(100vh - 124px); overflow: hidden; border: 1px solid #e1ebe7; border-radius: 16px; background: #fff; box-shadow: 0 16px 40px rgba(35, 74, 70, .08); }
.conversation-rail { width: 260px; flex: 0 0 260px; padding: 22px 16px; border-right: 1px solid #e7efec; background: #f7faf8; overflow-y: auto; }
.rail-heading span, .rail-heading strong { display: block; }
.rail-heading span { color: #91aaa3; font-size: 10px; letter-spacing: 1.5px; }
.rail-heading strong { margin: 4px 0 18px; color: #173b3d; font: 600 21px Georgia, serif; }
.new-conversation { width: 100%; margin-bottom: 18px; }
.conversation-item { padding: 12px; cursor: pointer; border-radius: 9px; margin-bottom: 8px; transition: transform .2s ease, background .2s ease; }
.conversation-item:hover { transform: translateX(3px); background: #edf5f1 !important; }
.conversation-item div:first-child { color: #294b4b; font-weight: 600; }
.conversation-item div:last-child { margin-top: 5px; color: #9aada8; font-size: 11px; }
.chat-panel { min-width: 0; flex: 1; display: flex; flex-direction: column; background: radial-gradient(circle at 80% 0%, #f0f7f4 0, transparent 35%), #fff; }
.chat-scroll { flex: 1; overflow-y: auto; padding: 30px clamp(18px, 5vw, 72px); }
.message-list { display: flex; flex-direction: column; gap: 18px; }
.message-row { display: flex; flex-direction: column; max-width: 78%; }
.message-row.is-user { align-self: flex-end; align-items: flex-end; }
.message-row.is-assistant { align-self: flex-start; align-items: flex-start; }
.message-bubble { padding: 13px 16px; border-radius: 14px; line-height: 1.7; white-space: pre-wrap; text-align: left; }
.user-bubble { color: #fff; background: #236d69; border-bottom-right-radius: 4px; }
.assistant-bubble { color: #294b4b; background: #eef5f1; border-bottom-left-radius: 4px; }
.streaming-message .assistant-bubble::after { content: '▋'; margin-left: 3px; color: #d89c47; animation: blink 1s steps(2, start) infinite; }
.empty-chat { display: grid; justify-items: center; align-content: center; min-height: 60%; color: #6f8b84; text-align: center; }
.empty-chat h2 { margin: 16px 0 8px; color: #173b3d; font: 600 28px Georgia, serif; }
.empty-chat p { margin: 0; }
.empty-orbit { display: grid; place-items: center; width: 62px; height: 62px; border: 1px solid #d4e5df; border-radius: 50%; color: #d89c47; font-size: 28px; animation: float 3s ease-in-out infinite; }
.composer { display: flex; gap: 12px; padding: 16px 24px 20px; border-top: 1px solid #e7efec; background: rgba(255,255,255,.9); }
.composer :deep(.el-input__wrapper) { box-shadow: 0 0 0 1px #d9e6e1 inset; }
.message-rise-enter-active { transition: opacity .3s ease, transform .3s ease; }
.message-rise-enter-from { opacity: 0; transform: translateY(10px); }
@keyframes float { 0%, 100% { transform: translateY(0); } 50% { transform: translateY(-7px); } }
@keyframes blink { 50% { opacity: 0; } }
@media (max-width: 700px) { .chat-page { min-height: calc(100vh - 100px); } .conversation-rail { width: 86px; flex-basis: 86px; padding: 16px 8px; } .rail-heading strong { font-size: 14px; } .conversation-item { padding: 8px; } .conversation-item div:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; } .conversation-item div:last-child { display: none; } .chat-scroll { padding: 22px 14px; } .message-row { max-width: 92%; } .composer { padding: 12px; } }
</style>
