<script setup lang="ts">
import { computed, h, onMounted, ref } from 'vue'
import { useI18n } from 'vue-i18n'
import {
  NButton,
  NCard,
  NDataTable,
  NModal,
  NSelect,
  NSpace,
  NTag,
  useMessage,
  type DataTableColumns,
} from 'naive-ui'
import {
  decideProposal,
  getProposal,
  listProposals,
  type ProposalResponse,
  type ProposalStatus,
} from '@/api/architecture'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import { useAuthStore } from '@/stores/auth'
import { isOperatorOrAdmin } from '@/utils/roles'

const { t } = useI18n()
const message = useMessage()
const authStore = useAuthStore()

const proposals = ref<ProposalResponse[]>([])
const loading = ref(false)
const statusFilter = ref<ProposalStatus | ''>('PENDING_REVIEW')
const showDetail = ref(false)
const detail = ref<ProposalResponse | null>(null)
const deciding = ref(false)

const canDecide = computed(() => isOperatorOrAdmin(authStore.user?.roles))

const statusOptions = computed(() => [
  { label: t('proposals.statusAll'), value: '' },
  { label: 'PENDING_REVIEW', value: 'PENDING_REVIEW' },
  { label: 'APPROVED', value: 'APPROVED' },
  { label: 'REJECTED', value: 'REJECTED' },
  { label: 'AUTO_MERGED', value: 'AUTO_MERGED' },
  { label: 'MERGED', value: 'MERGED' },
  { label: 'DRAFT', value: 'DRAFT' },
])

function statusType(status: string) {
  if (status === 'PENDING_REVIEW') return 'warning'
  if (status === 'APPROVED' || status === 'MERGED' || status === 'AUTO_MERGED') return 'success'
  if (status === 'REJECTED') return 'error'
  return 'default'
}

const columns = computed<DataTableColumns<ProposalResponse>>(() => [
  { title: t('common.id'), key: 'id', width: 70 },
  { title: t('proposals.summary'), key: 'summary', ellipsis: { tooltip: true } },
  { title: t('proposals.partitionKey'), key: 'partitionKey', width: 160, ellipsis: { tooltip: true } },
  {
    title: t('proposals.status'),
    key: 'status',
    width: 140,
    render: (row) =>
      h(NTag, { size: 'small', type: statusType(row.status), round: true }, { default: () => row.status }),
  },
  {
    title: t('proposals.confidence'),
    key: 'confidence',
    width: 100,
    render: (row) => (row.confidence != null ? String(row.confidence) : '—'),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 120,
    render: (row) =>
      h(
        NButton,
        { size: 'small', onClick: () => void openDetail(row.id) },
        { default: () => t('proposals.view') },
      ),
  },
])

async function load() {
  loading.value = true
  try {
    const status = statusFilter.value || null
    const res = await listProposals(status)
    if (res.success && res.data) proposals.value = res.data
  } finally {
    loading.value = false
  }
}

async function openDetail(id: number) {
  const res = await getProposal(id)
  if (res.success && res.data) {
    detail.value = res.data
    showDetail.value = true
  }
}

async function handleDecide(decision: 'APPROVE' | 'REJECT') {
  if (!detail.value) return
  deciding.value = true
  try {
    const res = await decideProposal(detail.value.id, decision)
    if (res.success && res.data) {
      message.success(decision === 'APPROVE' ? t('proposals.approved') : t('proposals.rejected'))
      detail.value = res.data
      await load()
    } else {
      message.error(res.message || t('proposals.decideFailed'))
    }
  } finally {
    deciding.value = false
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('proposals.title')" :description="t('proposals.subtitle')">
      <template #extra>
        <NSpace>
          <NSelect
            v-model:value="statusFilter"
            :options="statusOptions"
            class="status-filter"
            :placeholder="t('proposals.filterStatus')"
            clearable
            @update:value="load"
          />
          <NButton @click="load">{{ t('common.refresh') }}</NButton>
        </NSpace>
      </template>
    </PageHeader>

    <NCard class="page-card" :bordered="false">
      <NDataTable :columns="columns" :data="proposals" :loading="loading" :bordered="false" />
      <EmptyState v-if="!loading && proposals.length === 0" :message="t('proposals.empty')" />
    </NCard>

    <NModal
      v-model:show="showDetail"
      preset="card"
      :title="t('proposals.detailTitle')"
      style="width: min(720px, 94vw)"
    >
      <template v-if="detail">
        <NSpace vertical :size="12">
          <div class="meta-row">
            <NTag size="small" :type="statusType(detail.status)" round>{{ detail.status }}</NTag>
            <span>{{ detail.partitionKey }}</span>
            <span v-if="detail.confidence != null">
              {{ t('proposals.confidence') }}: {{ detail.confidence }}
            </span>
          </div>
          <section>
            <h3 class="section-title">{{ t('proposals.summary') }}</h3>
            <p class="body-text">{{ detail.summary || '—' }}</p>
          </section>
          <section>
            <h3 class="section-title">{{ t('proposals.factOps') }}</h3>
            <pre class="code-block">{{ detail.factOps || '—' }}</pre>
          </section>
          <section>
            <h3 class="section-title">{{ t('proposals.evidence') }}</h3>
            <pre class="code-block">{{ detail.evidence || '—' }}</pre>
          </section>
          <NSpace v-if="canDecide && detail.status === 'PENDING_REVIEW'">
            <NButton type="success" :loading="deciding" @click="handleDecide('APPROVE')">
              {{ t('proposals.approve') }}
            </NButton>
            <NButton type="error" :loading="deciding" @click="handleDecide('REJECT')">
              {{ t('proposals.reject') }}
            </NButton>
          </NSpace>
        </NSpace>
      </template>
    </NModal>
  </NSpace>
</template>

<style scoped>
.status-filter {
  width: 180px;
}

.meta-row {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: var(--co-space-3);
  font-size: 0.875rem;
  color: var(--co-text-secondary);
}

.section-title {
  margin: 0 0 var(--co-space-2);
  font-size: 0.9375rem;
  font-weight: 600;
}

.body-text {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--co-text-secondary);
  white-space: pre-wrap;
}

.code-block {
  margin: 0;
  padding: var(--co-space-3);
  font-size: 0.75rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--co-text-secondary);
  background: var(--co-bg-page);
  border: 1px solid var(--co-border);
  border-radius: var(--co-radius);
  max-height: 240px;
  overflow: auto;
}
</style>
