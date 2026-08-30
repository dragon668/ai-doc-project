import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

const request = axios.create({
  baseURL: '/api',
  timeout: 30000
})

// 请求拦截器 - 添加Token
request.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  console.log('[API REQUEST]', config.method?.toUpperCase(), config.url, config.data || '')
  return config
})

// 响应拦截器 - 统一错误处理
request.interceptors.response.use(
  response => {
    const res = response.data
    console.log('[API RESPONSE]', response.status, response.config.url, res)
    if (res.code !== 200) {
      ElMessage.error(res.message || '请求失败')
      if (res.code === 401) {
        localStorage.removeItem('token')
        router.push('/login')
      }
      return Promise.reject(new Error(res.message))
    }
    return res
  },
  error => {
    console.error('[API ERROR]', error?.response?.status, error?.config?.url, error?.response?.data || error.message)
    ElMessage.error(error.message || '网络异常')
    return Promise.reject(error)
  }
)

export default request
