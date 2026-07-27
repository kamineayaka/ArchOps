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
import { listAssets, type Asset } from '@/api/assets'
import { runAssetQuery, type DbQueryResult } from '@/api/dbQuery'
import { apiErrorMessage } from '@/utils/apiError'

const route = useRoute()
const router = useRouter()
const message = useMessage()

const loading = ref(false)
const assets = ref<Asset[]>([])
const sql = ref('SELECT 1')
const result = ref<DbQueryResult | null>(null)
const pendingApprovalId = ref<number | null>(null)

const assetId = computed(() => {
  const raw = route.params.assetId
  const n = Number(Array.isArray(raw) ? raw[0] : raw)
  return Number.isFinite(n) && n > 0 ? n : null
})

const asset = computed(() => assets.value.find((a) => a.id === assetId.value) ?? null)

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

async function loadAssets() {
  try {
    const res = await listAssets()
    assets.value = (res.data ?? []).filter((a) => a.kind === 'DATABASE')
  } catch (err) {
    message.error(apiErrorMessage(err, t('query.loadFailed')))
  }
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

onMounted(() => {
  void loadAssets()
})

watch(assetId, () => {
  result.value = null
  pendingApprovalId.value = null
})
</script>

<template>
  <div class="query-page">
    <PageHeader :title="t('query.title')" :subtitle="t('query.subtitle')" />

    <NSpin :show="loading">
      <NSpace vertical :size="16">
        <NAlert v-if="!assetId" type="warning" :title="t('query.needAsset')" />
        <NAlert
          v-else-if="asset && !asset.hasSshCredential"
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
  gap: 16px;
  height: 100%;
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
  opacity: 0.7;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}
.query-sql :deep(textarea) {
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 13px;
}
.query-result {
  display: flex;
  flex-direction: column;
  gap: 10px;
}
.query-result-bar {
  font-size: 13px;
  opacity: 0.85;
}
</style>
