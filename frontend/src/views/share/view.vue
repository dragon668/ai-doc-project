<template>
  <div style="max-width: 860px; margin: 40px auto; padding: 0 16px">
    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <h2 style="margin: 0">
            <el-icon style="vertical-align: middle"><Share /></el-icon> 查看分享文档
          </h2>
          <el-button @click="goHome" text>
            <el-icon><HomeFilled /></el-icon> 前往登录
          </el-button>
        </div>
      </template>

      <!-- 需要提取码：先验证 -->
      <div v-if="needPassword && !accessible">
        <el-input v-model="password" placeholder="请输入提取码" size="large" style="margin-bottom: 16px" />
        <el-button type="primary" style="width: 100%" size="large" @click="verifyShare">验证后查看</el-button>
      </div>

      <!-- 验证/加载中 -->
      <div v-else-if="loading" style="text-align: center; padding: 40px">
        <el-icon class="is-loading" :size="28"><Loading /></el-icon>
        <p style="color: #666">正在加载分享文档...</p>
      </div>

      <!-- 文档内容 -->
      <div v-else-if="doc">
        <h3 style="margin-top: 0">{{ doc.title }}</h3>
        <el-tag size="small" style="margin-bottom: 16px">{{ doc.type?.toUpperCase() }} · {{ formatSize(doc.fileSize) }}</el-tag>

        <!-- 文本类型：Markdown 预览 -->
        <div v-if="isTextDoc" style="min-height: 420px; background:#fafafa; padding:16px; border-radius:8px; border:1px solid #eee; overflow:auto">
          <MdPreview :modelValue="content || '暂无内容'" />
        </div>

        <!-- PDF 预览 -->
        <div v-else-if="doc.type === 'pdf'" style="min-height: 520px; border:1px solid #eee; background:#fff">
          <embed v-if="previewUrl" :src="previewUrl" type="application/pdf" style="width:100%; height:560px; border:none" />
          <div v-else style="display:flex; align-items:center; justify-content:center; min-height:520px; color:#666">正在加载 PDF 预览…</div>
        </div>

        <!-- 其他类型：下载 -->
        <div v-else style="min-height: 320px; display:flex; align-items:center; justify-content:center; color:#666; flex-direction:column; gap:12px">
          <span>当前文件类型暂不支持在线预览，可直接下载查看</span>
          <el-button type="primary" @click="handleDownload">下载查看</el-button>
        </div>
      </div>

      <div v-else style="text-align: center; padding: 40px; color: #666">
        分享链接不存在或已过期
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const route = useRoute()
const router = useRouter()
const code = route.params.code
const doc = ref(null)
const content = ref('')
const previewUrl = ref('')
const password = ref('')
const needPassword = ref(false)
const accessible = ref(false)
const loading = ref(true)

const isTextDoc = computed(() => {
  const type = (doc.value?.type || '').toLowerCase()
  return ['md', 'markdown', 'txt'].includes(type)
})

onMounted(async () => {
  await loadDoc()
})

async function loadDoc() {
  loading.value = true
  try {
    const res = await request.get(`/share/doc/${code}`)
    doc.value = res.data
    accessible.value = true
    await loadContent()
  } catch (e) {
    loading.value = false
    if (e?.response?.status === 403 || e?.response?.data?.code === 403 ||
        (e?.message && (e.message.includes('提取码') || e.message.includes('密码')))) {
      needPassword.value = true
    }
  }
}

async function loadContent() {
  try {
    if (isTextDoc.value) {
      const res = await request.get(`/share/doc/${code}/content`, shareConfig())
      content.value = res.data || ''
    } else if (doc.value.type === 'pdf') {
      const res = await request.get(`/share/doc/${code}/download`, shareConfig())
      previewUrl.value = res.data
    }
  } catch (e) {
    ElMessage.error(e?.message || '文档加载失败')
  } finally {
    loading.value = false
  }
}

async function verifyShare() {
  try {
    const res = await request.get(`/share/doc/${code}`, shareConfig())
    doc.value = res.data
    accessible.value = true
    needPassword.value = false
    await loadContent()
  } catch (e) {
    ElMessage.error(e?.message || '提取码错误或链接无效')
  }
}

function handleDownload() {
  if (!previewUrl.value) {
    request.get(`/share/doc/${code}/download`, shareConfig()).then(res => {
      window.open(res.data, '_blank')
    }).catch(() => ElMessage.error('下载失败'))
    return
  }
  window.open(previewUrl.value, '_blank')
}

// 通过请求头传递提取码，避免 Query 参数影响路径变量绑定
function shareConfig() {
  if (!password.value) return {}
  return { headers: { 'X-Share-Password': password.value } }
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function goHome() {
  router.push('/login')
}
</script>
