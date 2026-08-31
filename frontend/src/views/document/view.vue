<template>
  <div>
    <div style="margin-bottom: 16px; display:flex; justify-content:space-between; align-items:center">
      <el-button @click="$router.back()" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div style="display:flex; gap:8px; align-items:center">
        <el-button @click="handleShowVersions">版本历史</el-button>
        <el-button @click="handleDownload">下载</el-button>
        <el-button type="primary" :disabled="!isTextDoc || isSaving" @click="saveContent">
          {{ isSaving ? '保存中...' : '保存内容' }}
        </el-button>
      </div>
    </div>

    <el-card>
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center; gap: 12px">
          <span>{{ doc?.title }}</span>
          <el-select v-model="contentMode" style="width: 140px" v-if="isTextDoc">
            <el-option label="编辑模式" value="edit" />
            <el-option label="预览模式" value="preview" />
          </el-select>
        </div>
      </template>

      <div v-if="isTextDoc">
        <div v-if="contentMode === 'edit'" style="min-height: 420px">
          <MdEditor v-model="content" :toolbarsExclude="['save', 'github']" style="height: 480px" />
        </div>
        <div v-else style="min-height: 420px; background:#fafafa; padding:16px; border-radius:8px; border:1px solid #eee; overflow:auto">
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument, getDownloadUrl, getDocumentContent, updateDocumentContent, getVersionHistory, rollbackVersion } from '@/api/document'
import { ElMessage, ElMessageBox } from 'element-plus'
import { MdEditor, MdPreview } from 'md-editor-v3'
import 'md-editor-v3/lib/style.css'

const route = useRoute()
const docId = route.params.id
const doc = ref(null)
const content = ref('')
const contentMode = ref('edit')
const previewUrl = ref('')
const previewKind = ref('')
const isSaving = ref(false)
const showVersionDialog = ref(false)
const versions = ref([])

const isTextDoc = computed(() => {
  const type = (doc.value?.type || '').toLowerCase()
  return ['md', 'markdown', 'txt'].includes(type)
})

onMounted(async () => {
  const res = await getDocument(docId)
  doc.value = res.data
  doc.value.type = (res.data.type || '').toLowerCase()

  if (isTextDoc.value) {
    contentMode.value = 'edit'
    try {
      const contentRes = await getDocumentContent(docId)
      content.value = contentRes.data || ''
    } catch (error) {
      content.value = ''
    }
    return
  }

  if (doc.value.type === 'pdf') {
    previewKind.value = 'pdf'
  } else if (['doc', 'docx', 'xls', 'xlsx', 'ppt', 'pptx'].includes(doc.value.type)) {
    previewKind.value = 'download'
  }

  const downloadRes = await getDownloadUrl(docId)
  previewUrl.value = downloadRes.data
})

async function saveContent() {
  if (!isTextDoc.value || isSaving.value) return

  try {
    isSaving.value = true
    await updateDocumentContent(docId, content.value)
    ElMessage.success('文档内容已保存')
  } catch (error) {
    ElMessage.error(error?.response?.data?.message || '保存失败')
  } finally {
    isSaving.value = false
  }
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
    const contentRes = await getDocumentContent(docId)
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
