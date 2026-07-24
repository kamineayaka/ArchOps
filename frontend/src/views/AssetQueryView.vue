<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCard,
  NDataTable,
  NFormItem,
  NInput,
  NSelect,
  NSpace,
  useMessage,
} from 'naive-ui'
import { listAssets, queryAsset, type Asset, type AssetQueryResponse } from '@/api/assets'
import '@/assetTypes'
import { connectActionFor, getAssetType } from '@/assetTypes/registry'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { apiErrorMessage } from '@/utils/apiError'

const { t } = useI18n()
const route = useRoute()
const message = useMessage()

const assets = ref<Asset[]>([])
const assetId = ref<number | null>(null)
const statement = ref('SELECT 1')
const loading = ref(false)
const result = ref<AssetQueryResponse | null>(null)

const queryableAssets = computed(() =>
  assets.value.filter((a) => connectActionFor(a.kind) === 'query'),
)

const assetOptions = computed(() =>
  queryableAssets.value.map((a) => ({
    label: `${a.name} (${getAssetType(a.kind) ? t(getAssetType(a.kind)!.labelKey) : a.kind})`,
    value: a.id,
  })),
)

const selectedAsset = computed(() => assets.value.find((a) => a.id === assetId.value) ?? null)

const placeholder = computed(() => {
  if (selectedAsset.value?.kind === 'REDIS') return t('query.placeholderRedis')
  return t('query.placeholderSql')
})

watch(
  () => selectedAsset.value?.kind,
  (kind) => {
    if (kind === 'REDIS' && statement.value.startsWith('SELECT')) {
      statement.value = 'PING'
    } else if (kind === 'DATABASE' && statement.value === 'PING') {
      statement.value = 'SELECT 1'
    }
  },
)

const columns = computed(() =>
  (result.value?.columns ?? []).map((col) => ({
    title: col,
    key: col,
    ellipsis: { tooltip: true },
  })),
)

const tableData = computed(() => {
  if (!result.value) return []
  const cols = result.value.columns
  return result.value.rows.map((row, idx) => {
    const obj: Record<string, string | number> = { __row: idx }
    cols.forEach((c, i) => {
      obj[c] = row[i] ?? ''
    })
    return obj
  })
})

async function loadAssets() {
  const res = await listAssets()
  if (res.success && res.data) {
    assets.value = res.data
    const fromRoute = Number(route.params.assetId)
    if (Number.isFinite(fromRoute) && queryableAssets.value.some((a) => a.id === fromRoute)) {
      assetId.value = fromRoute
    } else if (!assetId.value && queryableAssets.value.length) {
      assetId.value = queryableAssets.value[0].id
    }
  }
}

async function handleRun() {
  if (!assetId.value) {
    message.warning(t('query.selectAsset'))
    return
  }
  if (!statement.value.trim()) {
    message.warning(t('query.emptyStatement'))
    return
  }
  loading.value = true
  result.value = null
  try {
    const res = await queryAsset(assetId.value, statement.value.trim())
    if (res.success && res.data) {
      result.value = res.data
      if (!res.data.ok) {
        message.error(res.data.message || t('query.failed'))
      } else {
        message.success(res.data.message || t('query.ok'))
      }
    } else {
      message.error(res.message || t('query.failed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('query.failed')))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadAssets()
})
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('query.title')" :description="t('query.subtitle')" />

    <NCard class="page-card" :bordered="false">
      <NSpace vertical :size="12">
        <NFormItem :label="t('query.asset')">
          <NSelect
            v-model:value="assetId"
            :options="assetOptions"
            filterable
            :placeholder="t('query.selectAsset')"
          />
        </NFormItem>
        <NFormItem :label="t('query.statement')">
          <NInput
            v-model:value="statement"
            type="textarea"
            :rows="3"
            :placeholder="placeholder"
            @keydown.ctrl.enter="handleRun"
          />
        </NFormItem>
        <NSpace>
          <NButton type="primary" :loading="loading" :disabled="!assetId" @click="handleRun">
            {{ t('query.run') }}
          </NButton>
          <span class="hint">{{ t('query.readonlyHint') }}</span>
        </NSpace>
      </NSpace>
    </NCard>

    <NCard v-if="result" class="page-card" :bordered="false" :title="t('query.result')">
      <p class="result-msg">{{ result.message }}</p>
      <NDataTable
        v-if="result.ok && result.columns.length"
        :columns="columns"
        :data="tableData"
        :bordered="false"
        size="small"
      />
      <EmptyState v-else-if="result.ok" :message="t('query.emptyResult')" />
    </NCard>
  </NSpace>
</template>

<style scoped>
.hint {
  font-size: 0.8125rem;
  color: var(--co-text-secondary);
}
.result-msg {
  margin: 0 0 var(--co-space-3);
  font-size: 0.875rem;
  color: var(--co-text-secondary);
}
</style>
