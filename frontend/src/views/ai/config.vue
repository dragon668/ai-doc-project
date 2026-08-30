<template>
  <div style="max-width: 700px; margin: 20px auto">
    <el-card>
      <template #header>
        <div style="display:flex;justify-content:space-between;align-items:center">
          <span>AI 接口配置</span>
          <el-button type="primary" @click="saveConfig">保存</el-button>
        </div>
      </template>

      <el-form :model="form" label-width="100px">
        <el-form-item label="Provider">
          <el-select v-model="form.provider" placeholder="选择服务商">
            <el-option label="OpenAI" value="openai" />
            <el-option label="DeepSeek" value="deepseek" />
            <el-option label="SiliconFlow" value="siliconflow" />
          </el-select>
        </el-form-item>
        <el-form-item label="Base URL">
          <el-input v-model="form.baseUrl" placeholder="https://api.openai.com/v1" />
        </el-form-item>
        <el-form-item label="API Key">
          <el-input v-model="form.apiKey" type="password" show-password placeholder="输入 API Key" />
        </el-form-item>
        <el-form-item label="Model Name">
          <el-input v-model="form.modelName" placeholder="gpt-4o-mini" />
        </el-form-item>
        <el-form-item label="默认配置">
          <el-switch v-model="form.isDefault" :active-value="1" :inactive-value="0" />
        </el-form-item>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { listAiConfig, saveAiConfig } from '@/api/aiConfig'

const form = ref({
  provider: 'openai',
  apiKey: '',
  baseUrl: 'https://api.openai.com/v1',
  modelName: 'gpt-4o-mini',
  isDefault: 1
})

onMounted(async () => {
  const res = await listAiConfig()
  if (res.data && res.data.length) {
    const cfg = res.data[0]
    form.value = {
      provider: cfg.provider || 'openai',
      apiKey: cfg.apiKey || '',
      baseUrl: cfg.baseUrl || 'https://api.openai.com/v1',
      modelName: cfg.modelName || 'gpt-4o-mini',
      isDefault: cfg.isDefault || 0
    }
  }
})

async function saveConfig() {
  await saveAiConfig(form.value)
  ElMessage.success('保存成功')
}
</script>
