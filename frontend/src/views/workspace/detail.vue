<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <div>
        <el-button @click="$router.push('/workspace')" text>
          <el-icon><ArrowLeft /></el-icon> 返回
        </el-button>
        <h2 style="display: inline">{{ workspace?.name || '工作空间' }}</h2>
      </div>
      <div style="display: flex; gap: 10px">
        <el-button type="primary" @click="showUploadDialog = true">
          <el-icon><Upload /></el-icon> 上传文档
        </el-button>
        <el-button @click="createBlankDocument">
          <el-icon><Document /></el-icon> 新建文档
        </el-button>
        <el-button @click="$router.push(`/ai/${workspaceId}`)">
          <el-icon><ChatDotRound /></el-icon> AI问答
        </el-button>
      </div>
    </div>

    <!-- 文档列表 -->
    <el-table :data="documents" stripe style="width: 100%">
      <el-table-column prop="title" label="文档名称" min-width="200" />
      <el-table-column prop="type" label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small">{{ row.type?.toUpperCase() }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="120">
        <template #default="{ row }">
          {{ formatSize(row.fileSize) }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="120">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusText(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="version" label="版本" width="80" />
      <el-table-column label="操作" width="280">
        <template #default="{ row }">
          <el-button size="small" @click="viewDoc(row)">查看</el-button>
          <el-button size="small" @click="showVersions(row)">版本</el-button>
          <el-button size="small" type="primary" @click="handleShare(row)">分享</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-dialog v-model="showShareDialog" title="分享链接" width="500px">
      <el-form :model="shareForm" label-width="120px">
        <el-form-item label="链接有效时长">
          <el-select v-model="shareForm.expireHours" style="width: 100%">
            <el-option :value="1" label="1小时" />
            <el-option :value="24" label="24小时" />
            <el-option :value="168" label="7天" />
          </el-select>
        </el-form-item>
        <el-form-item label="访问权限">
          <el-select v-model="shareForm.permission" style="width: 100%">
            <el-option :value="1" label="可查看" />
            <el-option :value="2" label="可编辑" />
          </el-select>
        </el-form-item>
        <el-form-item label="提取码（可空）">
          <el-input v-model="shareForm.password" placeholder="不填则无需提取码" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showShareDialog = false">取消</el-button>
        <el-button type="primary" @click="createShareLink">生成链接</el-button>
      </template>
    </el-dialog>

    <!-- 上传文档对话框(分片上传) -->
    <el-dialog v-model="showUploadDialog" title="上传文档" width="500px">
      <chunk-upload :workspace-id="workspaceId" @success="onUploadSuccess" />
    </el-dialog>

    <!-- 版本历史对话框 -->
    <el-dialog v-model="showVersionDialog" title="版本历史" width="600px">
      <el-timeline>
        <el-timeline-item v-for="ver in versions" :key="ver.id" :timestamp="ver.createTime" placement="top">
          <el-card>
            <p>版本 {{ ver.version }} - {{ ver.remark }}</p>
            <p style="color: #999; font-size: 12px">{{ formatSize(ver.fileSize) }}</p>
            <el-button size="small" @click="handleRollback(ver)" style="margin-top: 8px">回滚到此版本</el-button>
          </el-card>
        </el-timeline-item>
      </el-timeline>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getWorkspace } from '@/api/workspace'
import { listDocuments, deleteDocument, getVersionHistory, rollbackVersion, createTextDocument } from '@/api/document'
import { ElMessage, ElMessageBox } from 'element-plus'
import ChunkUpload from '@/components/upload/ChunkUpload.vue'

const route = useRoute()
const router = useRouter()
const workspaceId = route.params.id
const workspace = ref(null)
const documents = ref([])
const showUploadDialog = ref(false)
const showVersionDialog = ref(false)
const versions = ref([])
const currentDocId = ref(null)
const showShareDialog = ref(false)
const shareForm = ref({ expireHours: 24, permission: 1, password: '' })
const currentShareDoc = ref(null)

onMounted(async () => {
  const wsRes = await getWorkspace(workspaceId)
  workspace.value = wsRes.data
  await loadDocuments()
})

async function loadDocuments() {
  const res = await listDocuments({ workspaceId, page: 1, size: 50 })
  documents.value = res.data.records || []
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function statusText(s) {
  return ['上传中', '正常', '解析中', '已向量化', '解析失败'][s] || '未知'
}
function statusType(s) {
  return ['info', 'success', 'warning', 'success', 'danger'][s] || 'info'
}

function viewDoc(row) {
  router.push(`/document/${row.id}`)
}

async function showVersions(row) {
  currentDocId.value = row.id
  const res = await getVersionHistory(row.id)
  versions.value = res.data
  showVersionDialog.value = true
}

async function handleRollback(ver) {
  await ElMessageBox.confirm(`确认回滚到版本 ${ver.version}？`)
  await rollbackVersion(currentDocId.value, ver.version)
  ElMessage.success('回滚成功')
  await loadDocuments()
}

async function handleDelete(row) {
  await ElMessageBox.confirm('确认删除此文档？')
  await deleteDocument(row.id)
  ElMessage.success('删除成功')
  await loadDocuments()
}

function handleShare(row) {
  currentShareDoc.value = row
  showShareDialog.value = true
}

async function createShareLink() {
  if (!currentShareDoc.value) return
  const res = await window.fetch('/api/share', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${localStorage.getItem('token') || ''}` },
    body: JSON.stringify({
      documentId: currentShareDoc.value.id,
      expireHours: shareForm.value.expireHours,
      permission: shareForm.value.permission,
      password: shareForm.value.password || ''
    })
  }).then(r => r.json())
  if (res.code === 200) {
    const link = `${window.location.origin}/share/${res.data.code}`
    navigator.clipboard?.writeText(link).catch(() => {})
    ElMessage.success(`分享链接已生成：${link}`)
    showShareDialog.value = false
  } else {
    ElMessage.error(res.message || '生成分享链接失败')
  }
}

async function createBlankDocument() {
  try {
    const { value } = await ElMessageBox.prompt('请输入文档名称', '新建 Markdown 文档', {
      confirmButtonText: '创建',
      cancelButtonText: '取消',
      inputValue: '新建文档.md',
      inputPattern: /\S/,
      inputErrorMessage: '文档名称不能为空'
    })

    const title = String(value || '').trim() || '新建文档.md'
    const fileName = title.endsWith('.md') || title.endsWith('.txt') ? title : `${title}.md`
    const content = `# ${fileName.replace(/\.(md|txt)$/i, '')}\n\n`

    const res = await createTextDocument({
      workspaceId,
      title: fileName,
      content
    })

    ElMessage.success('文档已创建')
    await loadDocuments()
    router.push(`/document/${res.data.id}`)
  } catch (error) {
    if (error !== 'cancel') {
      ElMessage.error(error?.response?.data?.message || '创建文档失败')
    }
  }
}

function onUploadSuccess() {
  showUploadDialog.value = false
  loadDocuments()
}
</script>
