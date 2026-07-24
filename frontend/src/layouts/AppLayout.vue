<script setup lang="ts">
import { computed, h, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NAvatar,
  NButton,
  NDrawer,
  NDrawerContent,
  NIcon,
  NLayout,
  NLayoutContent,
  NLayoutHeader,
  NLayoutSider,
  NMenu,
  NSelect,
  NSpace,
  NText,
  NTooltip,
} from 'naive-ui'
import {
  ChatbubbleEllipsesOutline,
  DesktopOutline,
  DocumentTextOutline,
  FolderOutline,
  GitPullRequestOutline,
  GridOutline,
  LibraryOutline,
  LogOutOutline,
  MoonOutline,
  ServerOutline,
  SettingsOutline,
  ShieldCheckmarkOutline,
  SunnyOutline,
} from '@vicons/ionicons5'
import AiAssistantRail from '@/components/AiAssistantRail.vue'
import AssetNavTree from '@/components/AssetNavTree.vue'
import { useAiWorkbenchShell } from '@/composables/useAiWorkbenchShell'
import { useAuthStore } from '@/stores/auth'
import { useTheme } from '@/composables/useTheme'
import { isAdmin as roleIsAdmin } from '@/utils/roles'
import { setAppLocale } from '@/i18n'

const route = useRoute()
const router = useRouter()
const { t, locale } = useI18n()
const authStore = useAuthStore()
const { isDark, toggle } = useTheme()
const {
  pinned,
  open,
  railAllowed,
  showDesktopRail,
  showMobileRailDrawer,
  showDesktopAssetTree,
  showMobileAssetTreeDrawer,
  toggleOpen,
  setOpen,
  toggleAssetTree,
  setAssetTreeOpen,
} = useAiWorkbenchShell()

const localeOptions = computed(() => [
  { label: t('common.localeZh'), value: 'zh-CN' },
  { label: t('common.localeEn'), value: 'en-US' },
])

const currentLocale = ref(locale.value)

function handleLocaleChange(value: 'zh-CN' | 'en-US') {
  setAppLocale(value)
  currentLocale.value = value
}

const username = computed(() => authStore.user?.displayName || authStore.user?.username || '')
const userInitial = computed(() => (username.value ? username.value.charAt(0).toUpperCase() : '?'))

const isAdmin = computed(() => roleIsAdmin(authStore.user?.roles))

const pageTitle = computed(() => {
  const key = route.meta.titleKey as string | undefined
  return key ? t(key) : ''
})

const menuOptions = computed(() => {
  const items = [
    { label: t('nav.dashboard'), key: 'dashboard', icon: () => h(NIcon, null, { default: () => h(GridOutline) }) },
    { label: t('nav.assets'), key: 'assets', icon: () => h(NIcon, null, { default: () => h(ServerOutline) }) },
    { label: t('nav.assetGroups'), key: 'asset-groups', icon: () => h(NIcon, null, { default: () => h(FolderOutline) }) },
    { label: t('nav.architecture'), key: 'architecture', icon: () => h(NIcon, null, { default: () => h(LibraryOutline) }) },
    {
      label: t('nav.proposals'),
      key: 'architecture-proposals',
      icon: () => h(NIcon, null, { default: () => h(GitPullRequestOutline) }),
    },
    { label: t('nav.terminal'), key: 'terminal', icon: () => h(NIcon, null, { default: () => h(DesktopOutline) }) },
    { label: t('nav.ai'), key: 'ai', icon: () => h(NIcon, null, { default: () => h(ChatbubbleEllipsesOutline) }) },
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
  if (name === 'ai' || name === 'agent') return 'ai'
  if (name === 'ai-settings') return 'ai-settings'
  return name
})

const railSiderWidth = computed(() => (open.value ? 360 : 48))

function handleMenu(key: string) {
  router.push({ name: key })
}

async function handleLogout() {
  await authStore.logout()
  await router.push({ name: 'login' })
}
</script>

<template>
  <a href="#main-content" class="skip-link">{{ t('common.skipToContent') }}</a>
  <NLayout class="app-layout" has-sider>
    <NLayoutSider
      class="app-sider"
      bordered
      collapse-mode="width"
      :collapsed-width="64"
      :width="232"
      show-trigger
    >
      <div class="brand">
        <div class="brand__mark" aria-hidden="true">CO</div>
        <div class="brand__text">
          <span class="brand__name">{{ t('common.appName') }}</span>
          <span class="brand__tag">{{ t('common.appTagline') }}</span>
        </div>
      </div>
      <nav :aria-label="t('common.mainNav')">
        <NMenu :value="activeKey" :options="menuOptions" @update:value="handleMenu" />
      </nav>
    </NLayoutSider>
    <NLayout>
      <NLayoutHeader class="header" bordered>
        <div class="header__inner">
          <div class="header__title">
            <h2 class="header__page-title">{{ pageTitle }}</h2>
          </div>
          <NSpace align="center" :size="12">
            <template v-if="railAllowed">
              <NTooltip :show-arrow="false">
                <template #trigger>
                  <NButton
                    quaternary
                    :type="showDesktopAssetTree || showMobileAssetTreeDrawer ? 'primary' : 'default'"
                    :aria-label="t('workbench.toggleAssetTree')"
                    @click="toggleAssetTree"
                  >
                    <template #icon><NIcon :component="FolderOutline" /></template>
                    <span class="header-action-label">{{ t('workbench.assetTree') }}</span>
                  </NButton>
                </template>
                {{ t('workbench.toggleAssetTree') }}
              </NTooltip>
              <NTooltip :show-arrow="false">
                <template #trigger>
                  <NButton
                    quaternary
                    :type="open || pinned ? 'primary' : 'default'"
                    :aria-label="t('workbench.toggleAiRail')"
                    @click="toggleOpen"
                  >
                    <template #icon><NIcon :component="ChatbubbleEllipsesOutline" /></template>
                    <span class="header-action-label">{{ t('workbench.aiRailTitle') }}</span>
                  </NButton>
                </template>
                {{ t('workbench.toggleAiRail') }}
              </NTooltip>
            </template>
            <NTooltip :show-arrow="false">
              <template #trigger>
                <NButton quaternary circle :aria-label="isDark ? t('common.lightMode') : t('common.darkMode')" @click="toggle">
                  <template #icon>
                    <NIcon :component="isDark ? SunnyOutline : MoonOutline" />
                  </template>
                </NButton>
              </template>
              {{ isDark ? t('common.lightMode') : t('common.darkMode') }}
            </NTooltip>
            <NSelect
              v-model:value="currentLocale"
              :options="localeOptions"
              size="small"
              class="locale-select"
              :consistent-menu-width="false"
              :aria-label="t('common.language')"
              @update:value="handleLocaleChange"
            />
            <NSpace align="center" :size="8" class="user-area">
              <NAvatar round size="small" class="user-avatar">{{ userInitial }}</NAvatar>
              <NText class="user-name">{{ username }}</NText>
            </NSpace>
            <NButton quaternary @click="handleLogout">
              <template #icon><NIcon :component="LogOutOutline" /></template>
              {{ t('common.logout') }}
            </NButton>
          </NSpace>
        </div>
      </NLayoutHeader>
      <NLayout has-sider class="workbench">
        <NLayoutSider
          v-if="showDesktopAssetTree"
          class="asset-tree-sider"
          bordered
          :width="248"
          :native-scrollbar="false"
        >
          <AssetNavTree />
        </NLayoutSider>
        <NLayout has-sider class="workbench__main">
          <NLayoutContent id="main-content" class="content" tag="main">
            <div class="page-shell">
              <RouterView />
            </div>
          </NLayoutContent>
          <NLayoutSider
            v-if="showDesktopRail"
            class="ai-rail-sider"
            bordered
            :width="railSiderWidth"
            :collapsed="!open"
            :collapsed-width="48"
            collapse-mode="width"
            :native-scrollbar="false"
          >
            <div v-if="!open" class="ai-rail-collapsed">
              <NTooltip :show-arrow="false" placement="left">
                <template #trigger>
                  <NButton
                    quaternary
                    circle
                    :aria-label="t('workbench.expandRail')"
                    @click="setOpen(true)"
                  >
                    <template #icon><NIcon :component="ChatbubbleEllipsesOutline" /></template>
                  </NButton>
                </template>
                {{ t('workbench.expandRail') }}
              </NTooltip>
            </div>
            <AiAssistantRail v-else mode="sider" />
          </NLayoutSider>
        </NLayout>
      </NLayout>
    </NLayout>
  </NLayout>

  <NDrawer
    :show="showMobileAssetTreeDrawer"
    placement="left"
    :width="300"
    display-directive="show"
    @update:show="setAssetTreeOpen"
  >
    <NDrawerContent :title="t('workbench.assetTree')" closable>
      <AssetNavTree />
    </NDrawerContent>
  </NDrawer>

  <NDrawer
    :show="showMobileRailDrawer"
    placement="right"
    :width="360"
    display-directive="show"
    @update:show="setOpen"
  >
    <NDrawerContent :title="t('workbench.aiRailTitle')" closable :native-scrollbar="false" body-content-style="padding: 0; height: 100%;">
      <AiAssistantRail mode="drawer" />
    </NDrawerContent>
  </NDrawer>
</template>

<style scoped>
.app-layout {
  min-height: 100vh;
}

.app-sider :deep(.n-layout-sider-scroll-container) {
  display: flex;
  flex-direction: column;
}

.brand {
  display: flex;
  align-items: center;
  gap: var(--co-space-3);
  padding: var(--co-space-5) var(--co-space-4) var(--co-space-4);
  border-bottom: 1px solid var(--co-border);
}

.brand__mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: var(--co-radius);
  background: var(--co-primary);
  color: #fff;
  font-size: 0.75rem;
  font-weight: 700;
  letter-spacing: 0.02em;
  flex-shrink: 0;
}

.brand__text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.brand__name {
  font-weight: 600;
  font-size: 0.875rem;
  color: var(--co-text);
  line-height: 1.3;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.brand__tag {
  font-size: 0.6875rem;
  color: var(--co-text-muted);
  line-height: 1.3;
}

.header {
  height: var(--co-header-height);
  display: flex;
  align-items: center;
  padding: 0 var(--co-space-6);
  background: var(--co-bg-card);
}

.header__inner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  gap: var(--co-space-4);
}

.header__page-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--co-text);
}

.user-name {
  font-size: 0.875rem;
}

.user-avatar {
  background: var(--co-primary) !important;
  color: #fff !important;
  font-size: 0.75rem;
  font-weight: 600;
}

.locale-select {
  width: 112px;
}

.workbench {
  min-height: calc(100vh - var(--co-header-height));
}

.workbench__main {
  min-height: calc(100vh - var(--co-header-height));
  flex: 1;
  min-width: 0;
}

.content {
  padding: var(--co-space-6);
  min-height: calc(100vh - var(--co-header-height));
  background: var(--co-bg-page);
}

.asset-tree-sider,
.ai-rail-sider {
  background: var(--co-bg-card);
}

.asset-tree-sider :deep(.n-layout-sider-scroll-container),
.ai-rail-sider :deep(.n-layout-sider-scroll-container) {
  height: 100%;
}

.ai-rail-collapsed {
  display: flex;
  justify-content: center;
  padding-top: var(--co-space-4);
}

@media (max-width: 900px) {
  .header-action-label {
    display: none;
  }
}

@media (max-width: 768px) {
  .user-name {
    display: none;
  }

  .header {
    padding: 0 var(--co-space-4);
  }

  .content {
    padding: var(--co-space-4);
  }
}
</style>
