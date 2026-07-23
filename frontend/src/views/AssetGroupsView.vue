<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
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
import { listAssets, type Asset } from '@/api/assets'
import {
  createAssetGroup,
  deleteAssetGroup,
  getAssetGroup,
  listAssetGroups,
  replaceAssetGroupMembers,
  type AssetGroup,
} from '@/api/assetGroups'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'

const { t } = useI18n()
const message = useMessage()

const groups = ref<AssetGroup[]>([])
const assets = ref<Asset[]>([])
const loading = ref(false)
const showCreate = ref(false)
const showMembers = ref(false)
const selectedGroupId = ref<number | null>(null)
const memberIds = ref<number[]>([])
const form = ref({ name: '', description: '' })

const assetOptions = computed(() =>
  assets.value.map((a) => ({
    label: `${a.name}${a.host ? ` (${a.host})` : ''}`,
    value: a.id,
  })),
)

const columns = computed(() => [
  { title: t('common.id'), key: 'id', width: 60 },
  { title: t('assetGroups.name'), key: 'name' },
  { title: t('assetGroups.description'), key: 'description', ellipsis: { tooltip: true } },
  {
    title: t('assetGroups.members'),
    key: 'memberCount',
    width: 100,
    render: (row: AssetGroup) =>
      h(NTag, { size: 'small', round: true }, { default: () => String(row.memberCount) }),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 220,
    render: (row: AssetGroup) =>
      h(NSpace, { size: 8 }, {
        default: () => [
          h(
            NButton,
            { size: 'small', onClick: () => openMembers(row.id) },
            { default: () => t('assetGroups.editMembers') },
          ),
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
    const [groupRes, assetRes] = await Promise.all([listAssetGroups(), listAssets()])
    if (groupRes.success && groupRes.data) groups.value = groupRes.data
    if (assetRes.success && assetRes.data) assets.value = assetRes.data
  } finally {
    loading.value = false
  }
}

async function handleCreate() {
  const res = await createAssetGroup({
    name: form.value.name.trim(),
    description: form.value.description.trim() || undefined,
    enabled: true,
  })
  if (res.success) {
    message.success(t('assetGroups.created'))
    showCreate.value = false
    form.value = { name: '', description: '' }
    await load()
  }
}

async function openMembers(groupId: number) {
  selectedGroupId.value = groupId
  const res = await getAssetGroup(groupId)
  if (res.success && res.data) {
    memberIds.value = res.data.members.map((m) => m.id)
    showMembers.value = true
  }
}

async function handleSaveMembers() {
  if (selectedGroupId.value == null) return
  const res = await replaceAssetGroupMembers(selectedGroupId.value, memberIds.value)
  if (res.success) {
    message.success(t('assetGroups.membersSaved'))
    showMembers.value = false
    await load()
  }
}

async function handleDelete(id: number) {
  const res = await deleteAssetGroup(id)
  if (res.success) {
    message.success(t('assetGroups.deleted'))
    await load()
  }
}

onMounted(load)
</script>

<template>
  <div>
    <PageHeader :title="t('assetGroups.title')" :description="t('assetGroups.subtitle')">
      <template #extra>
        <NButton type="primary" @click="showCreate = true">{{ t('assetGroups.addGroup') }}</NButton>
      </template>
    </PageHeader>

    <NDataTable
      v-if="groups.length || loading"
      :columns="columns"
      :data="groups"
      :loading="loading"
      :bordered="false"
      :single-line="false"
    />
    <EmptyState v-else :message="t('assetGroups.empty')" />

    <NModal v-model:show="showCreate" preset="card" :title="t('assetGroups.addGroup')" style="width: 480px">
      <NForm label-placement="top">
        <NFormItem :label="t('assetGroups.name')">
          <NInput v-model:value="form.name" :placeholder="t('assetGroups.namePlaceholder')" />
        </NFormItem>
        <NFormItem :label="t('assetGroups.description')">
          <NInput v-model:value="form.description" type="textarea" :placeholder="t('assetGroups.descriptionPlaceholder')" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showCreate = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :disabled="!form.name.trim()" @click="handleCreate">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showMembers" preset="card" :title="t('assetGroups.editMembers')" style="width: 520px">
      <NSelect
        v-model:value="memberIds"
        multiple
        filterable
        :options="assetOptions"
        :placeholder="t('assetGroups.selectMembers')"
        :aria-label="t('assetGroups.selectMembers')"
      />
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showMembers = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" @click="handleSaveMembers">{{ t('common.save') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>
