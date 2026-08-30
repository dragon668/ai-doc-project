<template>
  <div>
    <el-upload
      ref="uploadRef"
      :auto-upload="false"
      :show-file-list="false"
      :on-change="handleFileChange"
      accept=".pdf,.md,.docx,.txt"
    >
      <el-button type="primary">选择文件</el-button>
    </el-upload>

    <div v-if="selectedFile" style="margin-top: 16px">
      <p>文件: {{ selectedFile.name }} ({{ formatSize(selectedFile.size) }})</p>
      <el-progress :percentage="uploadProgress" :status="uploadStatus" style="margin: 12px 0" />
      <p v-if="statusText" style="font-size: 12px; color: #666">{{ statusText }}</p>
      <el-button type="success" @click="startUpload" :disabled="uploading" style="margin-top: 8px">
        {{ uploading ? '上传中...' : '开始上传' }}
      </el-button>
    </div>

    <div v-if="quickUpload" style="margin-top: 12px">
      <el-result icon="success" title="秒传成功" sub-title="文件已存在，无需重复上传" />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import SparkMD5 from 'spark-md5'
import { initUpload, uploadChunk, mergeChunks } from '@/api/document'

const props = defineProps({ workspaceId: { type: [String, Number], required: true } })
const emit = defineEmits(['success'])

const CHUNK_SIZE = 5 * 1024 * 1024 // 5MB

const selectedFile = ref(null)
const uploading = ref(false)
const uploadProgress = ref(0)
const uploadStatus = ref('')
const statusText = ref('')
const quickUpload = ref(false)

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

function handleFileChange(file) {
  selectedFile.value = file.raw
  uploadProgress.value = 0
  uploadStatus.value = ''
  statusText.value = ''
  quickUpload.value = false
}

async function calculateMD5(file) {
  return new Promise((resolve) => {
    const spark = new SparkMD5.ArrayBuffer()
    const reader = new FileReader()
    const chunks = Math.ceil(file.size / CHUNK_SIZE)
    let currentChunk = 0

    reader.onload = (e) => {
      spark.append(e.target.result)
      currentChunk++
      if (currentChunk < chunks) {
        loadNext()
      } else {
        resolve(spark.end())
      }
    }

    function loadNext() {
      const start = currentChunk * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      reader.readAsArrayBuffer(file.slice(start, end))
    }

    loadNext()
  })
}

async function startUpload() {
  if (!selectedFile.value) return
  uploading.value = true
  statusText.value = '正在计算文件MD5...'

  try {
    const file = selectedFile.value
    const fileMD5 = await calculateMD5(file)
    const totalChunks = Math.ceil(file.size / CHUNK_SIZE)

    statusText.value = '初始化上传...'
    const initRes = await initUpload({
      fileName: file.name,
      md5: fileMD5,
      totalChunks,
      totalSize: file.size,
      workspaceId: props.workspaceId
    })

    const result = initRes.data

    // 秒传
    if (result.quickUpload) {
      quickUpload.value = true
      uploadProgress.value = 100
      uploadStatus.value = 'success'
      statusText.value = '秒传成功'
      emit('success')
      return
    }

    const uploadedSet = new Set(result.uploadedChunks || [])

    // 逐片上传
    for (let i = 0; i < totalChunks; i++) {
      if (uploadedSet.has(i)) continue // 跳过已上传的分片(断点续传)

      const start = i * CHUNK_SIZE
      const end = Math.min(start + CHUNK_SIZE, file.size)
      const chunk = file.slice(start, end)

      const formData = new FormData()
      formData.append('file', chunk)
      formData.append('uploadId', result.uploadId)
      formData.append('fileName', file.name)
      formData.append('md5', fileMD5)
      formData.append('chunkIndex', i.toString())
      formData.append('totalChunks', totalChunks.toString())

      await uploadChunk(formData)

      uploadProgress.value = Math.round(((i + 1) / totalChunks) * 90)
      statusText.value = `上传中 ${i + 1}/${totalChunks}`
    }

    // 合并分片
    statusText.value = '合并分片中...'
    await mergeChunks({
      uploadId: result.uploadId,
      fileName: file.name,
      md5: fileMD5,
      workspaceId: props.workspaceId
    })

    uploadProgress.value = 100
    uploadStatus.value = 'success'
    statusText.value = '上传完成'
    emit('success')

  } catch (e) {
    uploadStatus.value = 'exception'
    statusText.value = '上传失败: ' + (e.message || '未知错误')
  } finally {
    uploading.value = false
  }
}
</script>
