<script setup lang="ts">
import { t } from '@/messages'
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NCard, NCheckbox, NDataTable, NPopconfirm, NSpace, useMessage } from 'naive-ui'
import { decideApproval, listPendingApprovals, type Approval } from '@/api/approvals'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'

const message = useMessage()

const approvals = ref<Approval[]>([])
const loading = ref(false)
/** Per-approval "remember for this session" checkbox state */
const rememberById = ref<Record<number, boolean>>({})

function payloadHasConversationId(payload: string | null | undefined): boolean {
  if (!payload) return false
  try {
    const parsed = JSON.parse(payload) as { conversationId?: unknown }
    return parsed.conversationId != null && parsed.conversationId !== ''
  } catch {
    return payload.includes('conversationId')
  }
}

const columns = computed(() => [
  { title: t('common.id'), key: 'id', width: 60 },
  { title: t('approvals.action'), key: 'action' },
  {
    title: t('approvals.risk'),
    key: 'riskLevel',
    render: (row: Approval) => h(StatusTag, { kind: 'risk', value: row.riskLevel }),
  },
  { title: t('common.resource'), key: 'resource' },
  {
    title: t('common.payload'),
    key: 'payload',
    ellipsis: { tooltip: true },
    render: (row: Approval) =>
      h('code', { class: 'ao-mono', style: 'font-size: 0.75rem' }, row.payload || '—'),
  },
  {
    title: t('common.actions'),
    key: 'actions',
    width: 280,
    render: (row: Approval) =>
      h(NSpace, { size: 8, vertical: true }, {
        default: () => [
          payloadHasConversationId(row.payload)
            ? h(
                NCheckbox,
                {
                  checked: !!rememberById.value[row.id],
                  'onUpdate:checked': (v: boolean) => {
                    rememberById.value = { ...rememberById.value, [row.id]: v }
                  },
                },
                { default: () => t('approvals.rememberForSession') },
              )
            : null,
          h(NSpace, { size: 8 }, {
            default: () => [
              h(
                NButton,
                {
                  size: 'small',
                  type: 'success',
                  onClick: () => handleDecide(row.id, 'APPROVE', !!rememberById.value[row.id]),
                },
                { default: () => t('approvals.approve') },
              ),
              h(
                NPopconfirm,
                { onPositiveClick: () => handleDecide(row.id, 'REJECT', false) },
                {
                  trigger: () => h(NButton, { size: 'small', type: 'error' }, { default: () => t('approvals.reject') }),
                  default: () => t('approvals.confirmReject'),
                },
              ),
            ],
          }),
        ],
      }),
  },
])

async function load() {
  loading.value = true
  try {
    const res = await listPendingApprovals()
    if (res.success && res.data) {
      approvals.value = res.data
      rememberById.value = {}
    }
  } finally {
    loading.value = false
  }
}

async function handleDecide(id: number, decision: 'APPROVE' | 'REJECT', rememberForSession: boolean) {
  const res = await decideApproval(id, decision, undefined, decision === 'APPROVE' ? rememberForSession : false)
  if (res.success) {
    message.success(decision === 'APPROVE' ? t('approvals.approved') : t('approvals.rejected'))
    await load()
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('approvals.title')" :description="t('approvals.subtitle')">
      <template #extra>
        <NButton @click="load">{{ t('common.refresh') }}</NButton>
      </template>
    </PageHeader>

    <NCard class="page-card" size="small">
      <NDataTable size="small" :columns="columns" :data="approvals" :loading="loading" :bordered="false" />
      <EmptyState v-if="!loading && approvals.length === 0" :message="t('approvals.empty')" />
    </NCard>
  </NSpace>
</template>
