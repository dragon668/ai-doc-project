<template>
  <div>
    <h2 style="margin-bottom: 20px">统计看板</h2>

    <el-row :gutter="20" style="margin-bottom: 20px">
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="文档总数" :value="data.totalDocs" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="已向量化" :value="data.vectorizedDocs" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="分享链接" :value="data.sharedLinks" />
        </el-card>
      </el-col>
      <el-col :span="6">
        <el-card shadow="hover">
          <el-statistic title="AI对话数" :value="data.totalConversations" />
        </el-card>
      </el-col>
    </el-row>

    <el-row :gutter="20">
      <el-col :span="12">
        <el-card>
          <template #header>存储空间使用</template>
          <el-progress :percentage="storagePercent" :stroke-width="20" style="margin: 20px 0" />
          <p style="color: #666">
            已使用 {{ formatSize(data.usedStorage) }} / 总计 {{ formatSize(data.totalStorage) }}
          </p>
        </el-card>
      </el-col>
      <el-col :span="12">
        <el-card>
          <template #header>文档状态分布</template>
          <div ref="chartRef" style="height: 250px"></div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { getDashboard } from '@/api/dashboard'
import * as echarts from 'echarts'

const data = ref({
  totalDocs: 0, totalSize: 0, usedStorage: 0, totalStorage: 0,
  vectorizedDocs: 0, parsingDocs: 0, sharedLinks: 0, totalConversations: 0
})
const chartRef = ref(null)

const storagePercent = computed(() => {
  if (!data.value.totalStorage) return 0
  return Math.round((data.value.usedStorage / data.value.totalStorage) * 100)
})

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(1) + ' ' + units[i]
}

onMounted(async () => {
  const res = await getDashboard()
  data.value = res.data

  // 绘制ECharts饼图
  if (chartRef.value) {
    const chart = echarts.init(chartRef.value)
    chart.setOption({
      tooltip: { trigger: 'item' },
      legend: { bottom: '5%' },
      series: [{
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        label: { show: false },
        data: [
          { value: data.value.vectorizedDocs, name: '已向量化' },
          { value: data.value.parsingDocs, name: '解析中' },
          { value: Math.max(0, data.value.totalDocs - data.value.vectorizedDocs - data.value.parsingDocs), name: '未处理' }
        ]
      }]
    })
  }
})
</script>
