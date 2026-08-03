<script setup lang="ts">
import { t } from '@/messages'
import { computed, h, onMounted, ref } from 'vue'
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
import { apiErrorMessage } from '@/utils/apiError'
import { isAdmin, isOperatorOrAdmin } from '@/utils/roles'

const message = useMessage()
const authStore = useAuthStore()

const proposals = ref<ProposalResponse[]>([])
const loading = ref(false)
const statusFilter = ref<ProposalStatus | ''>('PENDING_REVIEW')
const showDetail = ref(false)
const detail = ref<ProposalResponse | null>(null)
const deciding = ref(false)

const canDecide = computed(() => isOperatorOrAdmin(authStore.user?.roles))

const isOwnPending = computed(
  () =>
    detail.value?.status === 'PENDING_REVIEW' &&
    detail.value.requesterId != null &&
    detail.value.requesterId === authStore.user?.id,
)

const canApprove = computed(() => {
  if (!canDecide.value || detail.value?.status !== 'PENDING_REVIEW') return false
  if (!isOwnPending.value) return true
  return isAdmin(authStore.user?.roles)
})

const statusOptions = computed(() => [
  { label: t('proposals.statusAll'), value: '' },
  { label: 'PENDING_REVIEW', value: 'PENDING_REVIEW' },
  { label: 'APPROVED', value: 'APPROVED' },
  { label: 'REJECTED', value: 'REJECTED' },
  { label: 'AUTO_MERGED', value: 'AUTO_MERGED' },
  { label: 'MERGED', value: 'MERGED' },
  { label: 'CONFLICT', value: 'CONFLICT' },
  { label: 'SUPERSEDED', value: 'SUPERSEDED' },
  { label: 'MERGE_FAILED', value: 'MERGE_FAILED' },
  { label: 'DRAFT', value: 'DRAFT' },
])

function statusType(status: string) {
  if (status === 'PENDING_REVIEW' || status === 'SUPERSEDED') return 'warning'
  if (status === 'APPROVED' || status === 'MERGED' || status === 'AUTO_MERGED') return 'success'
  if (status === 'REJECTED' || status === 'CONFLICT' || status === 'MERGE_FAILED') return 'error'
  return 'default'
}

function formatJson(value: string | null | undefined): string {
  if (value == null || value === '') return '—'
  try {
    return JSON.stringify(JSON.parse(value), null, 2)
  } catch {
    return value
  }
}

function hasMeaningfulJson(value: string | null | undefined): boolean {
  if (value == null || value === '') return false
  const trimmed = value.trim()
  return trimmed !== '[]' && trimmed !== '{}'
}

function hasChangeSet(p: ProposalResponse): boolean {
  return hasMeaningfulJson(p.changeSet) && (p.changeSet?.includes('"ops"') ?? false)
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
  try {
    const res = await getProposal(id)
    if (res.success && res.data) {
      detail.value = res.data
      showDetail.value = true
    } else {
      message.error(res.message || t('proposals.decideFailed'))
    }
  } catch (err) {
    message.error(apiErrorMessage(err, t('proposals.decideFailed')))
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
  } catch (err) {
    message.error(apiErrorMessage(err, t('proposals.decideFailed')))
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

    <NCard class="page-card" size="small">
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
            <span v-if="detail.risk">{{ t('proposals.risk') }}: {{ detail.risk }}</span>
            <span v-if="detail.source">{{ t('proposals.source') }}: {{ detail.source }}</span>
            <span v-if="detail.confidence != null">
              {{ t('proposals.confidence') }}: {{ detail.confidence }}
            </span>
            <span v-if="detail.baseGraphVersion != null">
              {{ t('proposals.baseGraphVersion') }}: {{ detail.baseGraphVersion }}
            </span>
          </div>
          <section>
            <h3 class="section-title">{{ t('proposals.summary') }}</h3>
            <p class="body-text">{{ detail.summary || '—' }}</p>
          </section>
          <section v-if="detail.conflictDetail">
            <h3 class="section-title">冲突/失败详情</h3>
            <p class="body-text">{{ detail.conflictDetail }}</p>
          </section>
          <section v-if="hasChangeSet(detail)">
            <h3 class="section-title">{{ t('proposals.changeSet') }}</h3>
            <pre class="code-block">{{ formatJson(detail.changeSet) }}</pre>
          </section>
          <section v-if="hasMeaningfulJson(detail.factOps)">
            <h3 class="section-title">{{ t('proposals.factOps') }}</h3>
            <pre class="code-block">{{ formatJson(detail.factOps) }}</pre>
          </section>
          <section v-if="hasMeaningfulJson(detail.evidence)">
            <h3 class="section-title">{{ t('proposals.evidence') }}</h3>
            <pre class="code-block">{{ formatJson(detail.evidence) }}</pre>
          </section>
          <section
            v-if="!hasChangeSet(detail) && !hasMeaningfulJson(detail.factOps) && !hasMeaningfulJson(detail.evidence)"
          >
            <p class="body-text">{{ t('proposals.noOps') }}</p>
          </section>
          <p v-if="isOwnPending && !canApprove" class="hint-text">{{ t('proposals.selfReviewHint') }}</p>
          <NSpace v-if="canDecide && detail.status === 'PENDING_REVIEW'">
            <NButton
              type="success"
              :loading="deciding"
              :disabled="!canApprove"
              @click="handleDecide('APPROVE')"
            >
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
  gap: var(--ao-space-3);
  font-size: 0.875rem;
  color: var(--ao-text-secondary);
}

.section-title {
  margin: 0 0 var(--ao-space-2);
  font-size: 0.9375rem;
  font-weight: 600;
}

.body-text {
  margin: 0;
  font-size: 0.875rem;
  line-height: 1.5;
  color: var(--ao-text-secondary);
  white-space: pre-wrap;
}

.hint-text {
  margin: 0;
  font-size: 0.8125rem;
  line-height: 1.45;
  color: var(--ao-warning, #b45309);
}

.code-block {
  margin: 0;
  padding: var(--ao-space-3);
  font-size: 0.75rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
  color: var(--ao-text-secondary);
  background: var(--ao-bg-page);
  border: 1px solid var(--ao-border);
  border-left: 3px solid var(--ao-signal);
  border-radius: var(--ao-radius-sm);
  max-height: 320px;
  overflow: auto;
  font-family: var(--ao-font-mono);
}
</style>
