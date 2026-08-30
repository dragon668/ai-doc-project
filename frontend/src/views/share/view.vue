<template>
  <div style="max-width: 600px; margin: 100px auto">
    <el-card>
      <h2 style="text-align: center; margin-bottom: 20px">查看分享文档</h2>
      <div v-if="shareInfo">
        <p>文档ID: {{ shareInfo.documentId }}</p>
        <p>过期时间: {{ shareInfo.expireTime }}</p>
        <p>查看次数: {{ shareInfo.viewCount }}</p>
        <el-button type="primary" style="width: 100%; margin-top: 20px" @click="accessDoc">查看文档</el-button>
      </div>
      <div v-else>
        <el-input v-model="password" placeholder="请输入提取码(如有)" style="margin-bottom: 16px" />
        <el-button type="primary" style="width: 100%" @click="verifyShare">验证</el-button>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import request from '@/utils/request'

const route = useRoute()
const router = useRouter()
const code = route.params.code
const shareInfo = ref(null)
const password = ref('')

onMounted(async () => {
  try {
    const res = await request.get(`/share/view/${code}`)
    shareInfo.value = res.data
    // 无需提取码直接显示
    if (!shareInfo.value.password) {
      await request.post(`/share/verify/${code}`, { password: '' })
    }
  } catch (e) {
    // 需要提取码
  }
})

async function verifyShare() {
  await request.post(`/share/verify/${code}`, { password: password.value })
  const res = await request.get(`/share/view/${code}`)
  shareInfo.value = res.data
}

function accessDoc() {
  router.push(`/document/${shareInfo.value.documentId}`)
}
</script>
