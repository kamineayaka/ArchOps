import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { isAdmin } from '@/utils/roles'

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
          name: 'topology',
          component: () => import('@/views/TopologyView.vue'),
          meta: { titleKey: 'nav.topology', descKey: 'topology.subtitle' },
        },
        {
          path: 'topology',
          redirect: { name: 'topology' },
        },
        {
          path: 'dashboard',
          redirect: { name: 'topology' },
        },
        {
          path: 'graph',
          name: 'graph',
          component: () => import('@/views/GraphWorkbenchView.vue'),
          meta: { titleKey: 'nav.graphEditor', descKey: 'graph.subtitle' },
        },
        {
          path: 'assets',
          redirect: { name: 'topology' },
        },
        {
          path: 'asset-groups',
          redirect: { name: 'topology' },
        },
        {
          path: 'architecture',
          name: 'architecture',
          component: () => import('@/views/ArchitectureView.vue'),
          meta: { titleKey: 'nav.architecture', descKey: 'architecture.subtitle' },
        },
        {
          path: 'architecture-proposals',
          name: 'architecture-proposals',
          component: () => import('@/views/ArchitectureProposalsView.vue'),
          meta: { titleKey: 'nav.proposals', descKey: 'proposals.subtitle' },
        },
        {
          path: 'ai',
          name: 'ai',
          component: () => import('@/views/AiChatView.vue'),
          meta: { titleKey: 'nav.ai', descKey: 'ai.subtitle' },
        },
        {
          path: 'agent',
          redirect: { name: 'ai' },
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
          path: 'query/:assetId?',
          name: 'query',
          component: () => import('@/views/QueryView.vue'),
          meta: { titleKey: 'nav.query', descKey: 'query.subtitle' },
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
    if (hasToken && to.name === 'login') return { name: 'topology' }
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
  if (to.meta.requiresAdmin && !isAdmin(authStore.user?.roles)) {
    return { name: 'topology' }
  }
  return true
})

export default router
