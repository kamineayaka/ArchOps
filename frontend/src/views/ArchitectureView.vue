<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCard,
  NDataTable,
  NInputNumber,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  getPartition,
  listPartitions,
  rollbackPartition,
  type FactResponse,
  type PartitionDetail,
  type PartitionSummary,
} from '@/api/architecture'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { isAdmin as roleIsAdmin } from '@/utils/roles'

const { t } = useI18n()
const message = useMessage()
const authStore = useAuthStore()

const partitions = ref<PartitionSummary[]>([])
const loading = ref(false)
const detailLoading = ref(false)
const selected = ref<PartitionDetail | null>(null)
const rollbackVersion = ref<number | null>(null)

const isAdmin = computed(() => roleIsAdmin(authStore.user?.roles))

const columns = computed<DataTableColumns<PartitionSummary>>(() => [
  { title: t('architecture.partitionKey'), key: 'partitionKey', ellipsis: { tooltip: true } },
  { title: t('architecture.partitionTitle'), key: 'title', ellipsis: { tooltip: true } },
  {
    title: t('architecture.highImpact'),
    key: 'highImpact',
    width: 100,
    render: (row) =>
      h(
        NTag,
        { size: 'small', type: row.highImpact ? 'warning' : 'default', round: true },
        { default: () => (row.highImpact ? t('architecture.yes') : t('architecture.no')) },
      ),
  },
  { title: t('architecture.latestVersion'), key: 'latestVersion', width: 100 },
  { title: t('architecture.activeFacts'), key: 'activeFactCount', width: 110 },
])

const factColumns = computed<DataTableColumns<FactResponse>>(() => [
  { title: t('architecture.factType'), key: 'factType', width: 100 },
  { title: t('architecture.subject'), key: 'subject', ellipsis: { tooltip: true } },
  { title: t('architecture.predicate'), key: 'predicate', width: 120 },
  { title: t('architecture.object'), key: 'object', ellipsis: { tooltip: true } },
  { title: t('architecture.confidence'), key: 'confidence', width: 100 },
  { title: t('architecture.factStatus'), key: 'status', width: 100 },
])

const rowProps = (row: PartitionSummary) => ({
  style: 'cursor: pointer',
  onClick: () => void openDetail(row.partitionKey),
})

async function load() {
  loading.value = true
  try {
    const res = await listPartitions()
    if (res.success && res.data) partitions.value = res.data
  } finally {
    loading.value = false
  }
}

async function openDetail(key: string) {
  detailLoading.value = true
  try {
    const res = await getPartition(key)
    if (res.success && res.data) {
      selected.value = res.data
      const latest = res.data.latestVersion
      rollbackVersion.value = latest != null && latest > 1 ? latest - 1 : latest
    }
  } finally {
    detailLoading.value = false
  }
}

async function handleRollback() {
  if (!selected.value || rollbackVersion.value == null) return
  const key = selected.value.partitionKey
  const res = await rollbackPartition(key, rollbackVersion.value)
  if (res.success && res.data) {
    message.success(t('architecture.rollbackSuccess'))
    selected.value = res.data
    await load()
  } else {
    message.error(res.message || t('architecture.rollbackFailed'))
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('architecture.title')" :description="t('architecture.subtitle')">
      <template #extra>
        <NButton @click="load">{{ t('common.refresh') }}</NButton>
      </template>
    </PageHeader>

    <NCard class="page-card" :bordered="false">
      <NDataTable
        :columns="columns"
        :data="partitions"
        :loading="loading"
        :bordered="false"
        :row-props="rowProps"
      />
      <EmptyState v-if="!loading && partitions.length === 0" :message="t('architecture.empty')" />
    </NCard>

    <NCard
      v-if="selected"
      class="page-card"
      :bordered="false"
      :title="selected.partitionKey"
      :loading="detailLoading"
    >
      <NSpace vertical :size="12">
        <div class="detail-meta">
          <NTag v-if="selected.highImpact" type="warning" size="small" round>
            {{ t('architecture.highImpact') }}
          </NTag>
          <span>{{ t('architecture.latestVersion') }}: {{ selected.latestVersion ?? '—' }}</span>
        </div>

        <section>
          <h3 class="section-title">{{ t('architecture.summary') }}</h3>
          <p class="body-text">{{ selected.summary || t('architecture.noSummary') }}</p>
        </section>

        <section>
          <h3 class="section-title">{{ t('architecture.bodyMd') }}</h3>
          <pre class="body-md">{{ selected.bodyMd || t('architecture.noBody') }}</pre>
        </section>

        <section>
          <h3 class="section-title">{{ t('architecture.facts') }}</h3>
          <NDataTable
            :columns="factColumns"
            :data="selected.facts ?? []"
            :bordered="false"
            size="small"
          />
          <EmptyState
            v-if="!(selected.facts && selected.facts.length)"
            :message="t('architecture.noFacts')"
          />
        </section>

        <section v-if="isAdmin" class="rollback">
          <h3 class="section-title">{{ t('architecture.rollback') }}</h3>
          <NSpace align="center">
            <NInputNumber
              v-model:value="rollbackVersion"
              :min="1"
              :placeholder="t('architecture.targetVersion')"
            />
            <NButton type="warning" :disabled="rollbackVersion == null" @click="handleRollback">
              {{ t('architecture.rollback') }}
            </NButton>
          </NSpace>
        </section>
      </NSpace>
    </NCard>
  </NSpace>
</template>

<style scoped>
.detail-meta {
  display: flex;
  align-items: center;
  gap: var(--co-space-3);
  font-size: 0.875rem;
  color: var(--co-text-secondary);
}

.section-title {
  margin: 0 0 var(--co-space-2);
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--co-text);
}

.body-text {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--co-text-secondary);
  white-space: pre-wrap;
}

.body-md {
  margin: 0;
  padding: var(--co-space-3);
  font-size: 0.8125rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--co-text-secondary);
  background: var(--co-bg-page);
  border-radius: var(--co-radius);
  border: 1px solid var(--co-border);
  max-height: 320px;
  overflow: auto;
}

.rollback {
  padding-top: var(--co-space-2);
  border-top: 1px solid var(--co-border);
}
</style>
