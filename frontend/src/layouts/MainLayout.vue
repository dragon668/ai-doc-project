<template>
  <el-container class="app-shell">
    <el-aside width="236px" class="app-sidebar">
      <div class="brand-lockup">
        <div class="brand-mark">D</div>
        <div>
          <strong>Docwork</strong>
          <span>AI 文档协作</span>
        </div>
      </div>
      <el-menu
        class="app-menu"
        :default-active="$route.path"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
        router
      >
        <el-menu-item index="/workspace">
          <el-icon><Folder /></el-icon>
          <span>我的工作空间</span>
        </el-menu-item>
        <el-menu-item index="/friend">
          <el-icon><User /></el-icon>
          <span>好友列表</span>
        </el-menu-item>
        <el-menu-item index="/ai-config">
          <el-icon><Setting /></el-icon>
          <span>AI 接口</span>
        </el-menu-item>
        <el-menu-item index="/dashboard">
          <el-icon><DataAnalysis /></el-icon>
          <span>统计看板</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="content-shell">
      <el-header class="app-header">
        <div class="header-caption">
          <span class="eyebrow">WORKSPACE</span>
          <strong>让知识开始流动</strong>
        </div>
        <el-dropdown @command="handleCommand">
          <span class="profile-trigger">
            <el-avatar :size="32">{{ userStore.userInfo.nickname?.charAt(0) || 'U' }}</el-avatar>
            <span>{{ userStore.userInfo.nickname || userStore.userInfo.username }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item command="logout">退出登录</el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </el-header>

      <el-main class="app-main">
        <router-view v-slot="{ Component, route }">
          <transition name="page-rise" mode="out-in">
            <component :is="Component" :key="route.fullPath" />
          </transition>
        </router-view>
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { useUserStore } from '@/stores/user'
import { useRouter } from 'vue-router'

const userStore = useUserStore()
const router = useRouter()

function handleCommand(command) {
  if (command === 'logout') {
    userStore.logout()
    router.push('/login')
  }
}
</script>

<style scoped>
.app-shell { height: 100vh; background: #f4f7f6; }
.app-sidebar { background: #142f35; color: #d9e9e4; padding: 18px 12px; }
.brand-lockup { display: flex; align-items: center; gap: 10px; padding: 12px 10px 28px; }
.brand-mark { width: 36px; height: 36px; display: grid; place-items: center; border-radius: 10px; background: #e7b86b; color: #142f35; font: 800 20px Georgia, serif; }
.brand-lockup strong, .brand-lockup span { display: block; }
.brand-lockup strong { color: #fff; font: 700 19px Georgia, serif; letter-spacing: .4px; }
.brand-lockup span { margin-top: 3px; color: #9db8b1; font-size: 11px; }
.app-menu { border-right: 0; background: transparent; }
.app-menu :deep(.el-menu-item) { margin: 5px 0; border-radius: 9px; color: #a9c2bc; transition: background .2s ease, color .2s ease, transform .2s ease; }
.app-menu :deep(.el-menu-item:hover) { background: rgba(255,255,255,.08); color: #fff; transform: translateX(3px); }
.app-menu :deep(.el-menu-item.is-active) { background: #e7b86b; color: #142f35; font-weight: 700; }
.content-shell { min-width: 0; }
.app-header { height: 68px; display: flex; align-items: center; justify-content: space-between; padding: 0 30px; background: rgba(255,255,255,.88); border-bottom: 1px solid #e6ece9; }
.header-caption strong, .header-caption .eyebrow { display: block; }
.header-caption strong { color: #173b3d; font: 600 17px Georgia, serif; }
.eyebrow { margin-bottom: 3px; color: #879d98; font-size: 10px; letter-spacing: 1.5px; }
.profile-trigger { display: flex; align-items: center; gap: 9px; cursor: pointer; color: #294b4b; font-weight: 600; }
.app-main { background: #f4f7f6; overflow-y: auto; padding: 28px 30px; }
.page-rise-enter-active, .page-rise-leave-active { transition: opacity .22s ease, transform .22s ease; }
.page-rise-enter-from { opacity: 0; transform: translateY(8px); }
.page-rise-leave-to { opacity: 0; transform: translateY(-5px); }
@media (max-width: 760px) {
  .app-sidebar { width: 68px !important; padding: 12px 8px; }
  .brand-lockup { justify-content: center; padding: 8px 0 22px; }
  .brand-lockup > div:last-child, .app-menu :deep(.el-menu-item span) { display: none; }
  .app-menu :deep(.el-menu-item) { justify-content: center; padding: 0 !important; }
  .app-header { padding: 0 16px; }
  .header-caption { display: none; }
  .app-main { padding: 18px 14px; }
}
</style>
