<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NAlert, NButton, NCard, NInput, NSelect, NSpace, NSpin, NTag, useMessage } from 'naive-ui'
import {
  createConversation,
  getConversationTargets,
  getMessages,
  updateConversationTargets,
  type ChatMessage,
} from '@/api/ai'
import { createAiStreamClient, type AiStreamEvent } from '@/api/aiStream'
import { listChatProviders, type AiProvider } from '@/api/ai-providers'
import { listAssets, type Asset } from '@/api/assets'
import { listAssetGroups, type AssetGroup } from '@/api/assetGroups'
import { listSshPool, type SshPoolEntry } from '@/api/sshPool'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { renderChatContent } from '@/utils/format'

interface ToolBlock {
  tool: string
  status: string
  output: string
}

interface DisplayMessage extends ChatMessage {
  streaming?: boolean
  tools?: ToolBlock[]
  pendingApproval?: { approvalId: number; risk: string; message: string }
  architectureProposal?: { proposalId: number; status: string; message: string }
  workLogHint?: { level: string; message: string }
}

const { t } = useI18n()
const message = useMessage()

const conversationId = ref<number | null>(null)
const input = ref('')
const loading = ref(false)
const savingTargets = ref(false)
const messages = ref<DisplayMessage[]>([])
const providers = ref<AiProvider[]>([])
const assets = ref<Asset[]>([])
const groups = ref<AssetGroup[]>([])
const poolEntries = ref<SshPoolEntry[]>([])
const selectedProviderId = ref<number | undefined>(undefined)
const targetAssetIds = ref<number[]>([])
const targetGroupIds = ref<number[]>([])
const resolvedAssetIds = ref<number[]>([])
const chatBottomRef = ref<HTMLDivElement | null>(null)
const streamingIndex = ref<number | null>(null)

const providerOptions = computed(() =>
  providers.value.map((p) => ({
    label: p.defaultChat ? `${p.name} (${t('ai.providerDefault')})` : p.name,
    value: p.id,
  })),
)

const assetOptions = computed(() =>
  assets.value
    .filter((a) => a.hasSshCredential)
    .map((a) => ({ label: `${a.name} (${a.host})`, value: a.id })),
)

const groupOptions = computed(() =>
  groups.value.map((g) => ({
    label: `${g.name} (${g.memberCount})`,
    value: g.id,
  })),
)

const displayTargetAssetIds = computed(() =>
  resolvedAssetIds.value.length ? resolvedAssetIds.value : targetAssetIds.value,
)

const poolStatusByAsset = computed(() => {
  const map = new Map<number, SshPoolEntry>()
  for (const entry of poolEntries.value) {
    map.set(entry.assetId, entry)
  }
  return map
})

function messageKey(msg: DisplayMessage, index: number) {
  return `${msg.createdAt ?? index}-${msg.role}-${index}`
}

function roleLabel(role: string) {
  return role === 'user' ? t('ai.roleUser') : t('ai.roleAssistant')
}

function ensureStreamingMessage(): number {
  if (streamingIndex.value != null) {
    return streamingIndex.value
  }
  const idx = messages.value.length
  messages.value.push({
    role: 'assistant',
    content: '',
    createdAt: new Date().toISOString(),
    streaming: true,
    tools: [],
  })
  streamingIndex.value = idx
  return idx
}

function parseProposalId(content: string | null | undefined): number | null {
  if (!content) return null
  try {
    const parsed = JSON.parse(content) as { proposalId?: number }
    return typeof parsed.proposalId === 'number' ? parsed.proposalId : null
  } catch {
    return null
  }
}

function handleStreamEvent(event: AiStreamEvent) {
  if (event.type === 'conversation' && event.conversationId) {
    conversationId.value = event.conversationId
    return
  }

  if (event.type === 'resume_start') {
    if (event.conversationId) {
      conversationId.value = event.conversationId
    }
    loading.value = true
    streamingIndex.value = null
    ensureStreamingMessage()
    return
  }

  if (event.type === 'token' && event.content) {
    const idx = ensureStreamingMessage()
    messages.value[idx].content += event.content
    return
  }

  if (event.type === 'tool_start' && event.tool) {
    const idx = ensureStreamingMessage()
    const tools = messages.value[idx].tools ?? []
    tools.push({
      tool: event.tool,
      status: 'RUNNING',
      output: event.content ?? '',
    })
    messages.value[idx].tools = tools
    return
  }

  if (event.type === 'tool_result' && event.tool) {
    const idx = ensureStreamingMessage()
    const tools = messages.value[idx].tools ?? []
    const existing = tools.find((block) => block.tool === event.tool && block.status === 'RUNNING')
    if (existing) {
      existing.status = event.status ?? 'SUCCESS'
      existing.output = event.content ?? ''
    } else {
      tools.push({
        tool: event.tool,
        status: event.status ?? 'SUCCESS',
        output: event.content ?? '',
      })
    }
    messages.value[idx].tools = tools
    return
  }

  if (event.type === 'approval_required' && event.approvalId) {
    const idx = ensureStreamingMessage()
    messages.value[idx].pendingApproval = {
      approvalId: event.approvalId,
      risk: event.risk ?? 'UNKNOWN',
      message: event.content ?? '',
    }
    if (event.content) {
      messages.value[idx].content = event.content
    }
    return
  }

  if (event.type === 'architecture_proposal_created') {
    const idx = ensureStreamingMessage()
    const proposalId = event.proposalId ?? parseProposalId(event.content)
    if (proposalId != null) {
      messages.value[idx].architectureProposal = {
        proposalId,
        status: event.status ?? 'PENDING_REVIEW',
        message: event.content ?? '',
      }
    }
    return
  }

  if (event.type === 'work_log_appended') {
    const idx = ensureStreamingMessage()
    messages.value[idx].workLogHint = {
      level: event.status ?? 'INFO',
      message: event.content ?? '',
    }
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
    void refreshPool()
    return
  }

  if (event.type === 'error') {
    message.error(event.content ?? t('ai.requestFailed'))
    loading.value = false
    streamingIndex.value = null
  }
}

const streamClient = createAiStreamClient(handleStreamEvent)

async function loadProviders() {
  const res = await listChatProviders()
  if (res.success && res.data) {
    providers.value = res.data
  }
}

async function loadAssets() {
  const res = await listAssets()
  if (res.success && res.data) {
    assets.value = res.data
  }
}

async function loadGroups() {
  const res = await listAssetGroups()
  if (res.success && res.data) {
    groups.value = res.data
  }
}

async function refreshPool() {
  const res = await listSshPool()
  if (res.success && res.data) {
    poolEntries.value = res.data
  }
}

async function ensureConversation() {
  if (conversationId.value) return
  const res = await createConversation()
  if (res.success && res.data) {
    conversationId.value = res.data.id
    applyTargets(res.data)
  }
}

function applyTargets(data: {
  targetAssetIds?: number[]
  targetGroupIds?: number[]
  resolvedAssetIds?: number[]
}) {
  targetAssetIds.value = data.targetAssetIds ?? []
  targetGroupIds.value = data.targetGroupIds ?? []
  resolvedAssetIds.value = data.resolvedAssetIds ?? []
}

async function loadTargets() {
  if (!conversationId.value) return
  const res = await getConversationTargets(conversationId.value)
  if (res.success && res.data) {
    applyTargets(res.data)
  }
}

async function loadMessages() {
  if (!conversationId.value) return
  const res = await getMessages(conversationId.value)
  if (res.success && res.data) messages.value = res.data
}

async function persistTargets(nextAssets: number[], nextGroups: number[]) {
  if (!conversationId.value) return
  savingTargets.value = true
  try {
    const res = await updateConversationTargets(conversationId.value, nextAssets, nextGroups)
    if (res.success && res.data) {
      applyTargets(res.data)
      await refreshPool()
      message.success(t('ai.targetsSaved'))
    }
  } catch {
    message.error(t('ai.targetsSaveFailed'))
  } finally {
    savingTargets.value = false
  }
}

async function handleTargetsChange(value: number[]) {
  await persistTargets(value, targetGroupIds.value)
}

async function handleGroupTargetsChange(value: number[]) {
  await persistTargets(targetAssetIds.value, value)
}

async function scrollToBottom() {
  await nextTick()
  chatBottomRef.value?.scrollIntoView({ behavior: 'smooth' })
}

async function handleSend() {
  if (!input.value.trim() || loading.value) return
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
      selectedProviderId.value ?? undefined,
    )
  } catch {
    message.error(t('ai.requestFailed'))
    loading.value = false
  }
}

async function handleNewChat() {
  conversationId.value = null
  messages.value = []
  targetAssetIds.value = []
  targetGroupIds.value = []
  resolvedAssetIds.value = []
  streamingIndex.value = null
  await ensureConversation()
}

watch(conversationId, async (id) => {
  if (id) {
    await loadTargets()
    await loadMessages()
  }
})

watch(messages, () => scrollToBottom(), { deep: true })

onMounted(async () => {
  await Promise.all([loadProviders(), loadAssets(), loadGroups(), refreshPool()])
  await ensureConversation()
  await loadTargets()
  await loadMessages()
  try {
    await streamClient.connect()
  } catch {
    // User can retry on first send.
  }
})

onBeforeUnmount(() => {
  streamClient.disconnect()
})
</script>

<template>
  <div class="chat-page">
    <PageHeader :title="t('ai.title')" :description="t('ai.subtitle')">
      <template #extra>
        <NSpace align="center" :size="12">
          <NSelect
            v-model:value="targetGroupIds"
            class="select-lg"
            :options="groupOptions"
            :placeholder="t('ai.targetGroups')"
            :loading="savingTargets"
            multiple
            :aria-label="t('ai.targetGroups')"
            @update:value="handleGroupTargetsChange"
          />
          <NSelect
            v-model:value="targetAssetIds"
            class="select-lg"
            :options="assetOptions"
            :placeholder="t('ai.targetAssets')"
            :loading="savingTargets"
            multiple
            :aria-label="t('ai.targetAssets')"
            @update:value="handleTargetsChange"
          />
          <NSelect
            v-model:value="selectedProviderId"
            class="select-md"
            :options="providerOptions"
            :placeholder="t('ai.provider')"
            clearable
            :aria-label="t('ai.provider')"
          />
          <NButton @click="handleNewChat">{{ t('ai.newChat') }}</NButton>
        </NSpace>
      </template>
    </PageHeader>

    <NSpace v-if="displayTargetAssetIds.length" size="small" class="target-tags">
      <NTag
        v-for="id in displayTargetAssetIds"
        :key="id"
        size="small"
        :type="poolStatusByAsset.get(id)?.alive ? 'success' : 'default'"
      >
        {{ assets.find((a) => a.id === id)?.name ?? id }}
        · {{ poolStatusByAsset.get(id)?.alive ? t('ai.poolConnected') : t('ai.poolIdle') }}
      </NTag>
    </NSpace>

    <NCard class="chat-card page-card" :bordered="false">
      <div class="chat-messages" aria-live="polite">
        <EmptyState
          v-if="!loading && messages.length === 0"
          :message="t('ai.emptyTitle')"
          :hint="t('ai.emptyHint')"
        />
        <div
          v-for="(msg, i) in messages"
          :key="messageKey(msg, i)"
          class="chat-row"
          :class="msg.role"
        >
          <div class="chat-bubble">
            <span class="chat-bubble__role">{{ roleLabel(msg.role) }}</span>
            <div
              v-for="(tool, ti) in msg.tools"
              :key="`${tool.tool}-${ti}`"
              class="tool-block"
            >
              <div class="tool-block__header">
                <span class="tool-block__name">{{ tool.tool }}</span>
                <NTag size="small" :type="tool.status === 'SUCCESS' ? 'success' : tool.status === 'RUNNING' ? 'info' : 'warning'">
                  {{ tool.status }}
                </NTag>
              </div>
              <pre v-if="tool.output" class="tool-block__output">{{ tool.output }}</pre>
            </div>
            <NAlert
              v-if="msg.pendingApproval"
              type="warning"
              class="approval-alert"
            >
              <template #header>
                {{ t('ai.approvalRequired') }} #{{ msg.pendingApproval.approvalId }}
                ({{ msg.pendingApproval.risk }})
              </template>
              {{ msg.pendingApproval.message }}
              <div class="approval-hint">{{ t('ai.approvalResume') }}</div>
            </NAlert>
            <NAlert
              v-if="msg.architectureProposal"
              type="info"
              class="approval-alert"
            >
              <template #header>
                {{ t('ai.proposalCreated') }} #{{ msg.architectureProposal.proposalId }}
                ({{ msg.architectureProposal.status }})
              </template>
              <div>{{ t('ai.proposalCreatedHint') }}</div>
              <RouterLink class="hint-link" :to="{ name: 'architecture-proposals' }">
                {{ t('ai.openProposals') }}
              </RouterLink>
            </NAlert>
            <NAlert
              v-if="msg.workLogHint"
              type="success"
              class="approval-alert"
            >
              <template #header>
                {{ t('ai.workLogAppended') }}
                <span v-if="msg.workLogHint.level">({{ msg.workLogHint.level }})</span>
              </template>
              <RouterLink class="hint-link" :to="{ name: 'architecture-proposals' }">
                {{ t('ai.openProposals') }}
              </RouterLink>
            </NAlert>
            <!-- eslint-disable-next-line vue/no-v-html -->
            <div
              v-if="msg.content"
              class="chat-bubble__content"
              v-html="renderChatContent(msg.content)"
            />
          </div>
        </div>
        <div v-if="loading && streamingIndex === null" class="chat-loading">
          <NSpin size="small" />
          <span>{{ t('ai.sending') }}</span>
        </div>
        <div ref="chatBottomRef" />
      </div>

      <div class="chat-input">
        <NInput
          v-model:value="input"
          type="textarea"
          :placeholder="t('ai.placeholder')"
          :autosize="{ minRows: 2, maxRows: 5 }"
          class="chat-input__field"
          @keyup.ctrl.enter="handleSend"
        />
        <div class="chat-input__actions">
          <span class="chat-input__hint">{{ t('ai.sendHint') }}</span>
          <NButton type="primary" :loading="loading" :disabled="!input.trim()" @click="handleSend">
            {{ t('ai.send') }}
          </NButton>
        </div>
      </div>
    </NCard>
  </div>
</template>

<style scoped>
.chat-page {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--co-header-height) - var(--co-space-6) * 2);
  min-height: 480px;
}

.target-tags {
  margin-bottom: var(--co-space-2);
  flex-wrap: wrap;
}

.chat-card {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}

.chat-card :deep(.n-card__content) {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  padding-top: 0 !important;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: var(--co-space-4) 0;
  min-height: 0;
}

.chat-row {
  display: flex;
  margin-bottom: var(--co-space-4);
}

.chat-row.user {
  justify-content: flex-end;
}

.chat-row.assistant {
  justify-content: flex-start;
}

.chat-bubble {
  max-width: min(85%, 640px);
  padding: var(--co-space-3) var(--co-space-4);
  border-radius: var(--co-radius-lg);
  border: 1px solid var(--co-border);
}

.chat-row.user .chat-bubble {
  background: rgba(15, 118, 110, 0.12);
  border-color: rgba(15, 118, 110, 0.25);
}

.chat-row.assistant .chat-bubble {
  background: var(--co-bg-page);
}

.chat-bubble__role {
  display: block;
  font-size: 0.6875rem;
  font-weight: 600;
  text-transform: uppercase;
  letter-spacing: 0.04em;
  color: var(--co-text-muted);
  margin-bottom: var(--co-space-2);
}

.chat-bubble__content {
  font-size: 0.875rem;
  line-height: 1.6;
  color: var(--co-text);
  word-break: break-word;
}

.tool-block {
  margin-bottom: var(--co-space-3);
  padding: var(--co-space-2) var(--co-space-3);
  border-radius: var(--co-radius-md);
  border: 1px dashed var(--co-border);
  background: rgba(15, 118, 110, 0.04);
}

.tool-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--co-space-2);
  margin-bottom: var(--co-space-2);
}

.tool-block__name {
  font-size: 0.75rem;
  font-weight: 600;
  font-family: var(--co-font-mono, monospace);
}

.tool-block__output {
  margin: 0;
  font-size: 0.75rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--co-text-secondary);
  max-height: 200px;
  overflow-y: auto;
}

.approval-alert {
  margin-bottom: var(--co-space-3);
}

.approval-hint {
  margin-top: var(--co-space-2);
  font-size: 0.75rem;
  color: var(--co-text-muted);
}

.hint-link {
  display: inline-block;
  margin-top: var(--co-space-2);
  font-size: 0.8125rem;
  color: var(--co-primary);
  text-decoration: underline;
}

.chat-loading {
  display: flex;
  align-items: center;
  gap: var(--co-space-2);
  padding: var(--co-space-2) 0;
  font-size: 0.8125rem;
  color: var(--co-text-secondary);
}

.chat-input {
  border-top: 1px solid var(--co-border);
  padding-top: var(--co-space-4);
  margin-top: auto;
}

.chat-input__field {
  width: 100%;
}

.chat-input__actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: var(--co-space-3);
  gap: var(--co-space-4);
}

.chat-input__hint {
  font-size: 0.75rem;
  color: var(--co-text-muted);
}
</style>
