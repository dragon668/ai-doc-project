import request from '@/utils/request'

export function listWorkspaces() {
  return request.get('/workspace/list')
}

export function createWorkspace(data) {
  return request.post('/workspace', data)
}

export function getWorkspace(id) {
  return request.get(`/workspace/${id}`)
}

export function updateWorkspace(id, data) {
  return request.put(`/workspace/${id}`, data)
}

export function deleteWorkspace(id) {
  return request.delete(`/workspace/${id}`)
}

export function listMembers(workspaceId) {
  return request.get(`/workspace/${workspaceId}/members`)
}

export function addMember(workspaceId, data) {
  return request.post(`/workspace/${workspaceId}/members`, data)
}

export function removeMember(workspaceId, userId) {
  return request.delete(`/workspace/${workspaceId}/members/${userId}`)
}
