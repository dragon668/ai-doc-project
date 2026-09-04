<template>
  <div ref="documentPage" class="document-page">
    <div style="margin-bottom: 16px; display:flex; justify-content:space-between; align-items:center">
      <el-button @click="$router.back()" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div style="display:flex; gap:8px; align-items:center">
        <el-button @click="handleShowVersions">版本历史</el-button>
        <el-button @click="handleDownload">下载</el-button>
        <el-dropdown v-if="editableDoc" @command="exportContent">
          <el-button>导出</el-button>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="md">Markdown</el-dropdown-item>
              <el-dropdown-item command="txt">纯文本</el-dropdown-item>
              <el-dropdown-item command="html">HTML</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
        <el-button v-if="editableDoc" @click="showCanvas = true">无限画布</el-button>
        <el-button v-if="editableDoc" @click="openAiAssistant">AI 助手</el-button>
        <el-button @click="toggleFullscreen">全屏预览</el-button>
        <el-button type="primary" :disabled="!editableDoc || isSaving" @click="saveContent">
          {{ isSaving ? '保存中...' : '保存内容' }}
        </el-button>
      </div>
    </div>

    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px">
          <span>{{ doc?.title }}</span>
          <el-select v-model="contentMode" style="width: 140px" v-if="editableDoc">
            <el-option label="编辑模式" value="edit" />
            <el-option label="预览模式" value="preview" />
          </el-select>
        </div>
      </template>

      <div v-if="editableDoc">
        <div v-if="contentMode === 'edit'" class="editor-stage">
          <div class="editor-tools">
            <span class="tool-label">手账工具</span>
            <el-button size="small" @click="insertMedia('image')"><el-icon><Picture /></el-icon> 图片</el-button>
            <el-button size="small" @click="insertMedia('video')"><el-icon><VideoCamera /></el-icon> 视频</el-button>
            <el-button size="small" @click="insertDecoration">添加分隔装饰</el-button>
            <input ref="mediaInput" type="file" accept="image/*,video/*" hidden @change="handleMediaFile" />
            <el-button size="small" plain @click="mediaInput?.click()">本地媒体</el-button>
          </div>
          <MdEditor v-model="content" :toolbarsExclude="['save', 'github']" style="height: 480px" />
        </div>
        <div v-else class="preview-surface">
          <MdPreview :modelValue="content || '暂无内容'" />
        </div>
      </div>

      <div v-else-if="previewKind === 'pdf'" style="min-height: 520px; border:1px solid #eee; background:#fff">
        <embed v-if="previewUrl" :src="previewUrl" type="application/pdf" style="width:100%; height:520px; border:none" />
        <div v-else style="display:flex; align-items:center; justify-content:center; min-height:520px; color:#666">
          正在加载 PDF 预览…
        </div>
      </div>

      <div v-else-if="previewKind === 'download'" style="min-height: 420px; display:flex; align-items:center; justify-content:center; color:#666; flex-direction:column; gap:12px">
        <span>当前文件类型无法直接在线预览，建议下载查看</span>
        <el-button @click="handleDownload" type="primary">下载查看</el-button>
      </div>

      <div v-else style="min-height: 420px; display:flex; align-items:center; justify-content:center; color:#666; flex-direction:column; gap:12px">
        <span>当前文件类型暂不支持在线预览，建议直接下载查看</span>
        <el-button @click="handleDownload" type="primary">下载查看</el-button>
      </div>
    </el-card>

    <el-dialog v-model="showCanvas" title="无限画布" fullscreen destroy-on-close>
      <InfiniteCanvas @insert="insertCanvas" />
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog v-model="showVersionDialog" title="版本历史" width="620px">
      <el-timeline>
        <el-timeline-item v-for="ver in versions" :key="ver.id" :timestamp="formatTime(ver.createTime)" placement="top">
          <el-card>
            <p>版本 {{ ver.version }} - {{ ver.remark }}</p>
            <p style="color: #999; font-size: 12px">{{ formatSize(ver.fileSize) }}</p>
            <el-button size="small" type="primary" :disabled="ver.version === doc?.version" @click="handleRollback(ver)" style="margin-top: 8px">
              {{ ver.version === doc?.version ? '当前版本' : '回滚到此版本' }}
            </el-button>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>

    <el-dialog v-model="aiVisible" title="AI 编辑助手" width="520px">
      <p class="ai-hint">AI 对话会基于当前工作区的知识库，你可以让它总结、改写或补充这篇文档。</p>
      <el-input v-model="aiInstruction" type="textarea" :rows="4" placeholder="例如：请把这篇文档整理成清晰的项目计划" />
      <template #footer>
        <el-button @click="aiVisible = false">取消</el-button>
        <el-button type="primary" @click="openAiAssistant">打开 AI 对话</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getDocument, getDownloadUrl, getEditableContent, updateEditableContent, getVersionHistory, rollbackVersion } from '@/api/document'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'
import InfiniteCanvas from '@/components/document/InfiniteCanvas.vue'

const route = useRoute()
const router = useRouter()
const docId = route.params.id
const doc = ref(null)
const content = ref('')
const contentMode = ref('edit')
const previewUrl = ref('')
const previewKind = ref('')
const isSaving = ref(false)
const showVersionDialog = ref(false)
const versions = ref([])
const showCanvas = ref(false)
const documentPage = ref(null)
const mediaInput = ref(null)
const aiVisible = ref(false)
const aiInstruction = ref('')

const editableDoc = computed(() => {
  const type = (doc.value?.type || '').toLowerCase()
  return ['md', 'markdown', 'txt', 'pdf', 'doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(type)
})

onMounted(async () => {
  const res = await getDocument(docId)
  doc.value = res.data
  doc.value.type = (res.data.type || '').toLowerCase()

  if (editableDoc.value) {
    contentMode.value = 'edit'
    try {
      const contentRes = await getEditableContent(docId)
      content.value = contentRes.data || ''
    } catch (error) {
      content.value = ''
    }
    return
  }

  if (doc.value.type === 'pdf') previewKind.value = 'pdf'
  else previewKind.value = 'download'

  const downloadRes = await getDownloadUrl(docId)
  previewUrl.value = downloadRes.data
})

async function saveContent() {
  if (!editableDoc.value || isSaving.value) return

  try {
    isSaving.value = true
    await updateEditableContent(docId, content.value)
    ElMessage.success('文档内容已保存')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    isSaving.value = false
  }
}

function exportContent(format) {
  const baseName = (doc.value?.title || 'document').replace(/\.[^.]+$/, '')
  const payload = format === 'html'
    ? `<!doctype html><html lang="zh-CN"><meta charset="utf-8"><title>${escapeHtml(baseName)}</title><body><pre>${escapeHtml(content.value)}</pre></body></html>`
    : content.value
  const mime = format === 'html' ? 'text/html;charset=utf-8' : format === 'md' ? 'text/markdown;charset=utf-8' : 'text/plain;charset=utf-8'
  const link = document.createElement('a')
  link.href = URL.createObjectURL(new Blob([payload], { type: mime }))
  link.download = `${baseName}.${format}`
  link.click()
  URL.revokeObjectURL(link.href)
  ElMessage.success(`已导出 ${format.toUpperCase()} 文件`)
}

function escapeHtml(value) {
  return value.replace(/[&<>"']/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[char]))
}

function toggleFullscreen() {
  if (!document.fullscreenElement) documentPage.value?.requestFullscreen()
  else document.exitFullscreen()
}

function openAiAssistant() {
  aiVisible.value = false
  router.push({ path: `/ai/${doc.value?.workspaceId}`, query: { prompt: aiInstruction.value, documentId: docId } })
}

function insertCanvas(dataUrl) {
  content.value += `${content.value ? '\n\n' : ''}![画布](${dataUrl})`
  showCanvas.value = false
  contentMode.value = 'edit'
}

async function insertMedia(type) {
  try {
    const result = await ElMessageBox.prompt(`输入${type === 'image' ? '图片' : '视频'} URL`, `插入${type === 'image' ? '图片' : '视频'}`, { inputPlaceholder: 'https://...' })
    const url = result.value.trim()
    if (!url) return
    content.value += `${content.value ? '\n\n' : ''}${type === 'image' ? `![手账图片](${url})` : `<video controls src="${url}" style="max-width:100%"></video>`}`
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('媒体插入失败')
  }
}

function handleMediaFile(event) {
  const file = event.target.files?.[0]
  if (!file) return
  const reader = new FileReader()
  reader.onload = () => {
    const url = reader.result
    content.value += `${content.value ? '\n\n' : ''}${file.type.startsWith('image/') ? `![${escapeHtml(file.name)}](${url})` : `<video controls src="${url}" style="max-width:100%"></video>`}`
    event.target.value = ''
  }
  reader.readAsDataURL(file)
}

function insertDecoration() {
  content.value += `${content.value ? '\n\n' : ''}> ✦ · · · · · · · · · · · · · · · · · ✦\n\n`
}

async function handleDownload() {
  const res = await getDownloadUrl(docId)
  window.open(res.data, '_blank')
}

async function handleShowVersions() {
  try {
    const res = await getVersionHistory(docId)
    versions.value = res.data || []
    showVersionDialog.value = true
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '获取版本历史失败')
  }
}

async function handleRollback(ver) {
  try {
    await ElMessageBox.confirm(`确认回滚到版本 ${ver.version}？`)
    await rollbackVersion(docId, ver.version)
    ElMessage.success('回滚成功')
    showVersionDialog.value = false
    // 重新加载文档内容
    const contentRes = await getEditableContent(docId)
    content.value = contentRes.data || ''
    const docRes = await getDocument(docId)
    doc.value.type = (docRes.data.type || '').toLowerCase()
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '回滚失败')
    }
  }
}

function formatTime(t) {
  return t || ''
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}
</script>

<style scoped>
.document-page { min-height: 100%; }
.editor-stage { padding: 16px; border: 1px solid #e1ebe7; border-radius: 14px; background: #f4f0e7; }
.editor-tools { display: flex; align-items: center; flex-wrap: wrap; gap: 8px; margin-bottom: 12px; }
.tool-label { margin-right: 4px; color: #8e7760; font-size: 12px; font-weight: 700; letter-spacing: .5px; }
.editor-stage :deep(.md-editor) { border: 0; border-radius: 9px; box-shadow: 0 8px 24px rgba(100, 79, 54, .08); }
.editor-stage :deep(.md-editor-content) { background: #fffdf8; }
.preview-surface { min-height: 420px; background: #fafafa; padding: 16px; border-radius: 8px; border: 1px solid #eee; overflow: auto; }
.ai-hint { color: #78918a; line-height: 1.7; }
.document-page:fullscreen { padding: 24px; overflow: auto; background: #f5f7fa; }
</style>
