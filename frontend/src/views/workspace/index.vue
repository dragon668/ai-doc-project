<template>
  <div>
    <div style="display: flex; justify-content: space-between; align-items: center; margin-bottom: 20px">
      <h2>我的工作空间</h2>
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 新建空间
      </el-button>
    </div>

    <el-row :gutter="20">
      <el-col :span="8" v-for="ws in workspaces" :key="ws.id">
        <el-card shadow="hover" style="cursor: pointer; margin-bottom: 20px" @click="goToWorkspace(ws)">
          <template #header>
            <div style="display: flex; justify-content: space-between; align-items: center">
              <span style="font-weight: bold">{{ ws.name }}</span>
              <el-tag :type="ws.type === 1 ? 'success' : 'primary'" size="small">
                {{ ws.type === 1 ? '个人' : '团队' }}
              </el-tag>
            </div>
          </template>
          <p style="color: #666">{{ ws.description || '暂无描述' }}</p>
        </el-card>
      </el-col>
    </el-row>

    <!-- 新建空间对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建工作空间" width="400px">
      <el-form :model="newWs" label-width="80px">
        <el-form-item label="名称">
          <el-input v-model="newWs.name" placeholder="输入空间名称" />
        </el-form-item>
        <el-form-item label="描述">
          <el-input v-model="newWs.description" type="textarea" placeholder="空间描述(可选)" />
        </el-form-item>
        <el-form-item label="类型">
          <el-radio-group v-model="newWs.type">
            <el-radio :value="1">个人</el-radio>
            <el-radio :value="2">团队</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { listWorkspaces, createWorkspace } from '@/api/workspace'
import { ElMessage } from 'element-plus'

const router = useRouter()
const workspaces = ref([])
const showCreateDialog = ref(false)
const newWs = reactive({ name: '', description: '', type: 1 })

onMounted(async () => {
  const res = await listWorkspaces()
  workspaces.value = res.data
})

function goToWorkspace(ws) {
  router.push(`/workspace/${ws.id}`)
}

async function handleCreate() {
  if (!newWs.name) {
    ElMessage.warning('请输入空间名称')
    return
  }
  await createWorkspace(newWs)
  ElMessage.success('创建成功')
  showCreateDialog.value = false
  const res = await listWorkspaces()
  workspaces.value = res.data
  newWs.name = ''
  newWs.description = ''
}
</script>
