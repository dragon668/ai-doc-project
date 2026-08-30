import { createRouter, createWebHistory } from 'vue-router'
import { useUserStore } from '@/stores/user'

const routes = [
  {
    path: '/login',
    name: 'Login',
    component: () => import('@/views/login/index.vue')
  },
  {
    path: '/register',
    name: 'Register',
    component: () => import('@/views/register/index.vue')
  },
  {
    path: '/share/:code',
    name: 'ShareView',
    component: () => import('@/views/share/view.vue')
  },
  {
    path: '/',
    component: () => import('@/layouts/MainLayout.vue'),
    redirect: '/workspace',
    meta: { requiresAuth: true },
    children: [
      {
        path: 'workspace',
        name: 'Workspace',
        component: () => import('@/views/workspace/index.vue')
      },
      {
        path: 'workspace/:id',
        name: 'WorkspaceDetail',
        component: () => import('@/views/workspace/detail.vue')
      },
      {
        path: 'document/:id',
        name: 'DocumentView',
        component: () => import('@/views/document/view.vue')
      },
      {
        path: 'ai/:workspaceId',
        name: 'AiChat',
        component: () => import('@/views/ai/chat.vue')
      },
      {
        path: 'ai-config',
        name: 'AiConfig',
        component: () => import('@/views/ai/config.vue')
      },
      {
        path: 'friend',
        name: 'Friend',
        component: () => import('@/views/friend/index.vue')
      },
      {
        path: 'dashboard',
        name: 'Dashboard',
        component: () => import('@/views/dashboard/index.vue')
      }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

// 路由守卫
router.beforeEach((to, from, next) => {
  const userStore = useUserStore()
  if (to.meta.requiresAuth && !userStore.token) {
    next('/login')
  } else {
    next()
  }
})

export default router
