<template>
  <div class="canvas-shell">
    <div class="canvas-toolbar">
      <el-button-group>
        <el-button :type="mode === 'draw' ? 'primary' : 'default'" @click="mode = 'draw'">画笔</el-button>
        <el-button :type="mode === 'pan' ? 'primary' : 'default'" @click="mode = 'pan'">平移</el-button>
      </el-button-group>
      <el-color-picker v-model="strokeColor" />
      <el-button @click="addText">添加文字</el-button>
      <el-button @click="clearCanvas">清空</el-button>
      <el-button @click="exportPng">导出 PNG</el-button>
      <el-button type="primary" @click="insertIntoDocument">插入文档</el-button>
      <span class="canvas-tip">滚轮缩放 · 平移模式拖动画布</span>
    </div>
    <div ref="viewport" class="canvas-viewport" @wheel.prevent="zoomCanvas">
      <canvas ref="canvas" @pointerdown="startPointer" @pointermove="movePointer" @pointerup="endPointer" @pointerleave="endPointer" />
    </div>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const emit = defineEmits(['insert'])
const canvas = ref(null)
const viewport = ref(null)
const mode = ref('draw')
const strokeColor = ref('#245b8f')
const scale = ref(1)
const offset = ref({ x: 0, y: 0 })
const drawing = ref(false)
const lastPoint = ref(null)
const canvasSize = { width: 1400, height: 800 }

function context() {
  return canvas.value.getContext('2d')
}

function resizeCanvas() {
  const ratio = window.devicePixelRatio || 1
  canvas.value.width = canvasSize.width * ratio
  canvas.value.height = canvasSize.height * ratio
  canvas.value.style.width = `${canvasSize.width}px`
  canvas.value.style.height = `${canvasSize.height}px`
  context().setTransform(ratio, 0, 0, ratio, 0, 0)
  drawGrid()
}

function drawGrid() {
  const ctx = context()
  ctx.save()
  ctx.clearRect(0, 0, canvasSize.width, canvasSize.height)
  ctx.fillStyle = '#fbfcfe'
  ctx.fillRect(0, 0, canvasSize.width, canvasSize.height)
  ctx.strokeStyle = '#e8edf3'
  ctx.lineWidth = 1
  for (let x = 0; x <= canvasSize.width; x += 32) {
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, canvasSize.height); ctx.stroke()
  }
  for (let y = 0; y <= canvasSize.height; y += 32) {
    ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(canvasSize.width, y); ctx.stroke()
  }
  ctx.restore()
}

function point(event) {
  const rect = canvas.value.getBoundingClientRect()
  return { x: (event.clientX - rect.left) / scale.value, y: (event.clientY - rect.top) / scale.value }
}

function startPointer(event) {
  canvas.value.setPointerCapture(event.pointerId)
  drawing.value = true
  lastPoint.value = point(event)
}

function movePointer(event) {
  if (!drawing.value) return
  const current = point(event)
  const ctx = context()
  if (mode.value === 'pan') {
    offset.value = { x: offset.value.x + event.movementX, y: offset.value.y + event.movementY }
    canvas.value.style.transform = `translate(${offset.value.x}px, ${offset.value.y}px) scale(${scale.value})`
  } else {
    ctx.strokeStyle = strokeColor.value
    ctx.lineWidth = 3 / scale.value
    ctx.lineCap = 'round'
    ctx.beginPath(); ctx.moveTo(lastPoint.value.x, lastPoint.value.y); ctx.lineTo(current.x, current.y); ctx.stroke()
  }
  lastPoint.value = current
}

function endPointer() {
  drawing.value = false
  lastPoint.value = null
}

function zoomCanvas(event) {
  const nextScale = Math.min(2, Math.max(0.5, scale.value + (event.deltaY > 0 ? -0.1 : 0.1)))
  scale.value = Number(nextScale.toFixed(2))
  canvas.value.style.transform = `translate(${offset.value.x}px, ${offset.value.y}px) scale(${scale.value})`
}

async function addText() {
  try {
    const result = await ElMessageBox.prompt('输入要放置在画布上的文字', '添加文字', { inputPlaceholder: '例如：项目流程' })
    const ctx = context()
    ctx.fillStyle = strokeColor.value
    ctx.font = 'bold 24px Microsoft YaHei'
    ctx.fillText(result.value, 120, 120 + Math.random() * 300)
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('添加文字失败')
  }
}

function clearCanvas() {
  drawGrid()
}

function exportPng() {
  const link = document.createElement('a')
  link.download = 'docwork-canvas.png'
  link.href = canvas.value.toDataURL('image/png')
  link.click()
  ElMessage.success('画布已导出')
}

function insertIntoDocument() {
  emit('insert', canvas.value.toDataURL('image/png'))
  ElMessage.success('画布已插入文档')
}

onMounted(async () => {
  await nextTick()
  resizeCanvas()
  window.addEventListener('resize', resizeCanvas)
})
</script>

<style scoped>
.canvas-shell { border: 1px solid #dfe7ef; border-radius: 12px; overflow: hidden; background: #f5f8fb; }
.canvas-toolbar { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 12px; background: #fff; border-bottom: 1px solid #e7edf3; }
.canvas-tip { margin-left: auto; color: #718096; font-size: 12px; }
.canvas-viewport { height: 520px; overflow: auto; padding: 24px; background: #eef3f7; }
.canvas-viewport canvas { display: block; transform-origin: top left; box-shadow: 0 10px 30px rgba(35, 58, 78, .12); cursor: crosshair; }
@media (max-width: 700px) { .canvas-tip { width: 100%; margin-left: 0; } .canvas-viewport { height: 420px; padding: 12px; } }
</style>
