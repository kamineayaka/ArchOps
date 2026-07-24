<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  ChatbubbleEllipsesOutline,
  DesktopOutline,
  DocumentTextOutline,
  ServerOutline,
  ShieldCheckmarkOutline,
} from '@vicons/ionicons5'
import {
  NAlert,
  NButton,
  NCard,
  NDescriptions,
  NDescriptionsItem,
  NGrid,
  NGridItem,
  NIcon,
  NSpace,
  NTag,
} from 'naive-ui'
import { listAllProviders } from '@/api/ai-providers'
import AiProviderSetupWizard from '@/components/ai/AiProviderSetupWizard.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { formatRole } from '@/utils/format'
import { isAdmin as roleIsAdmin } from '@/utils/roles'

const { t } = useI18n()
const router = useRouter()
const authStore = useAuthStore()
const user = computed(() => authStore.user)
const isAdmin = computed(() => roleIsAdmin(authStore.user?.roles))
const showNoProviderBanner = ref(false)
const showWizard = ref(false)

const modules = computed(() => [
  { key: 'assets', label: t('dashboard.modules.assets'), icon: ServerOutline, route: 'assets' },
  { key: 'ai', label: t('dashboard.modules.ai'), icon: ChatbubbleEllipsesOutline, route: 'ai' },
  { key: 'terminal', label: t('dashboard.modules.terminal'), icon: DesktopOutline, route: 'terminal' },
  { key: 'approvals', label: t('dashboard.modules.approvals'), icon: ShieldCheckmarkOutline, route: 'approvals' },
  { key: 'audit', label: t('dashboard.modules.audit'), icon: DocumentTextOutline, route: 'audit' },
])

function goTo(name: string) {
  router.push({ name })
}

async function refreshProviderBanner() {
  if (!isAdmin.value) {
    showNoProviderBanner.value = false
    return
  }
  try {
    const res = await listAllProviders()
    showNoProviderBanner.value = Boolean(res.success && Array.isArray(res.data) && res.data.length === 0)
  } catch {
    // Banner is optional; ignore probe failures
  }
}

async function onWizardCompleted() {
  showWizard.value = false
  await refreshProviderBanner()
}

onMounted(async () => {
  await refreshProviderBanner()
  if (showNoProviderBanner.value) {
    showWizard.value = true
  }
})
</script>

<template>
  <NSpace vertical :size="24">
    <PageHeader :title="t('dashboard.title')" :description="t('dashboard.description')" />

    <NAlert
      v-if="showNoProviderBanner"
      type="info"
      :title="t('dashboard.noAiProviderTitle')"
    >
      <NSpace vertical :size="8">
        <span>{{ t('dashboard.noAiProviderDesc') }}</span>
        <div>
          <NButton size="small" type="primary" @click="showWizard = true">
            {{ t('aiSettings.startWizard') }}
          </NButton>
          <NButton size="small" quaternary @click="goTo('ai-settings')">
            {{ t('dashboard.goAiSettings') }}
          </NButton>
        </div>
      </NSpace>
    </NAlert>

    <AiProviderSetupWizard
      v-model:show="showWizard"
      @completed="onWizardCompleted"
      @changed="refreshProviderBanner"
    />

    <NGrid cols="1 s:2" :x-gap="16" :y-gap="16" responsive="screen">
      <NGridItem>
        <NCard class="page-card" :title="t('dashboard.profile')" :bordered="false">
          <NDescriptions v-if="user" :column="1" label-placement="left">
            <NDescriptionsItem :label="t('common.username')">{{ user.username }}</NDescriptionsItem>
            <NDescriptionsItem :label="t('dashboard.rbacTier')">
              <NTag type="info" size="small" round>{{ user.rbacTier }}</NTag>
            </NDescriptionsItem>
            <NDescriptionsItem :label="t('dashboard.approvalPolicy')">
              <NTag type="warning" size="small" round>{{ user.approvalPolicy }}</NTag>
            </NDescriptionsItem>
            <NDescriptionsItem :label="t('dashboard.roles')">
              <NSpace :size="8">
                <NTag v-for="role in user.roles" :key="role" type="success" size="small" round>
                  {{ formatRole(role) }}
                </NTag>
              </NSpace>
            </NDescriptionsItem>
          </NDescriptions>
        </NCard>
      </NGridItem>

      <NGridItem>
        <NCard class="page-card" :title="t('dashboard.quickActions')" :bordered="false">
          <NGrid cols="2" :x-gap="12" :y-gap="12">
            <NGridItem v-for="mod in modules" :key="mod.key">
              <button type="button" class="module-card" @click="goTo(mod.route)">
                <NIcon :component="mod.icon" :size="22" class="module-card__icon" />
                <span class="module-card__label">{{ mod.label }}</span>
                <span class="module-card__action">{{ t('dashboard.goTo') }} →</span>
              </button>
            </NGridItem>
          </NGrid>
        </NCard>
      </NGridItem>
    </NGrid>
  </NSpace>
</template>

<style scoped>
.module-card {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: var(--co-space-2);
  width: 100%;
  padding: var(--co-space-4);
  border: 1px solid var(--co-border);
  border-radius: var(--co-radius);
  background: var(--co-bg-page);
  color: var(--co-text);
  cursor: pointer;
  text-align: left;
  transition: border-color 0.15s ease, box-shadow 0.15s ease, transform 0.15s ease;
}

.module-card:hover {
  border-color: var(--co-primary);
  box-shadow: var(--co-shadow);
  transform: translateY(-1px);
}

.module-card:focus-visible {
  outline: 2px solid var(--co-primary);
  outline-offset: 2px;
}

.module-card__icon {
  color: var(--co-primary);
}

.module-card__label {
  font-size: 0.875rem;
  font-weight: 600;
}

.module-card__action {
  font-size: 0.75rem;
  color: var(--co-text-muted);
}
</style>
