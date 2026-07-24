<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  useMessage,
} from 'naive-ui'
import {
  createAsset,
  testAssetConnection,
  type Asset,
  type AssetRequest,
} from '@/api/assets'
import { listAssetGroups, type AssetGroup } from '@/api/assetGroups'
import '@/assetTypes'
import { defaultPortFor, getAssetType, listAssetTypes } from '@/assetTypes/registry'
import { apiErrorMessage } from '@/utils/apiError'

export interface AssetCreateFormModel {
  name: string
  kind: string
  host: string
  port: number | null
  groupId: number | null
  description: string
  database: string
  username: string
  authType: 'PASSWORD' | 'PRIVATE_KEY'
  secret: string
  jumpAssetIds: number[]
}

const props = withDefaults(
  defineProps<{
    assets: Asset[]
    initialKind?: string
  }>(),
  { initialKind: 'SERVER' },
)

const emit = defineEmits<{
  created: [asset: Asset]
  cancel: []
}>()

const { t } = useI18n()
const message = useMessage()

const groups = ref<AssetGroup[]>([])
const saving = ref(false)
const testing = ref(false)

const form = ref<AssetCreateFormModel>({
  name: '',
  kind: props.initialKind,
  host: '',
  port: defaultPortFor(props.initialKind),
  groupId: null,
  description: '',
  database: '',
  username: 'root',
  authType: 'PASSWORD',
  secret: '',
  jumpAssetIds: [],
})

const kindOptions = computed(() =>
  listAssetTypes().map((def) => ({
    label: t(def.labelKey),
    value: def.kind,
  })),
)

const selectedType = computed(() => getAssetType(form.value.kind))
const showSsh = computed(() => selectedType.value?.authMode === 'ssh')
const showPasswordAuth = computed(() => selectedType.value?.authMode === 'password')
const showAuth = computed(() => showSsh.value || showPasswordAuth.value)
const showDatabaseName = computed(() => Boolean(selectedType.value?.showDatabaseName))
const supportsTest = computed(() => Boolean(selectedType.value?.supportsTest))
const showJump = computed(() => showAuth.value)

const groupOptions = computed(() =>
  groups.value.map((g) => ({
    label: g.name,
    value: g.id,
  })),
)

const connectionMode = ref<'direct' | 'jump'>('direct')

const jumpAssetOptions = computed(() =>
  props.assets
    .filter((a) => a.hasSshCredential)
    .map((a) => ({
      label: `${a.name} (#${a.id})`,
      value: a.id,
    })),
)

watch(connectionMode, (mode) => {
  if (mode === 'direct') form.value.jumpAssetIds = []
})

watch(
  () => form.value.kind,
  (kind) => {
    form.value.port = defaultPortFor(kind)
    const def = getAssetType(kind)
    if (def?.authMode === 'password') {
      form.value.username = 'postgres'
      form.value.authType = 'PASSWORD'
    } else if (def?.authMode === 'ssh') {
      form.value.username = 'root'
    }
  },
)

async function loadGroups() {
  const res = await listAssetGroups()
  if (res.success && res.data) groups.value = res.data
}

void loadGroups()

function validateBasics(): boolean {
  if (!form.value.name.trim()) {
    message.warning(t('assets.nameRequired'))
    return false
  }
  if (selectedType.value?.showHost !== false && !form.value.host.trim()) {
    message.warning(t('assets.hostRequired'))
    return false
  }
  if (showAuth.value) {
    if (!form.value.username.trim()) {
      message.warning(t('assets.usernameRequired'))
      return false
    }
    if (!form.value.secret.trim()) {
      message.warning(t('assets.secretRequired'))
      return false
    }
  }
  return true
}

function toPayload(): AssetRequest {
  const payload: AssetRequest = {
    name: form.value.name.trim(),
    kind: form.value.kind,
    host: form.value.host.trim() || undefined,
    port: form.value.port ?? undefined,
    description: form.value.description.trim() || undefined,
    groupId: form.value.groupId ?? undefined,
  }
  if (showDatabaseName.value && form.value.database.trim()) {
    payload.database = form.value.database.trim()
  }
  if (showAuth.value) {
    payload.username = form.value.username.trim()
    payload.authType = showSsh.value ? form.value.authType : 'PASSWORD'
    payload.secret = form.value.secret
    payload.jumpAssetIds = form.value.jumpAssetIds
  }
  return payload
}

async function handleTest() {
  if (!validateBasics()) return
  testing.value = true
  try {
    const res = await testAssetConnection({
      kind: form.value.kind,
      host: form.value.host.trim(),
      port: form.value.port ?? selectedType.value?.defaultPort ?? undefined,
      username: form.value.username.trim() || undefined,
      authType: showSsh.value ? form.value.authType : 'PASSWORD',
      secret: form.value.secret || undefined,
      jumpAssetIds: form.value.jumpAssetIds,
      database: form.value.database.trim() || undefined,
    })
    if (res.success && res.data?.ok) {
      message.success(`${res.data.message} (${res.data.latencyMs}ms)`)
    } else {
      message.error(res.data?.message || res.message || t('assets.testFailed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('assets.testFailed')))
  } finally {
    testing.value = false
  }
}

async function handleSubmit() {
  if (!validateBasics()) return
  saving.value = true
  try {
    const res = await createAsset(toPayload())
    if (res.success && res.data) {
      message.success(
        showAuth.value ? t('assets.createdConnectable') : t('assets.created'),
      )
      emit('created', res.data)
    } else {
      message.error(res.message || t('common.failed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('common.failed')))
  } finally {
    saving.value = false
  }
}
</script>

<template>
  <NForm :model="form" label-placement="top">
    <NFormItem :label="t('assets.name')" required>
      <NInput v-model:value="form.name" :placeholder="t('assets.namePlaceholder')" />
    </NFormItem>
    <NFormItem :label="t('assets.kind')">
      <NSelect v-model:value="form.kind" :options="kindOptions" />
    </NFormItem>
    <NFormItem :label="t('assets.group')">
      <NSelect
        v-model:value="form.groupId"
        :options="groupOptions"
        clearable
        filterable
        :placeholder="t('assets.groupPlaceholder')"
      />
    </NFormItem>
    <NFormItem v-if="selectedType?.showHost !== false" :label="t('assets.host')" required>
      <NInput v-model:value="form.host" :placeholder="t('assets.hostPlaceholder')" />
    </NFormItem>
    <NFormItem v-if="selectedType?.showPort !== false" :label="t('assets.port')">
      <NInputNumber v-model:value="form.port" :min="1" :max="65535" class="full-width" />
    </NFormItem>

    <NFormItem v-if="showDatabaseName" :label="t('assets.databaseName')">
      <NSpace vertical :size="4" class="full-width">
        <NInput
          v-model:value="form.database"
          :placeholder="t('assets.databaseNamePlaceholder')"
          spellcheck="false"
        />
        <span class="field-hint">{{ t('assets.databaseNameHint') }}</span>
      </NSpace>
    </NFormItem>

    <template v-if="showAuth">
      <NFormItem v-if="showJump" :label="t('assets.connectionMode')">
        <NRadioGroup v-model:value="connectionMode">
          <NRadioButton value="direct">{{ t('assets.directConnect') }}</NRadioButton>
          <NRadioButton value="jump">{{ t('assets.jumpConnect') }}</NRadioButton>
        </NRadioGroup>
      </NFormItem>
      <NFormItem v-if="showJump && connectionMode === 'jump'" :label="t('assets.jumpChain')">
        <NSpace vertical :size="4" class="full-width">
          <NSelect
            v-model:value="form.jumpAssetIds"
            :options="jumpAssetOptions"
            multiple
            filterable
            clearable
            :placeholder="t('assets.jumpChainPlaceholder')"
          />
          <span class="field-hint">{{
            showPasswordAuth ? t('assets.jumpChainDbHint') : t('assets.jumpChainHint')
          }}</span>
        </NSpace>
      </NFormItem>
      <NFormItem :label="showPasswordAuth ? t('assets.dbUser') : t('assets.sshUser')" required>
        <NInput v-model:value="form.username" spellcheck="false" />
      </NFormItem>
      <NFormItem v-if="showSsh" :label="t('assets.authType')">
        <NSelect
          v-model:value="form.authType"
          :options="[
            { label: t('assets.password'), value: 'PASSWORD' },
            { label: t('assets.privateKey'), value: 'PRIVATE_KEY' },
          ]"
        />
      </NFormItem>
      <NFormItem
        :label="
          showSsh && form.authType === 'PRIVATE_KEY' ? t('assets.privateKey') : t('assets.password')
        "
        required
      >
        <NInput
          v-model:value="form.secret"
          :type="showSsh && form.authType === 'PRIVATE_KEY' ? 'textarea' : 'password'"
          :rows="showSsh && form.authType === 'PRIVATE_KEY' ? 4 : undefined"
          show-password-on="click"
          :placeholder="
            showSsh && form.authType === 'PRIVATE_KEY'
              ? t('assets.privateKeyPlaceholder')
              : t('assets.passwordPlaceholder')
          "
        />
      </NFormItem>
    </template>

    <NFormItem :label="t('assets.description')">
      <NSpace vertical :size="4" class="full-width">
        <NInput
          v-model:value="form.description"
          type="textarea"
          :rows="2"
          :placeholder="t('assets.descriptionPlaceholder')"
        />
        <span class="field-hint">{{ t('assets.descriptionHint') }}</span>
      </NSpace>
    </NFormItem>

    <NSpace justify="space-between" class="form-actions">
      <NButton v-if="supportsTest" :loading="testing" @click="handleTest">
        {{ t('assets.testConnection') }}
      </NButton>
      <span v-else />
      <NSpace>
        <NButton @click="emit('cancel')">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="saving" @click="handleSubmit">
          {{ t('common.create') }}
        </NButton>
      </NSpace>
    </NSpace>
  </NForm>
</template>

<style scoped>
.full-width {
  width: 100%;
}
.field-hint {
  font-size: 12px;
  color: var(--n-text-color-3, #999);
  line-height: 1.4;
}
.form-actions {
  margin-top: 8px;
}
</style>
