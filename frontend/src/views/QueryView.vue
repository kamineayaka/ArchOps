<script setup lang="ts">
import { t } from '@/messages'
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NDataTable,
  NInput,
  NSpace,
  NSpin,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import PageHeader from '@/components/PageHeader.vue'
import { getAsset, type Asset } from '@/api/assets'
import { runAssetQuery, type DbQueryResult } from '@/api/dbQuery'
import { useAgentUiSelection } from '@/composables/useAgentUiSelection'
import { apiErrorMessage } from '@/utils/apiError'

const route = useRoute()
const router = useRouter()
const message = useMessage()
const { selectedPgAssetIds } = useAgentUiSelection()

const loading = ref(false)
const asset = ref<Asset | null>(null)
const sql = ref('SELECT 1')
const result = ref<DbQueryResult | null>(null)
const pendingApprovalId = ref<number | null>(null)
const selectionCandidate = ref<Asset | null>(null)

const assetId = computed(() => {
  const raw = route.params.assetId
  const n = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(n) && n > 0 ? n : null
})

const tableColumns = computed<DataTableColumns>(() => {
  const cols = result.value?.columns ?? []
  return cols.map((c, idx) => ({
    title: c,
    key: `c${idx}`,
    ellipsis: { tooltip: true },
    render: (row: Record<string, unknown>) => {
      const v = row[`c${idx}`]
      return v == null ? 'NULL' : String(v)
    },
  }))
})

const tableData = computed(() => {
  const rows = result.value?.rows ?? []
  return rows.map((row, i) => {
    const obj: Record<string, unknown> = { key: i }
    row.forEach((cell, idx) => {
      obj[`c${idx}`] = cell
    })
    return obj
  })
})

async function loadAssetById(id: number): Promise<Asset | null> {
  try {
    const res = await getAsset(id)
    return res.success ? (res.data ?? null) : null
  } catch (err) {
    message.error(apiErrorMessage(err, t('query.loadFailed')))
    return null
  }
}

async function refreshAsset() {
  if (!assetId.value) {
    asset.value = null
    return
  }
  asset.value = await loadAssetById(assetId.value)
}

async function refreshSelectionCandidate() {
  if (assetId.value || !selectedPgAssetIds.value.length) {
    selectionCandidate.value = null
    return
  }
  const id = selectedPgAssetIds.value[0]
  const candidate = await loadAssetById(id)
  selectionCandidate.value =
    candidate &&
    candidate.kind === 'DATABASE' &&
    (candidate.hasCredential ?? candidate.hasSshCredential)
      ? candidate
      : null
}

async function run(withApproval = false) {
  if (!assetId.value) {
    message.warning(t('query.needAsset'))
    return
  }
  if (!sql.value.trim()) {
    message.warning(t('query.needSql'))
    return
  }
  loading.value = true
  try {
    const approvalId = withApproval ? pendingApprovalId.value : null
    const res = await runAssetQuery(assetId.value, sql.value, approvalId)
    result.value = res.data
    if (res.data?.status === 'PENDING_APPROVAL') {
      pendingApprovalId.value = res.data.approvalId
      message.warning(t('query.pendingApproval', { id: String(res.data.approvalId ?? '') }))
    } else {
      pendingApprovalId.value = null
      message.success(res.data?.message || t('query.success'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('query.runFailed')))
  } finally {
    loading.value = false
  }
}

function goApprovals() {
  void router.push({ name: 'approvals' })
}

function goTopology() {
  void router.push({ name: 'topology' })
}

function useTopologySelection() {
  const id = selectionCandidate.value?.id
  if (!id) return
  void router.replace({ name: 'query', params: { assetId: String(id) } })
}

onMounted(async () => {
  await refreshAsset()
  await refreshSelectionCandidate()
})

watch(assetId, async () => {
  result.value = null
  pendingApprovalId.value = null
  await refreshAsset()
  await refreshSelectionCandidate()
})

watch(selectedPgAssetIds, () => {
  void refreshSelectionCandidate()
})
</script>

<template>
  <div class="query-page">
    <PageHeader :title="t('query.title')" :description="t('query.subtitle')">
      <template #extra>
        <NButton size="small" @click="goTopology">{{ t('query.goTopology') }}</NButton>
      </template>
    </PageHeader>

    <NSpin :show="loading">
      <NSpace vertical :size="16">
        <NAlert v-if="!assetId" type="warning" :title="t('query.needAsset')">
          <NSpace :size="8" style="margin-top: 8px">
            <NButton
              v-if="selectionCandidate"
              size="small"
              type="primary"
              @click="useTopologySelection"
            >
              {{ t('query.useTopologySelection') }}: {{ selectionCandidate.name }}
            </NButton>
            <NButton size="small" @click="goTopology">{{ t('query.goTopology') }}</NButton>
          </NSpace>
        </NAlert>
        <NAlert
          v-else-if="asset && !(asset.hasCredential ?? asset.hasSshCredential)"
          type="warning"
          :title="t('query.needCredential')"
        />
        <div v-if="asset" class="query-meta">
          <NTag type="info" size="small">DATABASE</NTag>
          <span class="query-meta-name">{{ asset.name }}</span>
          <span class="query-meta-host">
            {{ asset.host || '—' }}{{ asset.port ? ':' + asset.port : '' }}
          </span>
        </div>

        <NInput
          v-model:value="sql"
          type="textarea"
          :rows="8"
          :placeholder="t('query.sqlPlaceholder')"
          class="query-sql"
        />

        <NSpace>
          <NButton type="primary" :disabled="!assetId" @click="run(false)">
            {{ t('query.run') }}
          </NButton>
          <NButton
            v-if="pendingApprovalId"
            type="warning"
            :disabled="!assetId"
            @click="run(true)"
          >
            {{ t('query.runAfterApproval', { id: String(pendingApprovalId) }) }}
          </NButton>
          <NButton v-if="pendingApprovalId" quaternary @click="goApprovals">
            {{ t('query.openApprovals') }}
          </NButton>
        </NSpace>

        <NAlert
          v-if="result?.status === 'PENDING_APPROVAL'"
          type="warning"
          :title="t('query.pendingTitle')"
        >
          {{ result.message }}
        </NAlert>

        <div v-if="result && result.status === 'EXECUTED'" class="query-result">
          <NSpace align="center" :size="8" class="query-result-bar">
            <NTag :type="result.mutating ? 'warning' : 'success'" size="small">
              {{ result.mutating ? t('query.mutating') : t('query.readOnly') }}
            </NTag>
            <span v-if="result.elapsedMs != null">{{ result.elapsedMs }} ms</span>
            <span>{{ t('query.rowCount', { n: String(result.rowCount) }) }}</span>
            <span v-if="result.mutating">update={{ result.updateCount }}</span>
            <NTag v-if="result.truncated" type="warning" size="small">{{ t('query.truncated') }}</NTag>
          </NSpace>
          <NDataTable
            v-if="(result.columns?.length ?? 0) > 0"
            :columns="tableColumns"
            :data="tableData"
            size="small"
            :bordered="true"
            :max-height="420"
            :scroll-x="Math.max(600, (result.columns?.length ?? 0) * 140)"
          />
          <NAlert v-else type="success" :title="result.message || t('query.success')" />
        </div>
      </NSpace>
    </NSpin>
  </div>
</template>

<style scoped>
.query-page {
  display: flex;
  flex-direction: column;
  gap: 12px;
  height: calc(100vh - var(--ao-header-height));
  padding: var(--ao-space-4);
  box-sizing: border-box;
  background: var(--ao-bg-page);
}
.query-meta {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.query-meta-name {
  font-weight: 600;
}
.query-meta-host {
  opacity: 0.85;
  font-family: var(--ao-font-mono);
  font-size: 12px;
  color: var(--ao-text-secondary);
}
.query-sql :deep(textarea) {
  font-family: var(--ao-font-mono) !important;
  font-size: 13px !important;
  background: var(--ao-ink) !important;
  color: #e8eef7 !important;
}
.query-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
  flex: 1;
  min-height: 0;
}
.query-result-bar {
  font-size: 12px;
  color: var(--ao-text-secondary);
  font-family: var(--ao-font-mono);
}
.query-result :deep(.n-data-table) {
  font-family: var(--ao-font-mono);
  font-size: 12px;
}
</style>
