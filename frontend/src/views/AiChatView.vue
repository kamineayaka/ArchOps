<script setup lang="ts">
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NCard, NInput, NSelect, NSpace, NTag, useMessage } from 'naive-ui'
import {
  createConversation,
  getConversationTargets,
  getMessages,
  sendChat,
  updateConversationTargets,
  type ChatMessage,
} from '@/api/ai'
import { listChatProviders, type AiProvider } from '@/api/ai-providers'
import { listAssets, type Asset } from '@/api/assets'
import { listSshPool, type SshPoolEntry } from '@/api/sshPool'

const { t } = useI18n()
const message = useMessage()

const conversationId = ref<number | null>(null)
const input = ref('')
const loading = ref(false)
const savingTargets = ref(false)
const messages = ref<ChatMessage[]>([])
const providers = ref<AiProvider[]>([])
const assets = ref<Asset[]>([])
const poolEntries = ref<SshPoolEntry[]>([])
const selectedProviderId = ref<number | undefined>(undefined)
const targetAssetIds = ref<number[]>([])

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

const poolStatusByAsset = computed(() => {
  const map = new Map<number, SshPoolEntry>()
  for (const entry of poolEntries.value) {
    map.set(entry.assetId, entry)
  }
  return map
})

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
    targetAssetIds.value = res.data.targetAssetIds ?? []
  }
}

async function loadTargets() {
  if (!conversationId.value) return
  const res = await getConversationTargets(conversationId.value)
  if (res.success && res.data) {
    targetAssetIds.value = res.data
  }
}

async function loadMessages() {
  if (!conversationId.value) return
  const res = await getMessages(conversationId.value)
  if (res.success && res.data) messages.value = res.data
}

async function handleTargetsChange(value: number[]) {
  if (!conversationId.value) return
  savingTargets.value = true
  try {
    const res = await updateConversationTargets(conversationId.value, value)
    if (res.success) {
      targetAssetIds.value = value
      await refreshPool()
      message.success(t('ai.targetsSaved'))
    }
  } catch {
    message.error(t('ai.targetsSaveFailed'))
  } finally {
    savingTargets.value = false
  }
}

async function handleSend() {
  if (!input.value.trim()) return
  loading.value = true
  const userMsg = input.value
  input.value = ''
  messages.value.push({ role: 'user', content: userMsg, createdAt: new Date().toISOString() })
  try {
    await ensureConversation()
    const res = await sendChat(
      userMsg,
      conversationId.value ?? undefined,
      selectedProviderId.value ?? undefined,
    )
    if (res.success && res.data) {
      conversationId.value = res.data.conversationId
      messages.value.push({ role: 'assistant', content: res.data.answer, createdAt: new Date().toISOString() })
      await refreshPool()
    }
  } catch {
    message.error(t('ai.requestFailed'))
  } finally {
    loading.value = false
    await nextTick()
    document.getElementById('chat-bottom')?.scrollIntoView({ behavior: 'smooth' })
  }
}

async function handleNewChat() {
  conversationId.value = null
  messages.value = []
  targetAssetIds.value = []
  await ensureConversation()
}

watch(conversationId, async (id) => {
  if (id) {
    await loadTargets()
    await loadMessages()
  }
})

onMounted(async () => {
  await Promise.all([loadProviders(), loadAssets(), refreshPool()])
  await ensureConversation()
  await loadTargets()
  await loadMessages()
})
</script>

<template>
  <NCard :title="t('ai.title')" style="height: calc(100vh - 120px); display: flex; flex-direction: column">
    <template #header-extra>
      <NSpace align="center">
        <NSelect
          v-model:value="targetAssetIds"
          :options="assetOptions"
          :placeholder="t('ai.targetAssets')"
          :loading="savingTargets"
          multiple
          style="width: 320px"
          @update:value="handleTargetsChange"
        />
        <NSelect
          v-model:value="selectedProviderId"
          :options="providerOptions"
          :placeholder="t('ai.provider')"
          style="width: 220px"
          clearable
        />
        <NButton @click="handleNewChat">{{ t('ai.newChat') }}</NButton>
      </NSpace>
    </template>

    <NSpace v-if="targetAssetIds.length" size="small" style="margin-bottom: 8px">
      <NTag
        v-for="id in targetAssetIds"
        :key="id"
        size="small"
        :type="poolStatusByAsset.get(id)?.alive ? 'success' : 'default'"
      >
        {{ assets.find((a) => a.id === id)?.name ?? id }}
        · {{ poolStatusByAsset.get(id)?.alive ? t('ai.poolConnected') : t('ai.poolIdle') }}
      </NTag>
    </NSpace>

    <div class="chat-messages">
      <div v-for="(msg, i) in messages" :key="i" class="chat-bubble" :class="msg.role">
        <NTag size="small" :type="msg.role === 'user' ? 'info' : 'success'">{{ msg.role }}</NTag>
        <pre class="content">{{ msg.content }}</pre>
      </div>
      <div id="chat-bottom" />
    </div>

    <NSpace style="margin-top: auto; padding-top: 12px">
      <NInput
        v-model:value="input"
        type="textarea"
        :placeholder="t('ai.placeholder')"
        :autosize="{ minRows: 2, maxRows: 4 }"
        style="flex: 1"
        @keyup.ctrl.enter="handleSend"
      />
      <NButton type="primary" :loading="loading" @click="handleSend">{{ t('ai.send') }}</NButton>
    </NSpace>
  </NCard>
</template>

<style scoped>
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 0;
  max-height: calc(100vh - 320px);
}

.chat-bubble {
  margin-bottom: 16px;
}

.chat-bubble .content {
  margin: 6px 0 0;
  white-space: pre-wrap;
  font-family: inherit;
  font-size: 14px;
  line-height: 1.6;
}
</style>
