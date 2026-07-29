<script setup lang="ts">
import { t } from '@/messages'
import { computed, h, onMounted, ref } from 'vue'
import { NButton, NCard, NDataTable, NSpace, useMessage } from 'naive-ui'
import client from '@/api/client'
import type { ApiResponse } from '@/api/types'
import EmptyState from '@/components/EmptyState.vue'
import PageHeader from '@/components/PageHeader.vue'
import StatusTag from '@/components/StatusTag.vue'
import { formatDateTime } from '@/utils/format'

const message = useMessage()

interface AuditLog {
  id: number
  actorName: string | null
  action: string
  resource: string | null
  riskLevel: string | null
  status: string
  createdAt: string
}

const logs = ref<AuditLog[]>([])
const loading = ref(false)

const columns = computed(() => [
  { title: t('common.id'), key: 'id', width: 70 },
  { title: t('audit.actor'), key: 'actorName' },
  {
    title: t('audit.action'),
    key: 'action',
    render: (row: AuditLog) => h('code', { class: 'ao-mono' }, row.action),
  },
  {
    title: t('audit.resource'),
    key: 'resource',
    ellipsis: { tooltip: true },
    render: (row: AuditLog) =>
      h('code', { class: 'ao-mono', style: 'font-size: 0.75rem' }, row.resource || '—'),
  },
  {
    title: t('audit.risk'),
    key: 'riskLevel',
    render: (row: AuditLog) =>
      row.riskLevel ? h(StatusTag, { kind: 'risk', value: row.riskLevel }) : '—',
  },
  {
    title: t('audit.status'),
    key: 'status',
    render: (row: AuditLog) => h(StatusTag, { kind: 'status', value: row.status }),
  },
  {
    title: t('audit.time'),
    key: 'createdAt',
    render: (row: AuditLog) =>
      h('span', { class: 'ao-mono', style: 'font-size: 0.75rem' }, formatDateTime(row.createdAt)),
  },
])

async function load() {
  loading.value = true
  try {
    const { data } = await client.get<ApiResponse<{ content: AuditLog[] }>>('/api/audit?page=0&size=50')
    if (data.success && data.data) {
      const page = data.data as { content?: AuditLog[] }
      logs.value = page.content ?? (data.data as unknown as AuditLog[])
    }
  } finally {
    loading.value = false
  }
}

async function verifyChain() {
  const { data } = await client.get<ApiResponse<boolean>>('/api/audit/verify')
  if (data.success) {
    message.info(data.data ? t('audit.chainValid') : t('audit.chainInvalid'))
  }
}

onMounted(load)
</script>

<template>
  <NSpace vertical :size="16">
    <PageHeader :title="t('audit.title')" :description="t('audit.subtitle')">
      <template #extra>
        <NSpace>
          <NButton @click="verifyChain">{{ t('audit.verify') }}</NButton>
          <NButton @click="load">{{ t('common.refresh') }}</NButton>
        </NSpace>
      </template>
    </PageHeader>

    <NCard class="page-card" size="small">
      <NDataTable size="small" :columns="columns" :data="logs" :loading="loading" :bordered="false" />
      <EmptyState v-if="!loading && logs.length === 0" :message="t('audit.empty')" />
    </NCard>
  </NSpace>
</template>
