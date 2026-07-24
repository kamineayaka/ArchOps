<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NPopconfirm,
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
  type ReasoningEffort,
} from '@/api/ai-providers'
import { getIndexStats, type IndexStats } from '@/api/knowledge'
import AiProviderSetupWizard from '@/components/ai/AiProviderSetupWizard.vue'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { apiErrorMessage, isProviderTestFailed } from '@/utils/apiError'

const { t } = useI18n()
const message = useMessage()

const providers = ref<AiProvider[]>([])
const loading = ref(false)
const showModal = ref(false)
const showWizard = ref(false)
const editingId = ref<number | null>(null)
const modelOptions = ref<{ label: string; value: string }[]>([])
const fetchingModels = ref(false)
const indexStats = ref<IndexStats | null>(null)
const initialEmbeddingProviderId = ref<number | null>(null)

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
  maxOutputTokens: 0,
  contextWindow: 0,
  reasoningEnabled: false,
  reasoningEffort: 'NONE',
})

const typeOptions = computed(() => [
  { label: t('aiSettings.openAiCompat'), value: 'OPENAI_COMPAT' },
  { label: t('aiSettings.anthropic'), value: 'ANTHROPIC' },
])

const reasoningOptions = computed(() => {
  const base = [
    { label: t('aiSettings.reasoningNone'), value: 'NONE' },
    { label: t('aiSettings.reasoningLow'), value: 'LOW' },
    { label: t('aiSettings.reasoningMedium'), value: 'MEDIUM' },
    { label: t('aiSettings.reasoningHigh'), value: 'HIGH' },
    { label: t('aiSettings.reasoningXhigh'), value: 'XHIGH' },
  ]
  if (form.value.providerType === 'ANTHROPIC') {
    return [...base, { label: t('aiSettings.reasoningMax'), value: 'MAX' }]
  }
  return base
})

const baseUrlHint = computed(() =>
  form.value.providerType === 'ANTHROPIC'
    ? t('aiSettings.baseUrlHintAnthropic')
    : t('aiSettings.baseUrlHintOpenAi'),
)

const chatProviders = computed(() => providers.value.filter((p) => p.supportsChat && p.enabled))
const needsFirstRun = computed(() => providers.value.length === 0)
const needsDefault = computed(
  () => providers.value.length > 0 && !settings.value.defaultChatProviderId && chatProviders.value.length > 0,
)
const showSetupPrompt = computed(() => needsFirstRun.value || needsDefault.value)

const showReindexAlert = computed(() => {
  if (settings.value.defaultEmbeddingProviderId !== initialEmbeddingProviderId.value) {
    return true
  }
  if (!editingId.value || !showModal.value) return false
  const row = providers.value.find((p) => p.id === editingId.value)
  if (!row || row.id !== settings.value.defaultEmbeddingProviderId) return false
  return (
    form.value.embeddingModel !== (row.embeddingModel ?? '') ||
    form.value.embeddingDims !== (row.embeddingDims ?? 1536)
  )
})

const reindexAlertText = computed(() => indexStats.value?.reindexHint ?? t('aiSettings.reindexRequired'))

const chatProviderOptions = computed(() =>
  providers.value.filter((p) => p.supportsChat).map((p) => ({ label: p.name, value: p.id })),
)

const embeddingProviderOptions = computed(() =>
  providers.value.filter((p) => p.supportsEmbedding).map((p) => ({ label: p.name, value: p.id })),
)

const columns = computed(() => [
  { title: t('common.id'), key: 'id', width: 60 },
  { title: t('aiSettings.name'), key: 'name' },
  {
    title: t('aiSettings.type'),
    key: 'providerType',
    render: (row: AiProvider) =>
      row.providerType === 'ANTHROPIC' ? t('aiSettings.anthropic') : t('aiSettings.openAiCompat'),
  },
  {
    title: t('aiSettings.apiKeyMasked'),
    key: 'apiKeyMasked',
    ellipsis: { tooltip: true },
    render: (row: AiProvider) => row.apiKeyMasked || '—',
  },
  { title: t('aiSettings.chatModel'), key: 'chatModel', ellipsis: { tooltip: true } },
  {
    title: t('aiSettings.defaults'),
    key: 'defaults',
    render: (row: AiProvider) => {
      const tags: ReturnType<typeof h>[] = []
      if (row.defaultChat) tags.push(h(NTag, { size: 'small', type: 'success', round: true }, { default: () => t('aiSettings.tagChat') }))
      if (row.defaultEmbedding) tags.push(h(NTag, { size: 'small', type: 'info', round: true }, { default: () => t('aiSettings.tagEmbed') }))
      return tags.length ? h(NSpace, { size: 4 }, { default: () => tags }) : '-'
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
    width: 240,
    render: (row: AiProvider) =>
      h(NSpace, { size: 8 }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openEdit(row) }, { default: () => t('common.edit') }),
          h(NButton, { size: 'small', onClick: () => handleTest(row.id) }, { default: () => t('aiSettings.test') }),
          h(
            NPopconfirm,
            { onPositiveClick: () => handleDelete(row.id) },
            {
              trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => t('common.delete') }),
              default: () => t('common.confirmDelete'),
            },
          ),
        ],
      }),
  },
])

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
    maxOutputTokens: 0,
    contextWindow: 0,
    reasoningEnabled: false,
    reasoningEffort: 'NONE',
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
    maxOutputTokens: row.maxOutputTokens ?? 0,
    contextWindow: row.contextWindow ?? 0,
    reasoningEnabled: row.reasoningEnabled ?? false,
    reasoningEffort: (row.reasoningEffort ?? 'NONE') as ReasoningEffort,
  }
  modelOptions.value = row.chatModel ? [{ label: row.chatModel, value: row.chatModel }] : []
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
    if (form.value.reasoningEffort === 'MAX') {
      form.value.reasoningEffort = 'HIGH'
    }
  }
}

function onReasoningChange(effort: ReasoningEffort) {
  form.value.reasoningEffort = effort
  form.value.reasoningEnabled = effort !== 'NONE'
}

async function load() {
  loading.value = true
  try {
    const [provRes, settingsRes, statsRes] = await Promise.all([
      listAllProviders(),
      getAiSettings(),
      getIndexStats(),
    ])
    if (provRes.success && provRes.data) providers.value = provRes.data
    if (settingsRes.success && settingsRes.data) {
      settings.value = { ...settingsRes.data }
      initialEmbeddingProviderId.value = settingsRes.data.defaultEmbeddingProviderId
    }
    if (statsRes.success && statsRes.data) indexStats.value = statsRes.data
  } finally {
    loading.value = false
  }
}

async function handleSaveProvider() {
  const payload: AiProviderRequest = {
    ...form.value,
    reasoningEnabled: (form.value.reasoningEffort ?? 'NONE') !== 'NONE',
  }
  if (editingId.value && !payload.apiKey) {
    delete payload.apiKey
  }
  try {
    const res = editingId.value
      ? await updateProvider(editingId.value, payload)
      : await createProvider(payload)
    if (res.success) {
      message.success(t('aiSettings.saved'))
      showModal.value = false
      await load()
    } else {
      message.error(res.message || t('aiSettings.wizard.saveFailed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('aiSettings.wizard.saveFailed')))
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
  try {
    const res = await testProvider(id)
    if (!res.success) {
      message.error(res.message || t('aiSettings.wizard.testFailed'))
      return
    }
    const status = res.data?.status ?? ''
    if (isProviderTestFailed(status)) {
      const detail = status.startsWith('failed:')
        ? status.slice('failed:'.length).trim()
        : status
      message.error(detail || t('aiSettings.wizard.testFailed'))
    } else {
      message.success(t('aiSettings.wizard.testOk'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('aiSettings.wizard.testFailed')))
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
      if (!created.success || !created.data) {
        message.error(created.message || t('aiSettings.wizard.saveFailed'))
        return
      }
      id = created.data.id
      editingId.value = id
      await load()
    }
    const res = await fetchProviderModels(id)
    if (res.success && res.data) {
      modelOptions.value = res.data.map((m) => ({ label: m, value: m }))
      message.success(t('aiSettings.modelsLoaded', { count: res.data.length }))
    } else {
      message.error(res.message || t('aiSettings.wizard.modelsFailed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('aiSettings.wizard.modelsFailed')))
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

async function onWizardCompleted() {
  showWizard.value = false
  await load()
}

onMounted(async () => {
  await load()
  if (providers.value.length === 0) {
    showWizard.value = true
  }
})
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('nav.aiSettings')" :description="t('aiSettings.subtitle')" />

    <NAlert v-if="showReindexAlert" type="warning" :title="t('aiSettings.reindexRequired')">
      {{ reindexAlertText }}
    </NAlert>

    <NCard v-if="!loading && showSetupPrompt" class="page-card setup-prompt" :bordered="false">
      <NSpace vertical :size="12">
        <div>
          <h3 class="setup-prompt__title">
            {{ needsFirstRun ? t('aiSettings.needsSetupTitle') : t('aiSettings.needsDefaultTitle') }}
          </h3>
          <p class="setup-prompt__desc">
            {{ needsFirstRun ? t('aiSettings.needsSetupDesc') : t('aiSettings.needsDefaultDesc') }}
          </p>
        </div>
        <NSpace>
          <NButton type="primary" @click="showWizard = true">{{ t('aiSettings.startWizard') }}</NButton>
          <NButton v-if="needsFirstRun" quaternary @click="openCreate">{{ t('aiSettings.addProvider') }}</NButton>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard class="page-card" :title="t('aiSettings.providersTitle')" :bordered="false">
      <template #header-extra>
        <NSpace>
          <NButton v-if="needsFirstRun || needsDefault" @click="showWizard = true">
            {{ t('aiSettings.startWizard') }}
          </NButton>
          <NButton type="primary" @click="openCreate">{{ t('aiSettings.addProvider') }}</NButton>
        </NSpace>
      </template>
      <NDataTable :loading="loading" :columns="columns" :data="providers" :bordered="false" />
      <EmptyState v-if="!loading && providers.length === 0" :message="t('aiSettings.empty')">
        <template #action>
          <NButton type="primary" @click="showWizard = true">{{ t('aiSettings.startWizard') }}</NButton>
        </template>
      </EmptyState>
    </NCard>

    <NCard class="page-card" :title="t('aiSettings.platformTitle')" :bordered="false">
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

  <AiProviderSetupWizard
    v-model:show="showWizard"
    @completed="onWizardCompleted"
    @changed="load"
  />

  <NModal
    v-model:show="showModal"
    preset="card"
    class="modal-lg"
    :title="editingId ? t('aiSettings.editProvider') : t('aiSettings.addProvider')"
  >
    <NForm label-placement="top">
      <NFormItem :label="t('aiSettings.type')">
        <NSelect v-model:value="form.providerType" :options="typeOptions" @update:value="onTypeChange" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.name')">
        <NInput v-model:value="form.name" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.baseUrl')">
        <NInput v-model:value="form.baseUrl" :placeholder="baseUrlHint" />
      </NFormItem>
      <p class="field-hint">{{ baseUrlHint }}</p>
      <NFormItem :label="t('aiSettings.apiKey')">
        <NInput v-model:value="form.apiKey" type="password" :placeholder="editingId ? t('aiSettings.apiKeyKeep') : ''" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.chatModel')">
        <NSelect v-model:value="form.chatModel" :options="modelOptions" filterable tag />
      </NFormItem>
      <NFormItem :label="t('aiSettings.maxOutputTokens')">
        <NInputNumber v-model:value="form.maxOutputTokens" :min="0" :step="256" class="full-width" />
      </NFormItem>
      <p class="field-hint">{{ t('aiSettings.maxOutputTokensHint') }}</p>
      <NFormItem :label="t('aiSettings.contextWindow')">
        <NInputNumber v-model:value="form.contextWindow" :min="0" :step="1024" class="full-width" />
      </NFormItem>
      <p class="field-hint">{{ t('aiSettings.contextWindowHint') }}</p>
      <NFormItem :label="t('aiSettings.reasoningEffort')">
        <NSelect
          :value="form.reasoningEffort"
          :options="reasoningOptions"
          @update:value="onReasoningChange"
        />
      </NFormItem>
      <p class="field-hint">{{ t('aiSettings.reasoningEffortHint') }}</p>
      <NFormItem v-if="form.providerType === 'OPENAI_COMPAT'" :label="t('aiSettings.embeddingModel')">
        <NInput v-model:value="form.embeddingModel" />
      </NFormItem>
      <NFormItem v-if="form.providerType === 'OPENAI_COMPAT'" :label="t('aiSettings.embeddingDims')">
        <NInputNumber v-model:value="form.embeddingDims" :min="256" :max="4096" class="full-width" />
      </NFormItem>
      <NFormItem v-if="form.providerType === 'OPENAI_COMPAT'" :label="t('aiSettings.supportsEmbedding')">
        <NSwitch v-model:value="form.supportsEmbedding" />
      </NFormItem>
      <NFormItem :label="t('aiSettings.enabled')">
        <NSwitch v-model:value="form.enabled" />
      </NFormItem>
      <NSpace justify="end">
        <NButton :loading="fetchingModels" @click="handleFetchModels">{{ t('aiSettings.fetchModels') }}</NButton>
        <NButton @click="showModal = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSaveProvider">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>
</template>

<style scoped>
.hint {
  color: var(--co-text-secondary);
  font-size: 0.8125rem;
  margin: 0;
  line-height: 1.5;
}

.full-width {
  width: 100%;
}

.field-hint {
  margin: -0.5rem 0 var(--co-space-3);
  color: var(--co-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.setup-prompt__title {
  margin: 0 0 var(--co-space-2);
  font-size: 1rem;
  font-weight: 600;
}

.setup-prompt__desc {
  margin: 0;
  color: var(--co-text-secondary);
  font-size: 0.875rem;
  line-height: 1.5;
}
</style>
