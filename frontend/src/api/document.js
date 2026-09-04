import request from '@/utils/request'

export function listDocuments(params) {
  return request.get('/doc/list', { params })
}

export function getDocument(id) {
  return request.get(`/doc/${id}`)
}

export function createTextDocument(payload) {
  return request.post('/doc/text', payload)
}

export function getDocumentContent(id) {
  return request.get(`/doc/${id}/content`)
}

export function getEditableContent(id) {
  return request.get(`/doc/${id}/editable-content`)
}

export function updateDocumentContent(id, content) {
  return request.post(`/doc/${id}/content`, { content })
}

export function updateEditableContent(id, content) {
  return request.post(`/doc/${id}/editable-content`, { content })
}

export function deleteDocument(id) {
  return request.delete(`/doc/${id}`)
}

export function getVersionHistory(id) {
  return request.get(`/doc/${id}/versions`)
}

export function rollbackVersion(id, version) {
  return request.post(`/doc/${id}/rollback/${version}`)
}

export function getDownloadUrl(id) {
  return request.get(`/doc/${id}/download`)
}

// 分片上传相关
export function initUpload(params) {
  return request.post('/doc/upload/init', null, { params })
}

export function uploadChunk(formData) {
  return request.post('/doc/upload/chunk', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function mergeChunks(params) {
  return request.post('/doc/upload/merge', null, { params })
}
