<template>
  <div class="login-container">
    <div class="ambient ambient-one"></div><div class="ambient ambient-two"></div>
    <section class="login-story">
      <span class="story-kicker">DOCWORK / PRIVATE KNOWLEDGE SPACE</span>
      <h1>让每一次记录，<em>都能继续生长。</em></h1>
      <p>文档、团队与 AI 汇聚在同一张工作台里，把零散灵感整理成可复用的知识。</p>
      <div class="story-stats"><span><strong>01</strong> 结构化沉淀</span><span><strong>02</strong> 协作式编辑</span><span><strong>03</strong> AI 辅助检索</span></div>
    </section>
    <el-card class="login-card">
      <div class="login-heading"><div class="login-mark">D</div><div><span>WELCOME BACK</span><h2>进入你的工作台</h2></div></div>
      <el-form :model="form" :rules="rules" ref="formRef" label-width="0">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" prefix-icon="User" size="large" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" prefix-icon="Lock" size="large" show-password />
        </el-form-item>
        <el-form-item>
          <el-button class="login-button" type="primary" size="large" @click="handleLogin" :loading="loading">进入工作台 <span>→</span></el-button>
        </el-form-item>
      </el-form>
      <div class="register-tip">
        <router-link to="/register">没有账号？去注册</router-link>
      </div>
    </el-card>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { ElMessage } from 'element-plus'

const router = useRouter()
const userStore = useUserStore()
const formRef = ref()
const loading = ref(false)

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  try {
    await formRef.value.validate()
  } catch (validateErr) {
    console.warn('[LOGIN] 表单校验失败', validateErr)
    return
  }
  loading.value = true
  console.log('[LOGIN] 准备发送登录请求', { username: form.username, password: '******' })
  try {
    const result = await userStore.login(form)
    console.log('[LOGIN] 登录成功，返回数据', result)
    ElMessage.success('登录成功')
    router.push('/workspace')
  } catch (e) {
    console.error('[LOGIN] 登录失败', e)
  } finally {
    loading.value = false
  }
}

</script>

<style scoped>
.login-container { position: relative; min-height: 100vh; display: flex; align-items: center; justify-content: center; gap: clamp(40px, 9vw, 150px); overflow: hidden; padding: 32px; background: #edf4f0; color: #173b3d; }
.ambient { position: absolute; border-radius: 50%; filter: blur(1px); opacity: .55; animation: drift 10s ease-in-out infinite; }
.ambient-one { width: 420px; height: 420px; top: -160px; left: -100px; background: #d3e8df; }
.ambient-two { width: 300px; height: 300px; right: -70px; bottom: -100px; background: #f4d9a4; animation-delay: -3s; }
.login-story { position: relative; z-index: 1; width: min(470px, 42vw); animation: reveal .65s ease both; }
.story-kicker, .login-heading span { color: #78968e; font-size: 10px; letter-spacing: 1.7px; }
.login-story h1 { margin: 18px 0; font: 600 clamp(35px, 4vw, 58px)/1.08 Georgia, serif; letter-spacing: 0; }
.login-story h1 em { color: #236d69; font-style: normal; }
.login-story p { max-width: 400px; color: #66827a; font-size: 16px; line-height: 1.8; }
.story-stats { display: flex; flex-wrap: wrap; gap: 18px; margin-top: 42px; color: #6e8981; font-size: 12px; }
.story-stats strong { margin-right: 5px; color: #d3933d; }
.login-card { position: relative; z-index: 1; width: 410px; padding: 28px; border: 0; border-radius: 18px; box-shadow: 0 24px 60px rgba(31, 78, 72, .14); animation: reveal .65s .1s ease both; }
.login-heading { display: flex; align-items: center; gap: 12px; margin-bottom: 28px; }
.login-heading h2 { margin: 5px 0 0; font: 600 24px Georgia, serif; }
.login-mark { display: grid; place-items: center; width: 42px; height: 42px; border-radius: 12px; background: #236d69; color: #f9dfad; font: 800 24px Georgia, serif; }
.login-button { width: 100%; height: 46px; border: 0; background: #236d69; }
.login-button span { margin-left: auto; font-size: 20px; }
.register-tip { color: #78918a; text-align: center; font-size: 13px; }
.register-tip a { color: #236d69; font-weight: 700; text-decoration: none; }
@keyframes reveal { from { opacity: 0; transform: translateY(16px); } to { opacity: 1; transform: translateY(0); } }
@keyframes drift { 50% { transform: translate(24px, 18px) scale(1.05); } }
@media (max-width: 820px) { .login-container { align-items: flex-start; padding-top: 10vh; } .login-story { display: none; } .login-card { width: min(410px, 100%); } }
</style>
