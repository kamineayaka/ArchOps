<script setup lang="ts">
import { t } from '@/messages'
import { computed, ref, watch } from 'vue'
import type { SelectOption } from 'naive-ui'
import {
  NAlert,
  NButton,
  NCheckbox,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NRadioButton,
  NRadioGroup,
  NSelect,
  NSpace,
  NTabPane,
  NTabs,
  useMessage,
} from 'naive-ui'
import {
  createAsset,
  testAssetConnection,
  type Asset,
  type AssetRequest,
} from '@/api/assets'
import {
  findOrCreateAssetGroup,
  listAssetGroups,
  type AssetGroup,
} from '@/api/assetGroups'
import '@/assetTypes'
import { defaultPortFor, getAssetType, listAssetTypes } from '@/assetTypes/registry'
import {
  readKeyFiles,
  scanSshDirectory,
  supportsDirectoryPicker,
  type LocalSshKeyEntry,
} from '@/composables/localSshKeys'
import { apiErrorMessage } from '@/utils/apiError'

export interface AssetCreateFormModel {
  name: string
  kind: string
  host: string
  port: number | null
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

const message = useMessage()

const groups = ref<AssetGroup[]>([])
/** 已有分组 id，或待创建的分组名（string） */
const groupSelection = ref<number | string | null>(null)
/** 下拉过滤框当前输入（用于无匹配时保存即新建） */
const groupSearch = ref('')
const saving = ref(false)
const testing = ref(false)
const activeTab = ref('connection')
const keySource = ref<'browse'>('browse')
const discoveredKeys = ref<LocalSshKeyEntry[]>([])
const selectedKeyId = ref<string | null>(null)
const fileInputRef = ref<HTMLInputElement | null>(null)
const canPickDir = supportsDirectoryPicker()

const form = ref<AssetCreateFormModel>({
  name: '',
  kind: props.initialKind,
  host: '',
  port: defaultPortFor(props.initialKind),
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
const showTunnelTab = computed(() => showAuth.value)

const groupOptions = computed(() => {
  const opts: { label: string; value: number | string }[] = groups.value.map((g) => ({
    label: g.name,
    value: g.id,
  }))
  if (typeof groupSelection.value === 'string' && groupSelection.value.trim()) {
    const name = groupSelection.value.trim()
    if (!opts.some((o) => String(o.label).toLowerCase() === name.toLowerCase())) {
      opts.unshift({ label: `${name}（新建）`, value: name })
    }
  }
  return opts
})

const jumpAssetOptions = computed(() =>
  props.assets
    .filter((a) => a.hasSshCredential)
    .map((a) => ({
      label: `${a.name} (#${a.id})`,
      value: a.id,
    })),
)

const testBlockReason = computed(() => {
  if (!supportsTest.value) return ''
  if (!form.value.name.trim()) return t('assets.nameRequired')
  if (selectedType.value?.showHost !== false && !form.value.host.trim()) {
    return t('assets.hostRequired')
  }
  if (form.value.port == null || form.value.port < 1) return t('assets.portRequired')
  if (showAuth.value) {
    if (!form.value.username.trim()) return t('assets.usernameRequired')
    if (!form.value.secret.trim()) {
      return form.value.authType === 'PRIVATE_KEY'
        ? t('assets.keyRequired')
        : t('assets.passwordRequired')
    }
  }
  return ''
})

const canTest = computed(() => supportsTest.value && !testBlockReason.value)

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
    activeTab.value = 'connection'
  },
)

watch(
  () => form.value.authType,
  () => {
    form.value.secret = ''
    selectedKeyId.value = null
  },
)

async function loadGroups() {
  const res = await listAssetGroups()
  if (res.success && res.data) groups.value = res.data
}

void loadGroups()

function filterGroup(pattern: string, option: SelectOption): boolean {
  const raw = option.label
  const label = typeof raw === 'string' ? raw : String(option.value ?? '')
  return label.toLowerCase().includes(pattern.trim().toLowerCase())
}

function onGroupCreate(label: string): SelectOption {
  const name = label.trim() || label
  groupSelection.value = name
  return { label: `${name}（新建）`, value: name }
}

function onGroupSearch(query: string) {
  groupSearch.value = query
}

async function resolveGroupId(): Promise<number | undefined> {
  let pending: number | string | null = groupSelection.value
  if ((pending == null || pending === '') && groupSearch.value.trim()) {
    pending = groupSearch.value.trim()
  }
  if (pending == null || pending === '') return undefined
  if (typeof pending === 'number') return pending
  const name = String(pending).trim()
  if (!name) return undefined
  const res = await findOrCreateAssetGroup(name)
  if (!res.success || !res.data) {
    throw new Error(res.message || t('assets.groupCreateFailed'))
  }
  groups.value = [
    res.data,
    ...groups.value.filter((g) => g.id !== res.data!.id),
  ]
  groupSelection.value = res.data.id
  groupSearch.value = ''
  return res.data.id
}

function toPayload(groupId?: number): AssetRequest {
  const payload: AssetRequest = {
    name: form.value.name.trim(),
    kind: form.value.kind,
    host: form.value.host.trim() || undefined,
    port: form.value.port ?? undefined,
    description: form.value.description.trim() || undefined,
    groupId,
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
  if (!canTest.value) {
    if (testBlockReason.value) message.warning(testBlockReason.value)
    return
  }
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
  if (!canTest.value && supportsTest.value) {
    if (testBlockReason.value) message.warning(testBlockReason.value)
    return
  }
  if (!form.value.name.trim()) {
    message.warning(t('assets.nameRequired'))
    return
  }
  if (selectedType.value?.showHost !== false && !form.value.host.trim()) {
    message.warning(t('assets.hostRequired'))
    return
  }
  if (showAuth.value && (!form.value.username.trim() || !form.value.secret.trim())) {
    message.warning(t('assets.secretRequired'))
    return
  }
  saving.value = true
  try {
    const groupId = await resolveGroupId()
    const res = await createAsset(toPayload(groupId))
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

function selectDiscoveredKey(entry: LocalSshKeyEntry, checked: boolean) {
  if (checked) {
    selectedKeyId.value = entry.id
    form.value.secret = entry.content
  } else if (selectedKeyId.value === entry.id) {
    selectedKeyId.value = null
    form.value.secret = ''
  }
}

async function handleScanSshDir() {
  try {
    const keys = await scanSshDirectory()
    discoveredKeys.value = keys
    if (keys.length === 0) {
      message.info(t('assets.noLocalKeys'))
    } else {
      message.success(t('assets.localKeysFound', { count: keys.length }))
    }
  } catch (err) {
    if (err instanceof Error && err.message === 'DIRECTORY_PICKER_UNSUPPORTED') {
      message.warning(t('assets.dirPickerUnsupported'))
      return
    }
    if (err instanceof DOMException && err.name === 'AbortError') return
    message.error(apiErrorMessage(err, t('assets.scanKeysFailed')))
  }
}

function openFilePicker() {
  fileInputRef.value?.click()
}

async function onFilePicked(ev: Event) {
  const input = ev.target as HTMLInputElement
  if (!input.files?.length) return
  try {
    const keys = await readKeyFiles(input.files)
    if (keys.length === 0) {
      message.warning(t('assets.invalidKeyFile'))
      return
    }
    const merged = [...discoveredKeys.value]
    for (const k of keys) {
      if (!merged.some((m) => m.id === k.id)) merged.push(k)
    }
    discoveredKeys.value = merged
    selectDiscoveredKey(keys[0], true)
    message.success(t('assets.keyFileLoaded', { name: keys[0].name }))
  } catch (err) {
    message.error(apiErrorMessage(err, t('assets.scanKeysFailed')))
  } finally {
    input.value = ''
  }
}
</script>

<template>
  <NForm :model="form" label-placement="top" class="ssh-asset-form">
    <div class="header-grid">
      <NFormItem :label="t('assets.kind')" class="span-full">
        <NSelect v-model:value="form.kind" :options="kindOptions" />
      </NFormItem>
      <NFormItem :label="t('assets.name')" required>
        <NInput
          v-model:value="form.name"
          :placeholder="t('assets.namePlaceholder')"
          spellcheck="false"
        />
      </NFormItem>
      <NFormItem :label="t('assets.group')">
        <NSelect
          v-model:value="groupSelection"
          :options="groupOptions"
          filterable
          tag
          clearable
          :placeholder="t('assets.groupFilterPlaceholder')"
          :filter="filterGroup"
          :on-create="onGroupCreate"
          @search="onGroupSearch"
        />
      </NFormItem>
    </div>

    <NTabs v-model:value="activeTab" type="line" animated>
      <NTabPane name="connection" :tab="t('assets.tabConnection')">
        <div class="host-port-row">
          <NFormItem
            v-if="selectedType?.showHost !== false"
            :label="t('assets.host')"
            required
            class="host-field"
          >
            <NInput
              v-model:value="form.host"
              :placeholder="t('assets.hostPlaceholder')"
              spellcheck="false"
            />
          </NFormItem>
          <NFormItem
            v-if="selectedType?.showPort !== false"
            :label="t('assets.port')"
            class="port-field"
          >
            <NInputNumber
              v-model:value="form.port"
              :min="1"
              :max="65535"
              class="full-width"
            />
          </NFormItem>
        </div>

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
          <NFormItem
            :label="showPasswordAuth ? t('assets.dbUser') : t('assets.sshUser')"
            required
          >
            <NInput v-model:value="form.username" spellcheck="false" :placeholder="'root'" />
          </NFormItem>

          <NFormItem v-if="showSsh" :label="t('assets.authType')">
            <NRadioGroup v-model:value="form.authType" size="medium">
              <NRadioButton value="PASSWORD">{{ t('assets.password') }}</NRadioButton>
              <NRadioButton value="PRIVATE_KEY">{{ t('assets.privateKey') }}</NRadioButton>
            </NRadioGroup>
          </NFormItem>

          <NFormItem
            v-if="!showSsh || form.authType === 'PASSWORD'"
            :label="t('assets.password')"
            required
          >
            <NInput
              v-model:value="form.secret"
              type="password"
              show-password-on="click"
              :placeholder="t('assets.passwordLeaveBlankHint')"
            />
          </NFormItem>

          <template v-else>
            <NFormItem :label="t('assets.keySource')">
              <NRadioGroup v-model:value="keySource" size="medium">
                <NRadioButton value="browse">{{ t('assets.keySourceLocal') }}</NRadioButton>
              </NRadioGroup>
            </NFormItem>

            <div class="key-panel">
              <div class="key-panel-title">{{ t('assets.discoveredKeys') }}</div>
              <div v-if="discoveredKeys.length === 0" class="key-empty">
                {{ t('assets.discoveredKeysEmpty') }}
              </div>
              <div
                v-for="entry in discoveredKeys"
                :key="entry.id"
                class="key-row"
              >
                <NCheckbox
                  :checked="selectedKeyId === entry.id"
                  @update:checked="(v) => selectDiscoveredKey(entry, v)"
                >
                  <span class="key-path">{{ entry.pathLabel }}</span>
                  <span class="key-fp">{{ entry.fingerprint }}</span>
                </NCheckbox>
              </div>
              <NSpace :size="8" class="key-actions">
                <NButton v-if="canPickDir" secondary @click="handleScanSshDir">
                  {{ t('assets.scanSshDir') }}
                </NButton>
                <NButton secondary @click="openFilePicker">
                  {{ t('assets.browseKeyFile') }}
                </NButton>
              </NSpace>
              <input
                ref="fileInputRef"
                type="file"
                class="hidden-file"
                multiple
                @change="onFilePicked"
              />
              <NFormItem :label="t('assets.privateKeyPreview')" class="key-preview">
                <NInput
                  v-model:value="form.secret"
                  type="textarea"
                  :rows="3"
                  :placeholder="t('assets.privateKeyPlaceholder')"
                />
              </NFormItem>
            </div>
          </template>
        </template>
      </NTabPane>

      <NTabPane
        v-if="showTunnelTab"
        name="tunnel"
        :tab="t('assets.tabTunnel')"
        :disabled="!showTunnelTab"
      >
        <NAlert type="info" :bordered="false" class="tab-alert">
          {{ showPasswordAuth ? t('assets.jumpChainDbHint') : t('assets.jumpChainHint') }}
        </NAlert>
        <NFormItem :label="t('assets.jumpChain')">
          <NSelect
            v-model:value="form.jumpAssetIds"
            :options="jumpAssetOptions"
            multiple
            filterable
            clearable
            :placeholder="t('assets.jumpChainPlaceholder')"
          />
        </NFormItem>
        <p class="field-hint">{{ t('assets.tunnelEmptyMeansDirect') }}</p>
      </NTabPane>

      <NTabPane name="advanced" :tab="t('assets.tabAdvanced')">
        <NFormItem :label="t('assets.description')">
          <NSpace vertical :size="4" class="full-width">
            <NInput
              v-model:value="form.description"
              type="textarea"
              :rows="3"
              :placeholder="t('assets.descriptionPlaceholder')"
            />
            <span class="field-hint">{{ t('assets.descriptionHint') }}</span>
            <NButton text type="primary" size="small" disabled>
              {{ t('assets.addRemarkSoon') }}
            </NButton>
          </NSpace>
        </NFormItem>
      </NTabPane>
    </NTabs>

    <div class="form-footer">
      <div class="test-block">
        <NButton
          v-if="supportsTest"
          :loading="testing"
          :disabled="!canTest"
          @click="handleTest"
        >
          {{ t('assets.testConnection') }}
        </NButton>
        <div v-if="supportsTest && testBlockReason" class="test-hint">
          <span class="warn-icon">⚠</span>
          {{ testBlockReason }}
        </div>
      </div>
      <NSpace>
        <NButton @click="emit('cancel')">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" :loading="saving" @click="handleSubmit">
          {{ t('common.save') }}
        </NButton>
      </NSpace>
    </div>
  </NForm>
</template>

<style scoped>
.ssh-asset-form {
  min-width: 0;
}
.header-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 0 12px;
}
.span-full {
  grid-column: 1 / -1;
}
.host-port-row {
  display: flex;
  gap: 12px;
  align-items: flex-start;
}
.host-field {
  flex: 1;
  min-width: 0;
}
.port-field {
  width: 120px;
  flex-shrink: 0;
}
.full-width {
  width: 100%;
}
.field-hint {
  font-size: 12px;
  color: var(--n-text-color-3, #999);
  line-height: 1.4;
}
.key-panel {
  border: 1px solid var(--n-border-color, #333);
  border-radius: 6px;
  padding: 12px;
  margin-bottom: 12px;
}
.key-panel-title {
  font-size: 13px;
  margin-bottom: 8px;
  color: var(--n-text-color-2, #aaa);
}
.key-empty {
  font-size: 12px;
  color: var(--n-text-color-3, #888);
  margin-bottom: 8px;
}
.key-row {
  margin-bottom: 6px;
}
.key-path {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 12px;
  margin-right: 8px;
}
.key-fp {
  font-size: 11px;
  color: var(--n-text-color-3, #888);
}
.key-actions {
  margin: 8px 0;
}
.key-preview {
  margin-top: 8px;
  margin-bottom: 0;
}
.hidden-file {
  display: none;
}
.tab-alert {
  margin-bottom: 12px;
}
.form-footer {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--n-border-color, #333);
}
.test-block {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.test-hint {
  font-size: 12px;
  color: #e6a23c;
  display: flex;
  align-items: center;
  gap: 4px;
}
.warn-icon {
  flex-shrink: 0;
}
@media (max-width: 640px) {
  .header-grid {
    grid-template-columns: 1fr;
  }
  .host-port-row {
    flex-direction: column;
  }
  .port-field {
    width: 100%;
  }
  .form-footer {
    flex-direction: column;
  }
}
</style>
