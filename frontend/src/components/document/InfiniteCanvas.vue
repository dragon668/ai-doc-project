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
      <el-button @click="exportPng">导出内容 PNG</el-button>
      <el-button @click="exportCustomCrop">自定义裁剪</el-button>
      <el-button @click="exportSvg">导出 SVG</el-button>
      <el-button type="primary" @click="insertIntoDocument">插入文档</el-button>
      <span class="canvas-tip">画布可持续平移 · 滚轮缩放 · 导出默认裁剪内容</span>
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
const canvasSize = { width: 4000, height: 2400 }
const strokes = ref([])
const textItems = ref([])

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
  drawContent()
}

function drawContent() {
  const ctx = context()
  ctx.save()
  for (const stroke of strokes.value) {
    ctx.strokeStyle = stroke.color
    ctx.lineWidth = stroke.width
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    stroke.points.forEach((item, index) => {
      if (index === 0) ctx.moveTo(item.x, item.y)
      else ctx.lineTo(item.x, item.y)
    })
    ctx.stroke()
  }
  for (const item of textItems.value) {
    ctx.fillStyle = item.color
    ctx.font = `${item.size}px Microsoft YaHei`
    ctx.fillText(item.text, item.x, item.y)
  }
  ctx.restore()
}

function point(event) {
  const rect = canvas.value.getBoundingClientRect()
  return { x: (event.clientX - rect.left) / scale.value, y: (event.clientY - rect.top) / scale.value }
}

function ensureCanvasSpace(item) {
  const edge = 240
  let expanded = false
  if (item.x > canvasSize.width - edge) {
    canvasSize.width += 2000
    expanded = true
  }
  if (item.y > canvasSize.height - edge) {
    canvasSize.height += 1200
    expanded = true
  }
  if (expanded) resizeCanvas()
  return expanded
}

function startPointer(event) {
  canvas.value.setPointerCapture(event.pointerId)
  drawing.value = true
  const initialPoint = point(event)
  if (mode.value === 'draw' && ensureCanvasSpace(initialPoint)) lastPoint.value = point(event)
  else lastPoint.value = initialPoint
  if (mode.value === 'draw') {
    strokes.value.push({ color: strokeColor.value, width: 3 / scale.value, points: [lastPoint.value] })
  }
}

function movePointer(event) {
  if (!drawing.value) return
  let current = point(event)
  if (mode.value === 'draw' && ensureCanvasSpace(current)) current = point(event)
  if (mode.value === 'pan') {
    offset.value = { x: offset.value.x + event.movementX, y: offset.value.y + event.movementY }
    canvas.value.style.transform = `translate(${offset.value.x}px, ${offset.value.y}px) scale(${scale.value})`
  } else {
    const stroke = strokes.value[strokes.value.length - 1]
    stroke.points.push(current)
    drawGrid()
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
    textItems.value.push({ text: result.value, color: strokeColor.value, size: 24, x: 120, y: 120 + textItems.value.length * 42 })
    drawGrid()
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('添加文字失败')
  }
}

function clearCanvas() {
  strokes.value = []
  textItems.value = []
  drawGrid()
}

function contentBounds() {
  const points = strokes.value.flatMap(stroke => stroke.points)
  const textBounds = textItems.value.map(item => ({ x: item.x, y: item.y - item.size, width: item.text.length * item.size, height: item.size }))
  const all = [
    ...points.map(item => ({ x: item.x, y: item.y, width: 0, height: 0 })),
    ...textBounds
  ]
  if (!all.length) return { x: 0, y: 0, width: 1, height: 1 }
  const padding = 24
  const left = Math.max(0, Math.min(...all.map(item => item.x)) - padding)
  const top = Math.max(0, Math.min(...all.map(item => item.y)) - padding)
  const right = Math.min(canvasSize.width, Math.max(...all.map(item => item.x + item.width)) + padding)
  const bottom = Math.min(canvasSize.height, Math.max(...all.map(item => item.y + item.height)) + padding)
  return { x: left, y: top, width: Math.max(1, right - left), height: Math.max(1, bottom - top) }
}

function renderCrop(bounds, mime = 'image/png') {
  const output = document.createElement('canvas')
  output.width = Math.ceil(bounds.width)
  output.height = Math.ceil(bounds.height)
  const ctx = output.getContext('2d')
  ctx.fillStyle = '#ffffff'
  ctx.fillRect(0, 0, output.width, output.height)
  ctx.translate(-bounds.x, -bounds.y)
  for (const stroke of strokes.value) {
    ctx.strokeStyle = stroke.color
    ctx.lineWidth = stroke.width
    ctx.lineCap = 'round'
    ctx.lineJoin = 'round'
    ctx.beginPath()
    stroke.points.forEach((item, index) => index ? ctx.lineTo(item.x, item.y) : ctx.moveTo(item.x, item.y))
    ctx.stroke()
  }
  for (const item of textItems.value) {
    ctx.fillStyle = item.color
    ctx.font = `${item.size}px Microsoft YaHei`
    ctx.fillText(item.text, item.x, item.y)
  }
  return output.toDataURL(mime)
}

function download(dataUrl, filename) {
  const link = document.createElement('a')
  link.download = filename
  link.href = dataUrl
  link.click()
}

function exportPng() {
  download(renderCrop(contentBounds()), 'docwork-canvas-content.png')
  ElMessage.success('已按内容范围导出 PNG')
}

async function exportCustomCrop() {
  try {
    const result = await ElMessageBox.prompt('输入裁剪范围：x,y,width,height（画布坐标）', '自定义裁剪', {
      inputValue: Object.values(contentBounds()).join(','),
      inputPlaceholder: '例如：100,100,800,500'
    })
    const values = result.value.split(',').map(Number)
    if (values.length !== 4 || values.some(value => !Number.isFinite(value) || value <= 0)) throw new Error('invalid')
    const [x, y, width, height] = values
    download(renderCrop({ x, y, width, height }), 'docwork-canvas-crop.png')
    ElMessage.success('已导出自定义裁剪区域')
  } catch (error) {
    if (error !== 'cancel') ElMessage.error('裁剪范围格式应为 x,y,width,height')
  }
}

function svgDataUrl(bounds = contentBounds()) {
  const lines = strokes.value.map(stroke => `<polyline points="${stroke.points.map(item => `${item.x},${item.y}`).join(' ')}" fill="none" stroke="${stroke.color}" stroke-width="${stroke.width}" stroke-linecap="round" stroke-linejoin="round"/>`).join('')
  const labels = textItems.value.map(item => `<text x="${item.x}" y="${item.y}" fill="${item.color}" font-size="${item.size}" font-family="Microsoft YaHei">${escapeXml(item.text)}</text>`).join('')
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${bounds.width}" height="${bounds.height}" viewBox="${bounds.x} ${bounds.y} ${bounds.width} ${bounds.height}"><rect width="100%" height="100%" fill="white"/>${lines}${labels}</svg>`
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`
}

function escapeXml(value) {
  return value.replace(/[<>&'"]/g, char => ({ '<': '&lt;', '>': '&gt;', '&': '&amp;', "'": '&apos;', '"': '&quot;' })[char])
}

function exportSvg() {
  download(svgDataUrl(), 'docwork-canvas-content.svg')
  ElMessage.success('已按内容范围导出 SVG')
}

function insertIntoDocument() {
  emit('insert', svgDataUrl())
  ElMessage.success('已插入矢量画布内容')
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
