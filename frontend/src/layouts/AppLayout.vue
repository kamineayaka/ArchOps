<script setup lang="ts">
import { computed, h } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NSpace,
  NText,
} from 'naive-ui'
import {
  ChatbubbleEllipsesOutline,
  DesktopOutline,
  DocumentTextOutline,
  GridOutline,
  LogOutOutline,
  ServerOutline,
  SettingsOutline,
  ShieldCheckmarkOutline,
} from '@vicons/ionicons5'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const { t } = useI18n()
const authStore = useAuthStore()

const username = computed(() => authStore.user?.displayName || authStore.user?.username || '')

const isAdmin = computed(() => authStore.user?.roles?.includes('ROLE_ADMIN') ?? false)

const menuOptions = computed(() => {
  const items = [
    { label: t('nav.dashboard'), key: 'dashboard', icon: () => h(NIcon, null, { default: () => h(GridOutline) }) },
    { label: t('nav.assets'), key: 'assets', icon: () => h(NIcon, null, { default: () => h(ServerOutline) }) },
    { label: t('nav.ai'), key: 'ai', icon: () => h(NIcon, null, { default: () => h(ChatbubbleEllipsesOutline) }) },
    { label: t('nav.terminal'), key: 'terminal', icon: () => h(NIcon, null, { default: () => h(DesktopOutline) }) },
    { label: t('nav.approvals'), key: 'approvals', icon: () => h(NIcon, null, { default: () => h(ShieldCheckmarkOutline) }) },
    { label: t('nav.audit'), key: 'audit', icon: () => h(NIcon, null, { default: () => h(DocumentTextOutline) }) },
  ]
  if (isAdmin.value) {
    items.push({
      label: t('nav.aiSettings'),
      key: 'ai-settings',
      icon: () => h(NIcon, null, { default: () => h(SettingsOutline) }),
    })
  }
  return items
})

const activeKey = computed(() => {
  const name = route.name as string
  if (name === 'terminal') return 'terminal'
  if (name === 'ai-settings') return 'ai-settings'
  return name
})

function handleMenu(key: string) {
  router.push({ name: key })
}

async function handleLogout() {
  await authStore.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <NLayout class="app-layout" has-sider>
    <NLayoutSider bordered collapse-mode="width" :collapsed-width="64" :width="220" show-trigger>
      <div class="brand">{{ t('common.appName') }}</div>
      <NMenu :value="activeKey" :options="menuOptions" @update:value="handleMenu" />
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader class="header" bordered>
        <NSpace align="center" justify="space-between" style="width: 100%">
          <NText depth="3">{{ route.meta.title || '' }}</NText>
          <NSpace align="center">
            <NText>{{ username }}</NText>
            <NButton quaternary @click="handleLogout">
              <template #icon><NIcon :component="LogOutOutline" /></template>
              {{ t('common.logout') }}
            </NButton>
          </NSpace>
        </NSpace>
      </NLayoutHeader>
      <NLayoutContent class="content">
        <RouterView />
      </NLayoutContent>
    </NLayout>
  </NLayout>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.brand {
  padding: 20px 16px 12px;
  font-weight: 700;
  font-size: 15px;
  color: #0f766e;
}

.header {
  height: 56px;
  display: flex;
  align-items: center;
  padding: 0 20px;
}

.content {
  padding: 20px;
  min-height: calc(100vh - 56px);
}
</style>
