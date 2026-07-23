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
        {
          path: '',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { titleKey: 'nav.dashboard', descKey: 'dashboard.description' },
        },
        {
          path: 'assets',
          name: 'assets',
          component: () => import('@/views/AssetsView.vue'),
          meta: { titleKey: 'nav.assets', descKey: 'assets.subtitle' },
        },
        {
          path: 'asset-groups',
          name: 'asset-groups',
          component: () => import('@/views/AssetGroupsView.vue'),
          meta: { titleKey: 'nav.assetGroups', descKey: 'assetGroups.subtitle' },
        },
        {
          path: 'ai',
          name: 'ai',
          component: () => import('@/views/AiChatView.vue'),
          meta: { titleKey: 'nav.ai', descKey: 'ai.subtitle' },
        },
        {
          path: 'settings/ai',
          name: 'ai-settings',
          component: () => import('@/views/AiProvidersView.vue'),
          meta: { requiresAdmin: true, titleKey: 'nav.aiSettings', descKey: 'aiSettings.subtitle' },
        },
        {
          path: 'terminal/:assetId?',
          name: 'terminal',
          component: () => import('@/views/TerminalView.vue'),
          meta: { titleKey: 'nav.terminal', descKey: 'terminal.subtitle' },
        },
        {
          path: 'approvals',
          name: 'approvals',
          component: () => import('@/views/ApprovalsView.vue'),
          meta: { titleKey: 'nav.approvals', descKey: 'approvals.subtitle' },
        },
        {
          path: 'audit',
          name: 'audit',
          component: () => import('@/views/AuditView.vue'),
          meta: { titleKey: 'nav.audit', descKey: 'audit.subtitle' },
        },
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
