import request from '@/utils/request'

export function getDashboard() {
  return request.get('/dashboard')
}
