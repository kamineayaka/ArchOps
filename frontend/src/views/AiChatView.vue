<script setup lang="ts">
import { computed, nextTick, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { NButton, NCard, NInput, NSelect, NSpace, NTag, useMessage } from 'naive-ui'
import { createConversation, getMessages, sendChat, type ChatMessage } from '@/api/ai'
import { listChatProviders, type AiProvider } from '@/api/ai-providers'

const { t } = useI18n()
const message = useMessage()

const conversationId = ref<number | null>(null)
const input = ref('')
const loading = ref(false)
const messages = ref<ChatMessage[]>([])
const providers = ref<AiProvider[]>([])
const selectedProviderId = ref<number | undefined>(undefined)

const providerOptions = computed(() =>
  providers.value.map((p) => ({
    label: p.defaultChat ? `${p.name} (${t('ai.providerDefault')})` : p.name,
    value: p.id,
  })),
)

async function loadProviders() {
  const res = await listChatProviders()
  if (res.success && res.data) {
    providers.value = res.data
    const defaultProvider = res.data.find((p) => p.defaultChat)
    if (defaultProvider && selectedProviderId.value === undefined) {
      selectedProviderId.value = undefined
    }
  }
}

async function ensureConversation() {
  if (conversationId.value) return
  const res = await createConversation()
  if (res.success && res.data) conversationId.value = res.data.id
}

async function loadMessages() {
  if (!conversationId.value) return
  const res = await getMessages(conversationId.value)
  if (res.success && res.data) messages.value = res.data
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
  await ensureConversation()
}

onMounted(async () => {
  await loadProviders()
  await ensureConversation()
  await loadMessages()
})
</script>

<template>
  <NCard :title="t('ai.title')" style="height: calc(100vh - 120px); display: flex; flex-direction: column">
    <template #header-extra>
      <NSpace align="center">
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
  max-height: calc(100vh - 280px);
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
