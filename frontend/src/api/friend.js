import request from '@/utils/request'

export function listFriends() {
  return request.get('/friend/list')
}

export function searchUsers(keyword) {
  return request.get('/friend/search', { params: { keyword } })
}

export function addFriend(friendId) {
  return request.post('/friend/add', { friendId })
}
