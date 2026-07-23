<script setup lang="ts">
import { computed, h, onMounted, ref, watch } from 'vue'
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
  NPopconfirm,
  NSelect,
  NSpace,
  NTag,
  useMessage,
} from 'naive-ui'
import { createAsset, deleteAsset, listAssets, saveSshCredential, type Asset } from '@/api/assets'
import '@/assetTypes'
import { defaultPortFor, getAssetType, listAssetTypes } from '@/assetTypes/registry'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { apiErrorMessage } from '@/utils/apiError'

const { t } = useI18n()
const message = useMessage()

const assets = ref<Asset[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showCredential = ref(false)
const selectedAssetId = ref<number | null>(null)

const form = ref<{ name: string; kind: string; host: string; port: number | null }>({
  name: '',
  kind: 'SERVER',
  host: '',
  port: 22,
})
const credForm = ref({
  username: 'root',
  authType: 'PASSWORD',
  secret: '',
  jumpAssetIds: [] as number[],
})

const kindOptions = computed(() =>
  listAssetTypes().map((def) => ({
    label: t(def.labelKey),
    value: def.kind,
  })),
)

const selectedType = computed(() => getAssetType(form.value.kind))

const jumpAssetOptions = computed(() =>
  assets.value
    .filter((a) => a.hasSshCredential && a.id !== selectedAssetId.value)
    .map((a) => ({
      label: `${a.name} (#${a.id})`,
      value: a.id,
    })),
)

watch(
  () => form.value.kind,
  (kind) => {
    form.value.port = defaultPortFor(kind)
  },
)

const columns = computed(() => [
  { title: t('common.id'), key: 'id', width: 60 },
  { title: t('assets.name'), key: 'name' },
  { title: t('assets.kind'), key: 'kind' },
  { title: t('assets.host'), key: 'host' },
  { title: t('assets.port'), key: 'port', width: 80 },
  {
    title: t('assets.credential'),
    key: 'hasSshCredential',
    render: (row: Asset) =>
      h(
        NTag,
        { size: 'small', round: true, type: row.hasSshCredential ? 'success' : 'warning' },
        { default: () => (row.hasSshCredential ? t('common.configured') : t('common.notConfigured')) },
      ),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 200,
    render: (row: Asset) =>
      h(NSpace, { size: 8 }, {
        default: () => [
          h(NButton, { size: 'small', onClick: () => openCredential(row.id) }, { default: () => t('assets.credential') }),
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
  form.value = { name: '', kind: 'SERVER', host: '', port: defaultPortFor('SERVER') }
}

async function load() {
  loading.value = true
  try {
    const res = await listAssets()
    if (res.success && res.data) assets.value = res.data
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  if (!form.value.name.trim()) {
    message.warning(t('assets.nameRequired'))
    return
  }
  const def = getAssetType(form.value.kind)
  if (def?.showHost && !form.value.host.trim()) {
    message.warning(t('assets.hostRequired'))
    return
  }
  try {
    const payload = {
      name: form.value.name.trim(),
      kind: form.value.kind,
      host: form.value.host || undefined,
      port: form.value.port ?? undefined,
    }
    const res = await createAsset(payload)
    if (res.success) {
      message.success(t('assets.created'))
      showCreate.value = false
      resetForm()
      await load()
    } else {
      message.error(res.message || t('common.failed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('common.failed')))
  }
}

function openCredential(assetId: number) {
  const asset = assets.value.find((a) => a.id === assetId)
  selectedAssetId.value = assetId
  credForm.value = {
    username: 'root',
    authType: 'PASSWORD',
    secret: '',
    jumpAssetIds: asset?.jumpAssetIds ? [...asset.jumpAssetIds] : [],
  }
  showCredential.value = true
}

async function handleSaveCredential() {
  if (!selectedAssetId.value) return
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

async function handleDelete(id: number) {
  const res = await deleteAsset(id)
  if (res.success) {
    message.success(t('assets.deleted'))
    await load()
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

  <NModal v-model:show="showCreate" preset="card" class="modal-md" :title="t('assets.addAsset')">
    <NForm :model="form" label-placement="top">
      <NFormItem :label="t('assets.name')"><NInput v-model:value="form.name" /></NFormItem>
      <NFormItem :label="t('assets.kind')"><NSelect v-model:value="form.kind" :options="kindOptions" /></NFormItem>
      <NFormItem v-if="selectedType?.showHost !== false" :label="t('assets.host')">
        <NInput v-model:value="form.host" :placeholder="t('assets.hostPlaceholder')" />
      </NFormItem>
      <NFormItem v-if="selectedType?.showPort !== false" :label="t('assets.port')">
        <NInputNumber v-model:value="form.port" :min="1" :max="65535" class="full-width" />
      </NFormItem>
      <NSpace justify="end">
        <NButton @click="showCreate = false">{{ t('common.cancel') }}</NButton>
        <NButton type="primary" @click="handleCreate">{{ t('common.create') }}</NButton>
      </NSpace>
    </NForm>
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
