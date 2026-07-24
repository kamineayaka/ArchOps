<script setup lang="ts">
import { computed, nextTick, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NRadio,
  NRadioGroup,
  NSelect,
  NSpace,
  NSteps,
  NStep,
  useMessage,
} from 'naive-ui'
import {
  createProvider,
  fetchProviderModels,
  getModelDefaults,
  testProvider,
  updateAiSettings,
  updateProvider,
  type AiModelInfo,
  type AiProvider,
  type ProviderType,
  type ReasoningEffort,
} from '@/api/ai-providers'
import { applyModelDefaults, canFetchModels } from '@/composables/useAiModelDefaults'
import { apiErrorMessage, isProviderTestFailed } from '@/utils/apiError'

const props = defineProps<{
  show: boolean
}>()

const emit = defineEmits<{
  'update:show': [value: boolean]
  completed: [provider: AiProvider]
  changed: []
}>()

const { t } = useI18n()
const message = useMessage()

const step = ref(0)
const saving = ref(false)
const testing = ref(false)
const fetchingModels = ref(false)
const finishing = ref(false)
const createdId = ref<number | null>(null)
const testStatus = ref<string | null>(null)
const testError = ref<string | null>(null)
const stepError = ref<string | null>(null)
const modelOptions = ref<{ label: string; value: string }[]>([])
const modelInfoById = ref<Record<string, AiModelInfo>>({})
const modelSelectOpen = ref(false)
const lastDefaultsModel = ref('')

const form = ref({
  name: '',
  providerType: 'OPENAI_COMPAT' as ProviderType,
  baseUrl: 'https://api.openai.com/v1',
  apiKey: '',
  chatModel: 'gpt-4o-mini',
  maxOutputTokens: 0,
  contextWindow: 0,
  reasoningEffort: 'NONE' as ReasoningEffort,
})

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

const stepTitles = computed(() => [
  t('aiSettings.wizard.stepType'),
  t('aiSettings.wizard.stepCredentials'),
  t('aiSettings.wizard.stepTest'),
  t('aiSettings.wizard.stepModels'),
])

const canSave = computed(
  () => Boolean(form.value.name.trim() && form.value.baseUrl.trim() && form.value.apiKey.trim()),
)

/** After save, key is stored; step 3 fetch needs name+url+key (or stored). */
const fetchModelsEnabled = computed(() =>
  canFetchModels({
    name: form.value.name,
    baseUrl: form.value.baseUrl,
    apiKey: form.value.apiKey,
    editing: createdId.value != null,
    hasStoredKey: createdId.value != null,
  }),
)

function reset() {
  step.value = 0
  createdId.value = null
  testStatus.value = null
  testError.value = null
  stepError.value = null
  modelOptions.value = []
  modelInfoById.value = {}
  modelSelectOpen.value = false
  lastDefaultsModel.value = ''
  form.value = {
    name: '',
    providerType: 'OPENAI_COMPAT',
    baseUrl: 'https://api.openai.com/v1',
    apiKey: '',
    chatModel: 'gpt-4o-mini',
    maxOutputTokens: 0,
    contextWindow: 0,
    reasoningEffort: 'NONE',
  }
}

watch(
  () => props.show,
  (open) => {
    if (open) reset()
  },
)

function close() {
  onShowUpdate(false)
}

function onShowUpdate(v: boolean) {
  if (!v) {
    const hadDraft = createdId.value != null
    emit('update:show', false)
    if (hadDraft) emit('changed')
    return
  }
  emit('update:show', true)
}

function onTypeChange(type: ProviderType) {
  form.value.providerType = type
  if (type === 'ANTHROPIC') {
    form.value.baseUrl = 'https://api.anthropic.com'
    form.value.chatModel = 'claude-3-5-sonnet-20241022'
    if (!form.value.name.trim()) form.value.name = 'Anthropic'
  } else {
    form.value.baseUrl = 'https://api.openai.com/v1'
    form.value.chatModel = 'gpt-4o-mini'
    if (!form.value.name.trim() || form.value.name === 'Anthropic') {
      form.value.name = 'OpenAI'
    }
    if (form.value.reasoningEffort === 'MAX') {
      form.value.reasoningEffort = 'HIGH'
    }
  }
}

function providerPayload(includeApiKey: boolean) {
  return {
    name: form.value.name.trim(),
    providerType: form.value.providerType,
    baseUrl: form.value.baseUrl.trim(),
    ...(includeApiKey ? { apiKey: form.value.apiKey } : {}),
    chatModel: form.value.chatModel,
    supportsChat: true,
    supportsEmbedding: form.value.providerType === 'OPENAI_COMPAT',
    embeddingModel: form.value.providerType === 'OPENAI_COMPAT' ? 'text-embedding-3-small' : undefined,
    embeddingDims: form.value.providerType === 'OPENAI_COMPAT' ? 1536 : undefined,
    enabled: true,
    maxOutputTokens: form.value.maxOutputTokens ?? 0,
    contextWindow: form.value.contextWindow ?? 0,
    reasoningEffort: form.value.reasoningEffort,
    reasoningEnabled: form.value.reasoningEffort !== 'NONE',
  }
}

async function handleSave() {
  if (!canSave.value) {
    stepError.value = t('aiSettings.wizard.fillRequired')
    return
  }
  saving.value = true
  stepError.value = null
  try {
    const payload = providerPayload(true)
    const res = createdId.value
      ? await updateProvider(createdId.value, payload)
      : await createProvider(payload)
    if (!res.success || !res.data) {
      stepError.value = res.message || t('aiSettings.wizard.saveFailed')
      message.error(stepError.value)
      return
    }
    createdId.value = res.data.id
    testStatus.value = null
    testError.value = null
    message.success(t('aiSettings.saved'))
    step.value = 2
  } catch (err) {
    stepError.value = apiErrorMessage(err, t('aiSettings.wizard.saveFailed'))
    message.error(stepError.value)
  } finally {
    saving.value = false
  }
}

async function handleTest() {
  if (!createdId.value) return
  testing.value = true
  testError.value = null
  testStatus.value = null
  try {
    const res = await testProvider(createdId.value)
    if (!res.success) {
      testError.value = res.message || t('aiSettings.wizard.testFailed')
      message.error(testError.value)
      return
    }
    const status = res.data?.status ?? ''
    testStatus.value = status
    if (isProviderTestFailed(status)) {
      testError.value = status.startsWith('failed:')
        ? status.slice('failed:'.length).trim() || t('aiSettings.wizard.testFailed')
        : status || t('aiSettings.wizard.testFailed')
      message.error(testError.value)
    } else {
      message.success(t('aiSettings.wizard.testOk'))
    }
  } catch (err) {
    testError.value = apiErrorMessage(err, t('aiSettings.wizard.testFailed'))
    message.error(testError.value)
  } finally {
    testing.value = false
  }
}

async function handleFetchModels() {
  if (!createdId.value || !fetchModelsEnabled.value) {
    message.warning(t('aiSettings.apiKeyRequiredForModels'))
    return
  }
  fetchingModels.value = true
  stepError.value = null
  try {
    const res = await fetchProviderModels(createdId.value)
    if (!res.success || !res.data) {
      stepError.value = res.message || t('aiSettings.wizard.modelsFailed')
      message.error(stepError.value)
      return
    }
    const infos = res.data
    modelInfoById.value = Object.fromEntries(infos.map((m) => [m.id, m]))
    modelOptions.value = infos.map((m) => ({ label: m.id, value: m.id }))
    if (infos.length && !infos.some((m) => m.id === form.value.chatModel)) {
      form.value.chatModel = infos[0].id
      await applyDefaultsForModel(infos[0].id)
    }
    message.success(t('aiSettings.modelsLoaded', { count: infos.length }))
    await nextTick()
    modelSelectOpen.value = true
  } catch (err) {
    stepError.value = apiErrorMessage(err, t('aiSettings.wizard.modelsFailed'))
    message.error(stepError.value)
  } finally {
    fetchingModels.value = false
  }
}

async function applyDefaultsForModel(model: string | null | undefined) {
  const id = (model ?? '').trim()
  if (!id || lastDefaultsModel.value === id) return

  const cached = modelInfoById.value[id]
  if (cached && applyModelDefaults(form.value, cached)) {
    lastDefaultsModel.value = id
    message.success(t('aiSettings.appliedModelDefaults'))
    return
  }

  try {
    const res = await getModelDefaults(id)
    if (res.success && res.data && applyModelDefaults(form.value, res.data)) {
      lastDefaultsModel.value = id
      message.success(t('aiSettings.appliedModelDefaults'))
    }
  } catch {
    // silent
  }
}

function onChatModelUpdate(value: string) {
  form.value.chatModel = value
  void applyDefaultsForModel(value)
}

function onChatModelBlur() {
  void applyDefaultsForModel(form.value.chatModel)
}

async function handleFinish() {
  if (!createdId.value) return
  finishing.value = true
  stepError.value = null
  try {
    const updateRes = await updateProvider(createdId.value, providerPayload(false))
    if (!updateRes.success || !updateRes.data) {
      stepError.value = updateRes.message || t('aiSettings.wizard.finishFailed')
      message.error(stepError.value)
      return
    }
    const settingsRes = await updateAiSettings({
      defaultChatProviderId: createdId.value,
    })
    if (!settingsRes.success) {
      stepError.value = settingsRes.message || t('aiSettings.wizard.finishFailed')
      return
    }
    message.success(t('aiSettings.wizard.completed'))
    emit('completed', updateRes.data)
    close()
  } catch (err) {
    stepError.value = apiErrorMessage(err, t('aiSettings.wizard.finishFailed'))
  } finally {
    finishing.value = false
  }
}
</script>

<template>
  <NModal
    :show="show"
    preset="card"
    class="modal-lg wizard-modal"
    :title="t('aiSettings.wizard.title')"
    :mask-closable="false"
    @update:show="onShowUpdate"
  >
    <p class="wizard-desc">{{ t('aiSettings.wizard.description') }}</p>

    <NSteps :current="step + 1" size="small" class="wizard-steps">
      <NStep v-for="(title, i) in stepTitles" :key="i" :title="title" />
    </NSteps>

    <NAlert v-if="stepError" type="error" class="wizard-alert" :title="t('aiSettings.wizard.errorTitle')">
      {{ stepError }}
    </NAlert>

    <!-- Step 0: type -->
    <div v-if="step === 0" class="wizard-body">
      <NRadioGroup :value="form.providerType" @update:value="onTypeChange">
        <NSpace vertical>
          <NRadio value="OPENAI_COMPAT">
            <span class="type-label">{{ t('aiSettings.openAiCompat') }}</span>
            <span class="type-hint">{{ t('aiSettings.wizard.typeOpenAiHint') }}</span>
          </NRadio>
          <NRadio value="ANTHROPIC">
            <span class="type-label">{{ t('aiSettings.anthropic') }}</span>
            <span class="type-hint">{{ t('aiSettings.wizard.typeAnthropicHint') }}</span>
          </NRadio>
        </NSpace>
      </NRadioGroup>
      <NSpace justify="end" class="wizard-actions">
        <NButton @click="close">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="step = 1">{{ t('aiSettings.wizard.next') }}</NButton>
      </NSpace>
    </div>

    <!-- Step 1: credentials only (name → URL → Key) -->
    <div v-else-if="step === 1" class="wizard-body">
      <NForm label-placement="top">
        <NFormItem :label="t('aiSettings.name')" required>
          <NInput v-model:value="form.name" :placeholder="t('aiSettings.wizard.namePlaceholder')" />
        </NFormItem>
        <NFormItem :label="t('aiSettings.baseUrl')" required>
          <NInput v-model:value="form.baseUrl" />
        </NFormItem>
        <NFormItem :label="t('aiSettings.apiKey')" required>
          <NInput v-model:value="form.apiKey" type="password" show-password-on="click" />
        </NFormItem>
      </NForm>
      <NSpace justify="space-between" class="wizard-actions">
        <NButton @click="step = 0">{{ t('aiSettings.wizard.back') }}</NButton>
        <NSpace>
          <NButton @click="close">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="saving" :disabled="!canSave" @click="handleSave">
            {{ t('aiSettings.wizard.saveAndContinue') }}
          </NButton>
        </NSpace>
      </NSpace>
    </div>

    <!-- Step 2: test -->
    <div v-else-if="step === 2" class="wizard-body">
      <p class="wizard-hint">{{ t('aiSettings.wizard.testHint') }}</p>
      <NAlert
        v-if="testError"
        type="error"
        class="wizard-alert"
        :title="t('aiSettings.wizard.testFailed')"
      >
        {{ testError }}
      </NAlert>
      <NAlert
        v-else-if="testStatus && !isProviderTestFailed(testStatus)"
        type="success"
        class="wizard-alert"
        :title="t('aiSettings.wizard.testOk')"
      >
        {{ testStatus }}
      </NAlert>
      <NSpace justify="space-between" class="wizard-actions">
        <NButton @click="step = 1">{{ t('aiSettings.wizard.back') }}</NButton>
        <NSpace>
          <NButton :loading="testing" @click="handleTest">{{ t('aiSettings.test') }}</NButton>
          <NButton
            type="primary"
            :disabled="!testStatus || isProviderTestFailed(testStatus)"
            @click="step = 3"
          >
            {{ t('aiSettings.wizard.next') }}
          </NButton>
        </NSpace>
      </NSpace>
    </div>

    <!-- Step 3: fetch models → select → params (reasoning not overwritten by select) -->
    <div v-else class="wizard-body">
      <p class="wizard-hint">{{ t('aiSettings.wizard.modelsHint') }}</p>
      <NForm label-placement="top">
        <NFormItem :label="t('aiSettings.fetchModels')">
          <NSpace vertical :size="4" style="width: 100%">
            <NButton
              :loading="fetchingModels"
              :disabled="!fetchModelsEnabled"
              @click="handleFetchModels"
            >
              {{ t('aiSettings.fetchModels') }}
            </NButton>
            <p v-if="!fetchModelsEnabled" class="field-hint field-hint--inline">
              {{ t('aiSettings.fetchModelsGateHint') }}
            </p>
          </NSpace>
        </NFormItem>
        <NFormItem :label="t('aiSettings.chatModel')">
          <NSelect
            :value="form.chatModel"
            :options="modelOptions"
            filterable
            tag
            :show="modelSelectOpen"
            :placeholder="t('aiSettings.wizard.modelPlaceholder')"
            @update:show="(v: boolean) => (modelSelectOpen = v)"
            @update:value="onChatModelUpdate"
            @blur="onChatModelBlur"
          />
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
          <NSelect v-model:value="form.reasoningEffort" :options="reasoningOptions" />
        </NFormItem>
        <p class="field-hint">{{ t('aiSettings.reasoningEffortHint') }}</p>
      </NForm>
      <NSpace justify="space-between" class="wizard-actions">
        <NButton @click="step = 2">{{ t('aiSettings.wizard.back') }}</NButton>
        <NButton type="primary" :loading="finishing" @click="handleFinish">
          {{ t('aiSettings.wizard.finish') }}
        </NButton>
      </NSpace>
    </div>
  </NModal>
</template>

<style scoped>
.wizard-desc {
  margin: 0 0 var(--co-space-4);
  color: var(--co-text-secondary);
  font-size: 0.875rem;
  line-height: 1.5;
}

.wizard-steps {
  margin-bottom: var(--co-space-4);
}

.wizard-body {
  min-height: 8rem;
}

.wizard-hint {
  margin: 0 0 var(--co-space-3);
  color: var(--co-text-secondary);
  font-size: 0.875rem;
}

.wizard-alert {
  margin-bottom: var(--co-space-3);
}

.wizard-actions {
  margin-top: var(--co-space-4);
}

.type-label {
  font-weight: 600;
  margin-right: var(--co-space-2);
}

.type-hint {
  color: var(--co-text-muted);
  font-size: 0.8125rem;
}

.field-hint {
  margin: -0.5rem 0 var(--co-space-3);
  color: var(--co-text-muted);
  font-size: 0.75rem;
  line-height: 1.4;
}

.field-hint--inline {
  margin: 0;
}

.full-width {
  width: 100%;
}
</style>
