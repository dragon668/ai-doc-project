<template>
  <div>
    <div style="margin-bottom: 16px; display:flex; justify-content:space-between; align-items:center">
      <el-button @click="$router.back()" text>
        <el-icon><ArrowLeft /></el-icon> 返回
      </el-button>
      <div style="display:flex; gap:8px; align-items:center">
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
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { getDocument, getDownloadUrl, getDocumentContent, updateDocumentContent } from '@/api/document'
import { ElMessage } from 'element-plus'
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
</script>
