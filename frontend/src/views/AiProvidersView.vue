<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useMessage,
} from 'naive-ui'
import {
  createProvider,
  deleteProvider,
  fetchProviderModels,
  getAiSettings,
  listAllProviders,
  testProvider,
  updateAiSettings,
  updateProvider,
  type AiProvider,
  type AiProviderRequest,
  type ProviderType,
} from '@/api/ai-providers'

const { t } = useI18n()
const message = useMessage()

const providers = ref<AiProvider[]>([])
const loading = ref(false)
const showModal = ref(false)
const editingId = ref<number | null>(null)
const modelOptions = ref<{ label: string; value: string }[]>([])
const fetchingModels = ref(false)

const settings = ref({
  defaultChatProviderId: null as number | null,
  defaultEmbeddingProviderId: null as number | null,
  ragEnabled: true,
  ragTopK: 5,
  ragMinSimilarity: 0.35,
})

const form = ref<AiProviderRequest>({
  name: '',
  providerType: 'OPENAI_COMPAT',
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  chatModel: 'gpt-4o-mini',
  embeddingModel: 'text-embedding-3-small',
  embeddingDims: 1536,
  supportsChat: true,
  supportsEmbedding: true,
  enabled: true,
  timeoutMs: 60000,
})

const typeOptions = [
  { label: 'OpenAI 兼容', value: 'OPENAI_COMPAT' },
  { label: 'Anthropic', value: 'ANTHROPIC' },
]

const chatProviderOptions = computed(() =>
  providers.value.filter((p) => p.supportsChat).map((p) => ({ label: p.name, value: p.id })),
)

const embeddingProviderOptions = computed(() =>
  providers.value.filter((p) => p.supportsEmbedding).map((p) => ({ label: p.name, value: p.id })),
)

const columns = [
  { title: 'ID', key: 'id', width: 60 },
  { title: t('aiSettings.name'), key: 'name' },
  {
    title: t('aiSettings.type'),
    key: 'providerType',
    render: (row: AiProvider) => (row.providerType === 'ANTHROPIC' ? 'Anthropic' : 'OpenAI 兼容'),
  },
  { title: t('aiSettings.chatModel'), key: 'chatModel' },
  {
    title: t('aiSettings.defaults'),
    key: 'defaults',
    render: (row: AiProvider) => {
      const tags = []
      if (row.defaultChat) tags.push(h(NTag, { size: 'small', type: 'success' }, { default: () => 'Chat' }))
      if (row.defaultEmbedding) tags.push(h(NTag, { size: 'small', type: 'info', style: 'margin-left:4px' }, { default: () => 'Embed' }))
      return tags.length ? tags : '-'
    },
  },
  {
    title: t('aiSettings.enabled'),
    key: 'enabled',
    render: (row: AiProvider) => (row.enabled ? t('aiSettings.yes') : t('aiSettings.no')),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    render: (row: AiProvider) => [
      h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
      h(
        NButton,
        { size: 'small', style: 'margin-left:8px', onClick: () => handleTest(row.id) },
        { default: () => t('aiSettings.test') },
      ),
      h(
        NButton,
        { size: 'small', type: 'error', style: 'margin-left:8px', onClick: () => handleDelete(row.id) },
        { default: () => t('common.delete') },
      ),
    ],
  },
]

function resetForm() {
  form.value = {
    name: '',
    providerType: 'OPENAI_COMPAT',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    chatModel: 'gpt-4o-mini',
    embeddingModel: 'text-embedding-3-small',
    embeddingDims: 1536,
    supportsChat: true,
    supportsEmbedding: true,
    enabled: true,
    timeoutMs: 60000,
  }
  modelOptions.value = []
}

function openCreate() {
  editingId.value = null
  resetForm()
  showModal.value = true
}

function openEdit(row: AiProvider) {
  editingId.value = row.id
  form.value = {
    name: row.name,
    providerType: row.providerType,
    baseUrl: row.baseUrl,
    apiKey: '',
    chatModel: row.chatModel ?? '',
    embeddingModel: row.embeddingModel ?? '',
    embeddingDims: row.embeddingDims ?? 1536,
    supportsChat: row.supportsChat,
    supportsEmbedding: row.supportsEmbedding,
    enabled: row.enabled,
    timeoutMs: row.timeoutMs,
  }
  showModal.value = true
}

function onTypeChange(type: ProviderType) {
  if (type === 'ANTHROPIC') {
    form.value.baseUrl = 'https://api.anthropic.com'
    form.value.supportsEmbedding = false
    form.value.chatModel = 'claude-3-5-sonnet-20241022'
  } else {
    form.value.baseUrl = 'https://api.openai.com/v1'
    form.value.supportsEmbedding = true
    form.value.chatModel = 'gpt-4o-mini'
  }
}

async function load() {
  loading.value = true
  try {
    const [provRes, settingsRes] = await Promise.all([listAllProviders(), getAiSettings()])
    if (provRes.success && provRes.data) providers.value = provRes.data
    if (settingsRes.success && settingsRes.data) {
      settings.value = { ...settingsRes.data }
    }
  } finally {
    loading.value = false
  }
}

async function handleSaveProvider() {
  const payload = { ...form.value }
  if (editingId.value && !payload.apiKey) {
    delete payload.apiKey
  }
  const res = editingId.value
    ? await updateProvider(editingId.value, payload)
    : await createProvider(payload)
  if (res.success) {
    message.success(t('aiSettings.saved'))
    showModal.value = false
    await load()
  }
}

async function handleDelete(id: number) {
  const res = await deleteProvider(id)
  if (res.success) {
    message.success(t('aiSettings.deleted'))
    await load()
  }
}

async function handleTest(id: number) {
  const res = await testProvider(id)
  if (res.success && res.data) {
    message.info(res.data.status)
  }
}

async function handleFetchModels() {
  if (!editingId.value && !form.value.apiKey) {
    message.warning(t('aiSettings.apiKeyRequiredForModels'))
    return
  }
  fetchingModels.value = true
  try {
    let id = editingId.value
    if (!id) {
      const created = await createProvider({ ...form.value })
      if (!created.success || !created.data) return
      id = created.data.id
      editingId.value = id
      await load()
    }
    const res = await fetchProviderModels(id)
    if (res.success && res.data) {
      modelOptions.value = res.data.map((m) => ({ label: m, value: m }))
      message.success(t('aiSettings.modelsLoaded', { count: res.data.length }))
    }
  } finally {
    fetchingModels.value = false
  }
}

async function handleSaveSettings() {
  const res = await updateAiSettings(settings.value)
  if (res.success) {
    message.success(t('aiSettings.platformSaved'))
    if (res.data) settings.value = { ...res.data }
    await load()
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="16">
    <NCard :title="t('aiSettings.providersTitle')">
      <template #header-extra>
        <NButton type="primary" @click="openCreate">{{ t('aiSettings.addProvider') }}</NButton>
      </template>
      <NDataTable :loading="loading" :columns="columns" :data="providers" :bordered="false" />
    </NCard>

    <NCard :title="t('aiSettings.platformTitle')">
      <NForm label-placement="left" label-width="180">
        <NFormItem :label="t('aiSettings.defaultChat')">
          <NSelect v-model:value="settings.defaultChatProviderId" :options="chatProviderOptions" clearable />
        </NFormItem>
        <NFormItem :label="t('aiSettings.defaultEmbedding')">
          <NSelect v-model:value="settings.defaultEmbeddingProviderId" :options="embeddingProviderOptions" clearable />
        </NFormItem>
        <NFormItem :label="t('aiSettings.ragEnabled')">
          <NSwitch v-model:value="settings.ragEnabled" />
        </NFormItem>
        <NFormItem :label="t('aiSettings.ragTopK')">
          <NInputNumber v-model:value="settings.ragTopK" :min="1" :max="20" />
        </NFormItem>
        <NFormItem :label="t('aiSettings.ragMinSimilarity')">
          <NInputNumber v-model:value="settings.ragMinSimilarity" :min="0" :max="1" :step="0.05" />
        </NFormItem>
        <NFormItem>
          <NButton type="primary" @click="handleSaveSettings">{{ t('common.save') }}</NButton>
        </NFormItem>
        <p class="hint">{{ t('aiSettings.reindexHint') }}</p>
      </NForm>
    </NCard>
  </NSpace>

  <NModal v-model:show="showModal" preset="card" :title="editingId ? t('aiSettings.editProvider') : t('aiSettings.addProvider')" style="width: 560px">
    <NForm label-placement="left" label-width="120">
      <NFormItem :label="t('aiSettings.type')">
        <NSelect v-model:value="form.providerType" :options="typeOptions" @update:value="onTypeChange" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.name')">
        <NInput v-model:value="form.name" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.baseUrl')">
        <NInput v-model:value="form.baseUrl" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.apiKey')">
        <NInput v-model:value="form.apiKey" type="password" :placeholder="editingId ? t('aiSettings.apiKeyKeep') : ''" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.chatModel')">
        <NSelect v-model:value="form.chatModel" :options="modelOptions" filterable tag />
      </NFormItem>
      <NFormItem v-if="form.providerType === 'OPENAI_COMPAT'" :label="t('aiSettings.embeddingModel')">
        <NInput v-model:value="form.embeddingModel" />
      </NFormItem>
      <NFormItem v-if="form.providerType === 'OPENAI_COMPAT'" :label="t('aiSettings.embeddingDims')">
        <NInputNumber v-model:value="form.embeddingDims" :min="256" :max="4096" />
      </NFormItem>
      <NFormItem v-if="form.providerType === 'OPENAI_COMPAT'" :label="t('aiSettings.supportsEmbedding')">
        <NSwitch v-model:value="form.supportsEmbedding" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.enabled')">
        <NSwitch v-model:value="form.enabled" />
      </NFormItem>
      <NSpace>
        <NButton :loading="fetchingModels" @click="handleFetchModels">{{ t('aiSettings.fetchModels') }}</NButton>
        <NButton type="primary" @click="handleSaveProvider">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>

<style scoped>
.hint {
  color: #64748b;
  font-size: 13px;
  margin: 0;
}
</style>
