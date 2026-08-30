import request from '@/utils/request'

export function createConversation(data) {
  return request.post('/ai/conversation', data)
}

export function listConversations(workspaceId) {
  return request.get('/ai/conversation/list', { params: { workspaceId } })
}

export function deleteConversation(id) {
  return request.delete(`/ai/conversation/${id}`)
}

export function getMessages(conversationId) {
  return request.get(`/ai/conversation/${conversationId}/messages`)
}

// SSE流式问答 - 返回EventSource URL
export function getChatSseUrl() {
  return '/api/ai/chat'
}
