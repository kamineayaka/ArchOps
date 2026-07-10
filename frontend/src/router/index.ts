import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/',
      component: () => import('@/layouts/AppLayout.vue'),
      children: [
        { path: '', name: 'dashboard', component: () => import('@/views/DashboardView.vue') },
        { path: 'assets', name: 'assets', component: () => import('@/views/AssetsView.vue') },
        { path: 'ai', name: 'ai', component: () => import('@/views/AiChatView.vue') },
        {
          path: 'settings/ai',
          name: 'ai-settings',
          component: () => import('@/views/AiProvidersView.vue'),
          meta: { requiresAdmin: true },
        },
        { path: 'terminal/:assetId?', name: 'terminal', component: () => import('@/views/TerminalView.vue') },
        { path: 'approvals', name: 'approvals', component: () => import('@/views/ApprovalsView.vue') },
        { path: 'audit', name: 'audit', component: () => import('@/views/AuditView.vue') },
      ],
    },
    { path: '/:pathMatch(.*)*', redirect: '/' },
  ],
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const hasToken = authStore.isAuthenticated()

  if (to.meta.public) {
    if (hasToken && to.name === 'login') return { name: 'dashboard' }
    return true
  }

  if (!hasToken) return { name: 'login' }

  if (!authStore.user) {
    try {
      await authStore.fetchMe()
    } catch {
      authStore.clearSession()
      return { name: 'login' }
    }
  }
  if (to.meta.requiresAdmin && !authStore.user?.roles?.includes('ROLE_ADMIN')) {
    return { name: 'dashboard' }
  }
  return true
})

export default router
