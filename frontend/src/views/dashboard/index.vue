<template>
  <div class="dashboard-page">
    <div class="dashboard-heading"><div><span class="eyebrow">ACTIVITY OVERVIEW</span><h2>工作台脉搏</h2><p>记录你的文档、协作与知识贡献。</p></div><el-tag type="success" effect="light">近一年</el-tag></div>

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
      <el-col :span="6"><el-card shadow="hover"><el-statistic title="编辑次数" :value="data.editCount" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><el-statistic title="贡献次数" :value="data.contributionCount" /></el-card></el-col>
      <el-col :span="6"><el-card shadow="hover"><el-statistic title="活跃天数" :value="data.activeDays" /></el-card></el-col>
    </el-row>

    <el-card class="contribution-card">
      <template #header><div class="card-title"><span>贡献活动</span><small>{{ data.contributionCount }} 次贡献 · {{ data.activeDays }} 个活跃日</small></div></template>
      <div class="contribution-grid">
        <span v-for="(count, index) in data.activity" :key="index" class="activity-cell" :class="activityLevel(count)" :title="`${count} 次贡献`"></span>
      </div>
      <div class="activity-legend"><span>少</span><i class="activity-cell level-0"></i><i class="activity-cell level-1"></i><i class="activity-cell level-2"></i><i class="activity-cell level-3"></i><span>多</span></div>
    </el-card>

    <el-row :gutter="20" class="chart-row">
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
  vectorizedDocs: 0, parsingDocs: 0, sharedLinks: 0, totalConversations: 0,
  editCount: 0, contributionCount: 0, activeDays: 0, activity: []
})
const chartRef = ref(null)
let chart

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

function activityLevel(value) {
  if (!value) return 'level-0'
  if (value <= 2) return 'level-1'
  if (value <= 5) return 'level-2'
  return 'level-3'
}

onMounted(async () => {
  const res = await getDashboard()
  data.value = res.data

  // 绘制ECharts饼图
  if (chartRef.value) {
    chart = echarts.init(chartRef.value)
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
    window.addEventListener('resize', () => chart?.resize())
  }
})
</script>

<style scoped>
.dashboard-page { min-height: 100%; }
.dashboard-heading { display: flex; align-items: flex-end; justify-content: space-between; margin-bottom: 22px; }
.eyebrow { color: #8aa29a; font-size: 10px; letter-spacing: 1.5px; }
.dashboard-heading h2 { margin: 5px 0; color: #173b3d; font: 600 28px Georgia, serif; }
.dashboard-heading p { margin: 0; color: #78918a; }
.dashboard-page :deep(.el-card) { border: 1px solid #e1ebe7; box-shadow: 0 10px 25px rgba(35, 74, 70, .05); }
.contribution-card { margin-top: 20px; }
.card-title { display: flex; justify-content: space-between; color: #173b3d; font-weight: 700; }
.card-title small { color: #8aa29a; font-weight: 400; }
.contribution-grid { display: grid; grid-template-columns: repeat(52, minmax(7px, 1fr)); grid-auto-flow: column; grid-template-rows: repeat(7, 12px); gap: 4px; overflow-x: auto; padding: 6px 2px 12px; }
.activity-cell { display: block; width: 12px; height: 12px; border-radius: 3px; background: #edf3f0; }
.activity-cell.level-1 { background: #b8d9c9; }.activity-cell.level-2 { background: #65a995; }.activity-cell.level-3 { background: #236d69; }
.activity-legend { display: flex; align-items: center; justify-content: flex-end; gap: 5px; color: #91a39d; font-size: 11px; }
.chart-row { margin-top: 20px; }
@media (max-width: 760px) { .contribution-grid { grid-template-columns: repeat(52, 12px); } .card-title small { display: none; } }
</style>
