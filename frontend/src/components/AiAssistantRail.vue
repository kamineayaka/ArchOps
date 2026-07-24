<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NIcon, NInput, NSelect, NSpin, NTooltip, useMessage } from 'naive-ui'
import {
  CloseOutline,
  OpenOutline,
  PinOutline,
  RefreshOutline,
} from '@vicons/ionicons5'
import { createConversation, getMessages, type ChatMessage } from '@/api/ai'
import { createAiStreamClient, type AiStreamEvent, type UiContext } from '@/api/aiStream'
import { listChatProviders, type AiProvider } from '@/api/ai-providers'
import AiProviderSetupWizard from '@/components/ai/AiProviderSetupWizard.vue'
import { useAiWorkbenchShell } from '@/composables/useAiWorkbenchShell'
import { renderChatContent } from '@/utils/format'
import { useAuthStore } from '@/stores/auth'
import { isAdmin as roleIsAdmin } from '@/utils/roles'

interface DisplayMessage extends ChatMessage {
  streaming?: boolean
}

const props = withDefaults(
  defineProps<{
    /** When embedded in a drawer, show close instead of unpin. */
    mode?: 'sider' | 'drawer'
  }>(),
  { mode: 'sider' },
)

const { t } = useI18n()
const message = useMessage()
const route = useRoute()
const router = useRouter()
const authStore = useAuthStore()
const isAdmin = computed(() => roleIsAdmin(authStore.user?.roles))
const {
  pinned,
  surface,
  togglePin,
  setOpen,
  readConversationId,
  writeConversationId,
} = useAiWorkbenchShell()

const conversationId = ref<number | null>(readConversationId())
const input = ref('')
const loading = ref(false)
const messages = ref<DisplayMessage[]>([])
const streamingIndex = ref<number | null>(null)
const chatBottomRef = ref<HTMLDivElement | null>(null)
const providers = ref<AiProvider[]>([])
const selectedProviderId = ref<number | undefined>(undefined)
const showWizard = ref(false)

const title = computed(() => t('workbench.aiRailTitle'))
const needsProvider = computed(() => providers.value.length === 0)
const providerOptions = computed(() =>
  providers.value.map((p) => ({
    label: p.defaultChat ? `${p.name} (${t('ai.providerDefault')})` : p.name,
    value: p.id,
  })),
)

function buildUiContext(): UiContext {
  const assetParam = route.params.assetId
  const selectedAssetId =
    typeof assetParam === 'string' && assetParam
      ? Number(assetParam)
      : undefined
  return {
    route: typeof route.name === 'string' ? route.name : String(route.name ?? ''),
    surface: surface.value ?? 'assets',
    selectedAssetId: Number.isFinite(selectedAssetId) ? selectedAssetId : undefined,
  }
}

function ensureStreamingMessage(): number {
  if (streamingIndex.value != null) return streamingIndex.value
  const idx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    streaming: true,
  })
  streamingIndex.value = idx
  return idx
}

function handleStreamEvent(event: AiStreamEvent) {
  if (event.type === 'conversation' && event.conversationId) {
    conversationId.value = event.conversationId
    writeConversationId(event.conversationId)
    return
  }
  if (event.type === 'resume_start') {
    if (event.conversationId) {
      conversationId.value = event.conversationId
      writeConversationId(event.conversationId)
    }
    loading.value = true
    streamingIndex.value = null
    ensureStreamingMessage()
    return
  }
  if (event.type === 'token' && event.content) {
    messages.value[ensureStreamingMessage()].content += event.content
    return
  }
  if (event.type === 'done') {
    const idx = streamingIndex.value
    if (idx != null) {
      if (event.content && !messages.value[idx].content) {
        messages.value[idx].content = event.content
      }
      messages.value[idx].streaming = false
    }
    streamingIndex.value = null
    loading.value = false
    return
  }
  if (event.type === 'error') {
    message.error(event.content ?? t('ai.requestFailed'))
    loading.value = false
    streamingIndex.value = null
  }
}

const streamClient = createAiStreamClient(handleStreamEvent)

async function scrollToBottom() {
  await nextTick()
  chatBottomRef.value?.scrollIntoView({ behavior: 'smooth' })
}

async function loadMessages() {
  if (!conversationId.value) return
  const res = await getMessages(conversationId.value)
  if (res.success && res.data) messages.value = res.data
}

async function ensureConversation() {
  if (conversationId.value) return
  const res = await createConversation()
  if (res.success && res.data) {
    conversationId.value = res.data.id
    writeConversationId(res.data.id)
  }
}

async function loadProviders() {
  try {
    const res = await listChatProviders()
    if (res.success && res.data) {
      providers.value = res.data
      if (!selectedProviderId.value) {
        const def = res.data.find((p) => p.defaultChat) ?? res.data[0]
        selectedProviderId.value = def?.id
      }
      if (isAdmin.value && res.data.length === 0) {
        showWizard.value = true
      }
    }
  } catch {
    // Optional probe
  }
}

async function handleSend() {
  if (!input.value.trim() || loading.value) return
  if (needsProvider.value) {
    message.warning(t('workbench.aiRailNoProvider'))
    if (isAdmin.value) showWizard.value = true
    return
  }
  loading.value = true
  streamingIndex.value = null
  const userMsg = input.value
  input.value = ''
  messages.value.push({ role: 'user', content: userMsg, createdAt: new Date().toISOString() })
  await scrollToBottom()
  try {
    await ensureConversation()
    await streamClient.connect()
    streamClient.sendChat(
      userMsg,
      conversationId.value ?? undefined,
      selectedProviderId.value,
      buildUiContext(),
    )
  } catch {
    message.error(t('ai.requestFailed'))
    loading.value = false
  }
}

async function onWizardCompleted() {
  showWizard.value = false
  await loadProviders()
}

async function handleNewChat() {
  conversationId.value = null
  writeConversationId(null)
  messages.value = []
  streamingIndex.value = null
  await ensureConversation()
}

function handleClose() {
  setOpen(false)
}

function handlePinToggle() {
  togglePin()
  if (!pinned.value) {
    setOpen(false)
  }
}

watch(messages, () => scrollToBottom(), { deep: true })

onMounted(async () => {
  await loadProviders()
  if (conversationId.value) {
    await loadMessages()
  }
  try {
    await streamClient.connect()
  } catch {
    // Retry on first send.
  }
})

onBeforeUnmount(() => {
  streamClient.disconnect()
})
</script>

<template>
  <div class="ai-rail" :data-mode="props.mode">
    <header class="ai-rail__header">
      <span class="ai-rail__title">{{ title }}</span>
      <div class="ai-rail__actions">
        <NTooltip :show-arrow="false">
          <template #trigger>
            <NButton quaternary circle size="tiny" :aria-label="t('ai.newChat')" @click="handleNewChat">
              <template #icon><NIcon :component="RefreshOutline" /></template>
            </NButton>
          </template>
          {{ t('ai.newChat') }}
        </NTooltip>
        <NTooltip v-if="props.mode === 'sider'" :show-arrow="false">
          <template #trigger>
            <NButton
              quaternary
              circle
              size="tiny"
              :type="pinned ? 'primary' : 'default'"
              :aria-label="pinned ? t('workbench.unpinRail') : t('workbench.pinRail')"
              @click="handlePinToggle"
            >
              <template #icon><NIcon :component="PinOutline" /></template>
            </NButton>
          </template>
          {{ pinned ? t('workbench.unpinRail') : t('workbench.pinRail') }}
        </NTooltip>
        <NTooltip :show-arrow="false">
          <template #trigger>
            <NButton
              quaternary
              circle
              size="tiny"
              :aria-label="t('workbench.openFullAi')"
              @click="router.push({ name: 'ai' })"
            >
              <template #icon><NIcon :component="OpenOutline" /></template>
            </NButton>
          </template>
          {{ t('workbench.openFullAi') }}
        </NTooltip>
        <NTooltip :show-arrow="false">
          <template #trigger>
            <NButton
              quaternary
              circle
              size="tiny"
              :aria-label="t('workbench.closeRail')"
              @click="handleClose"
            >
              <template #icon><NIcon :component="CloseOutline" /></template>
            </NButton>
          </template>
          {{ t('workbench.closeRail') }}
        </NTooltip>
      </div>
    </header>

    <div v-if="needsProvider" class="ai-rail__setup">
      <p>{{ t('workbench.aiRailNoProvider') }}</p>
      <NButton v-if="isAdmin" size="tiny" type="primary" @click="showWizard = true">
        {{ t('workbench.aiRailOpenWizard') }}
      </NButton>
      <NButton v-else size="tiny" @click="router.push({ name: 'ai-settings' })">
        {{ t('dashboard.goAiSettings') }}
      </NButton>
    </div>
    <div v-else class="ai-rail__provider">
      <NSelect
        v-model:value="selectedProviderId"
        size="tiny"
        :options="providerOptions"
        :placeholder="t('workbench.provider')"
        clearable
      />
    </div>

    <div class="ai-rail__messages" aria-live="polite">
      <p v-if="!loading && !messages.length" class="ai-rail__empty">
        {{ t('workbench.aiRailEmpty') }}
      </p>
      <div
        v-for="(msg, i) in messages"
        :key="`${msg.createdAt}-${msg.role}-${i}`"
        class="ai-rail__row"
        :class="msg.role"
      >
        <div class="ai-rail__bubble">
          <span class="ai-rail__role">
            {{ msg.role === 'user' ? t('ai.roleUser') : t('ai.roleAssistant') }}
          </span>
          <!-- eslint-disable-next-line vue/no-v-html -->
          <div
            v-if="msg.content"
            class="ai-rail__content"
            v-html="renderChatContent(msg.content)"
          />
        </div>
      </div>
      <div v-if="loading && streamingIndex === null" class="ai-rail__loading">
        <NSpin size="small" />
        <span>{{ t('ai.sending') }}</span>
      </div>
      <div ref="chatBottomRef" />
    </div>

    <div class="ai-rail__composer">
      <NInput
        v-model:value="input"
        type="textarea"
        size="small"
        :placeholder="t('ai.placeholder')"
        :autosize="{ minRows: 2, maxRows: 4 }"
        @keyup.ctrl.enter="handleSend"
      />
      <NButton
        type="primary"
        size="small"
        block
        :loading="loading"
        :disabled="!input.trim() || needsProvider"
        @click="handleSend"
      >
        {{ t('ai.send') }}
      </NButton>
    </div>

    <AiProviderSetupWizard
      v-model:show="showWizard"
      @completed="onWizardCompleted"
      @changed="loadProviders"
    />
  </div>
</template>

<style scoped>
.ai-rail {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  background: var(--co-bg-card);
}

.ai-rail__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--co-space-2);
  padding: var(--co-space-3) var(--co-space-3) var(--co-space-3) var(--co-space-4);
  border-bottom: 1px solid var(--co-border);
  flex-shrink: 0;
}

.ai-rail__setup,
.ai-rail__provider {
  padding: 0.5rem 0.75rem;
  border-bottom: 1px solid var(--co-border);
  flex-shrink: 0;
}

.ai-rail__setup p {
  margin: 0 0 0.5rem;
  font-size: 0.75rem;
  color: var(--co-text-secondary);
  line-height: 1.4;
}

.ai-rail__title {
  font-size: 0.8125rem;
  font-weight: 600;
  color: var(--co-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.ai-rail__actions {
  display: flex;
  align-items: center;
  gap: 2px;
  flex-shrink: 0;
}

.ai-rail__messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--co-space-3);
  min-height: 0;
}

.ai-rail__empty {
  margin: var(--co-space-6) var(--co-space-2);
  font-size: 0.8125rem;
  color: var(--co-text-muted);
  text-align: center;
  line-height: 1.5;
}

.ai-rail__row {
  display: flex;
  margin-bottom: var(--co-space-3);
}

.ai-rail__row.user {
  justify-content: flex-end;
}

.ai-rail__row.assistant {
  justify-content: flex-start;
}

.ai-rail__bubble {
  max-width: 95%;
  padding: var(--co-space-2) var(--co-space-3);
  border-radius: var(--co-radius);
  background: var(--co-bg-page);
  border: 1px solid var(--co-border);
}

.ai-rail__row.user .ai-rail__bubble {
  background: color-mix(in srgb, var(--co-primary) 12%, var(--co-bg-card));
  border-color: transparent;
}

.ai-rail__role {
  display: block;
  font-size: 0.6875rem;
  font-weight: 600;
  color: var(--co-text-muted);
  margin-bottom: 4px;
}

.ai-rail__content {
  font-size: 0.8125rem;
  line-height: 1.5;
  word-break: break-word;
}

.ai-rail__content :deep(.chat-code) {
  margin: var(--co-space-2) 0;
  padding: var(--co-space-2);
  overflow-x: auto;
  font-size: 0.75rem;
  background: var(--co-bg-page);
  border-radius: var(--co-radius);
}

.ai-rail__content :deep(.chat-inline-code) {
  font-size: 0.75rem;
  padding: 0 3px;
  background: var(--co-bg-page);
  border-radius: 2px;
}

.ai-rail__loading {
  display: flex;
  align-items: center;
  gap: var(--co-space-2);
  font-size: 0.75rem;
  color: var(--co-text-muted);
  padding: var(--co-space-2);
}

.ai-rail__composer {
  display: flex;
  flex-direction: column;
  gap: var(--co-space-2);
  padding: var(--co-space-3);
  border-top: 1px solid var(--co-border);
  flex-shrink: 0;
}
</style>
