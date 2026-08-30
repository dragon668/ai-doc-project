import request from '@/utils/request'

export function listAiConfig() {
  return request.get('/ai-config/list')
}

export function saveAiConfig(data) {
  return request.post('/ai-config', data)
}
