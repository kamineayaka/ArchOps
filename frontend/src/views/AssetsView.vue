<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import { useRouter } from 'vue-router'
import {
  NButton,
  NCard,
  NDataTable,
  NForm,
  NFormItem,
  NInput,
  NModal,
  NPopconfirm,
  NSelect,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui'
import {
  deleteAsset,
  listAssets,
  saveSshCredential,
  testSavedAssetConnection,
  type Asset,
} from '@/api/assets'
import '@/assetTypes'
import { getAssetType } from '@/assetTypes/registry'
import { openAsset } from '@/assetTypes/openAsset'
import SshAssetForm from '@/components/assets/SshAssetForm.vue'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { apiErrorMessage } from '@/utils/apiError'

const { t } = useI18n()
const message = useMessage()
const router = useRouter()

const assets = ref<Asset[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showCredential = ref(false)
const selectedAssetId = ref<number | null>(null)
const testingId = ref<number | null>(null)

const credForm = ref({
  username: 'root',
  authType: 'PASSWORD',
  secret: '',
  jumpAssetIds: [] as number[],
})

const jumpAssetOptions = computed(() =>
  assets.value
    .filter((a) => a.hasSshCredential && a.id !== selectedAssetId.value)
    .map((a) => ({
      label: `${a.name} (#${a.id})`,
      value: a.id,
    })),
)

function kindLabel(kind: string) {
  const def = getAssetType(kind)
  return def ? t(def.labelKey) : kind
}

function canTest(row: Asset) {
  const def = getAssetType(row.kind)
  if (!def?.supportsTest) return false
  if (def.connectAction === 'terminal') return row.hasSshCredential
  return true
}

const columns = computed(() => [
  { title: t('common.id'), key: 'id', width: 60 },
  { title: t('assets.name'), key: 'name' },
  {
    title: t('assets.kind'),
    key: 'kind',
    width: 120,
    render: (row: Asset) =>
      h(NTag, { size: 'small', round: true }, { default: () => kindLabel(row.kind) }),
  },
  { title: t('assets.host'), key: 'host' },
  { title: t('assets.port'), key: 'port', width: 80 },
  {
    title: t('assets.credential'),
    key: 'hasSshCredential',
    width: 110,
    render: (row: Asset) =>
      h(
        NTag,
        { size: 'small', round: true, type: row.hasSshCredential ? 'success' : 'warning' },
        {
          default: () =>
            row.hasSshCredential ? t('assets.connectable') : t('common.notConfigured'),
        },
      ),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 340,
    render: (row: Asset) =>
      h(NSpace, { size: 8 }, {
        default: () => [
          h(
            NButton,
            {
              size: 'small',
              onClick: () => openAsset(row, { router, message, t }),
            },
            { default: () => t('assets.connect') },
          ),
          h(
            NButton,
            {
              size: 'small',
              loading: testingId.value === row.id,
              disabled: !canTest(row),
              onClick: () => void handleTestSaved(row.id),
            },
            { default: () => t('assets.testConnection') },
          ),
          h(NButton, { size: 'small', onClick: () => openCredential(row) }, { default: () => t('assets.credential') }),
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

async function load() {
  loading.value = true
  try {
    const res = await listAssets()
    if (res.success && res.data) assets.value = res.data
  } finally {
    loading.value = false
  }
}

function onCreated() {
  showCreate.value = false
  void load()
}

function openCredential(asset: Asset) {
  selectedAssetId.value = asset.id
  const def = getAssetType(asset.kind)
  credForm.value = {
    username: def?.authMode === 'password' ? 'postgres' : 'root',
    authType: 'PASSWORD',
    secret: '',
    jumpAssetIds: asset.jumpAssetIds ? [...asset.jumpAssetIds] : [],
  }
  showCredential.value = true
}

async function handleSaveCredential() {
  if (!selectedAssetId.value) return
  if (!credForm.value.username.trim() || !credForm.value.secret.trim()) {
    message.warning(t('assets.secretRequired'))
    return
  }
  try {
    const res = await saveSshCredential(selectedAssetId.value, credForm.value)
    if (res.success) {
      message.success(t('assets.credentialSaved'))
      showCredential.value = false
      await load()
    } else {
      message.error(res.message || t('common.failed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('common.failed')))
  }
}

async function handleTestSaved(id: number) {
  testingId.value = id
  try {
    const res = await testSavedAssetConnection(id)
    if (res.success && res.data?.ok) {
      message.success(`${res.data.message} (${res.data.latencyMs}ms)`)
    } else {
      message.error(res.data?.message || res.message || t('assets.testFailed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('assets.testFailed')))
  } finally {
    testingId.value = null
  }
}

async function handleDelete(id: number) {
  try {
    const res = await deleteAsset(id)
    if (res.success) {
      message.success(t('assets.deleted'))
      await load()
    } else {
      message.error(res.message || t('common.failed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('common.failed')))
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('assets.title')" :description="t('assets.subtitle')">
      <template #extra>
        <NSpace>
          <NButton @click="load">{{ t('common.refresh') }}</NButton>
          <NButton type="primary" @click="showCreate = true">{{ t('assets.addAsset') }}</NButton>
        </NSpace>
      </template>
    </PageHeader>

    <NCard class="page-card" :bordered="false">
      <NDataTable :columns="columns" :data="assets" :loading="loading" :bordered="false" />
      <EmptyState v-if="!loading && assets.length === 0" :message="t('assets.empty')" />
    </NCard>
  </NSpace>

  <NModal v-model:show="showCreate" preset="card" class="modal-lg" :title="t('assets.addAsset')">
    <SshAssetForm
      :assets="assets"
      @created="onCreated"
      @cancel="showCreate = false"
    />
  </NModal>

  <NModal v-model:show="showCredential" preset="card" class="modal-md" :title="t('assets.credential')">
    <NForm :model="credForm" label-placement="top">
      <NFormItem :label="t('assets.sshUser')"><NInput v-model:value="credForm.username" spellcheck="false" /></NFormItem>
      <NFormItem :label="t('assets.authType')">
        <NSelect
          v-model:value="credForm.authType"
          :options="[
            { label: t('assets.password'), value: 'PASSWORD' },
            { label: t('assets.privateKey'), value: 'PRIVATE_KEY' },
          ]"
        />
      </NFormItem>
      <NFormItem :label="t('assets.sshSecret')">
        <NInput v-model:value="credForm.secret" type="password" show-password-on="click" />
      </NFormItem>
      <NFormItem :label="t('assets.jumpChain')">
        <NSpace vertical :size="4" class="full-width">
          <NSelect
            v-model:value="credForm.jumpAssetIds"
            :options="jumpAssetOptions"
            multiple
            filterable
            clearable
            :placeholder="t('assets.jumpChainPlaceholder')"
          />
          <span class="field-hint">{{ t('assets.jumpChainHint') }}</span>
        </NSpace>
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showCredential = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleSaveCredential">{{ t('common.save') }}</NButton>
      </NSpace>
    </NForm>
  </NModal>
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
</style>
