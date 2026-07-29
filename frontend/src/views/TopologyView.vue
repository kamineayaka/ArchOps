<script setup lang="ts">
import { t } from '@/messages'
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  NButton,
  NDropdown,
  NSpace,
  NTag,
  useMessage,
  type DropdownOption,
} from 'naive-ui'
import cytoscape, { type Core, type ElementDefinition } from 'cytoscape'
import PageHeader from '@/components/PageHeader.vue'
import { getGraphSnapshot, touchTerminalDock, type GraphEdge, type GraphNode } from '@/api/graph'
import { useAgentUiSelection } from '@/composables/useAgentUiSelection'
import { useAuthStore } from '@/stores/auth'
import { isOperatorOrAdmin } from '@/utils/roles'

const { setNodeSelection, clearSelection } = useAgentUiSelection()

const message = useMessage()
const router = useRouter()
const auth = useAuthStore()
const canEdit = isOperatorOrAdmin(auth.user?.roles)

const loading = ref(false)
const graphVersion = ref(0)
const containerRef = ref<HTMLDivElement | null>(null)

const ctxMenu = ref({
  show: false,
  x: 0,
  y: 0,
  assetId: null as number | null,
  elementId: '' as string,
  name: '' as string,
  canConnect: false,
})

let cy: Core | null = null

const ctxOptions = ref<DropdownOption[]>([])

function nodeLabel(n: { name: string; host?: string | null }) {
  return n.host ? `${n.name}\n${n.host}` : n.name
}

function toElements(nodes: GraphNode[], edges: GraphEdge[]): ElementDefinition[] {
  const els: ElementDefinition[] = nodes.map((n) => ({
    group: 'nodes',
    data: {
      id: n.elementId,
      label: nodeLabel(n),
      kind: n.kind,
      pgAssetId: n.pgAssetId,
      hasCredential: n.hasCredential,
      name: n.name,
      host: n.host,
    },
  }))
  for (const e of edges) {
    els.push({
      group: 'edges',
      data: {
        id: e.elementId || `${e.fromElementId}-${e.type}-${e.toElementId}`,
        source: e.fromElementId,
        target: e.toElementId,
        label: e.type,
        type: e.type,
      },
    })
  }
  return els
}

function hideCtxMenu() {
  ctxMenu.value.show = false
}

async function connectAsset(assetId: number, elementId: string) {
  hideCtxMenu()
  try {
    await touchTerminalDock({ assetId, elementId })
  } catch {
    // non-blocking
  }
  void router.push({ name: 'terminal', params: { assetId: String(assetId) } })
}

function openQuery(assetId: number) {
  hideCtxMenu()
  void router.push({ name: 'query', params: { assetId: String(assetId) } })
}

function tryConnectFromData(data: Record<string, unknown>) {
  const kind = String(data.kind || '')
  const pgAssetId = data.pgAssetId != null ? Number(data.pgAssetId) : null
  const elementId = String(data.id || '')
  if (!pgAssetId) {
    message.info(t('topology.connectOnlyServer'))
    return
  }
  if (kind === 'DATABASE') {
    if (!data.hasCredential) {
      message.warning(t('topology.connectNeedsCredential'))
      if (canEdit) {
        void router.push({ name: 'graph' })
      }
      return
    }
    openQuery(pgAssetId)
    return
  }
  if (kind !== 'SERVER') {
    message.info(t('topology.connectOnlyServer'))
    return
  }
  if (!data.hasCredential) {
    message.warning(t('topology.connectNeedsCredential'))
    if (canEdit) {
      void router.push({ name: 'graph' })
    }
    return
  }
  void connectAsset(pgAssetId, elementId)
}

function openCtxMenu(evt: cytoscape.EventObject) {
  const original = evt.originalEvent as MouseEvent
  original.preventDefault()
  const data = evt.target.data()
  const pgAssetId = data.pgAssetId != null ? Number(data.pgAssetId) : null
  const kind = String(data.kind || '')
  const canConnect = kind === 'SERVER' && Boolean(pgAssetId)
  const canQuery = kind === 'DATABASE' && Boolean(pgAssetId)
  ctxMenu.value = {
    show: true,
    x: original.clientX,
    y: original.clientY,
    assetId: pgAssetId,
    elementId: String(data.id || ''),
    name: String(data.name || ''),
    canConnect,
  }
  ctxOptions.value = [
    ...(canConnect
      ? [
          {
            label: t('topology.connect'),
            key: 'connect',
          },
        ]
      : []),
    ...(canQuery
      ? [
          {
            label: t('topology.query'),
            key: 'query',
          },
        ]
      : []),
    ...(!canConnect && !canQuery
      ? [
          {
            label: t('topology.connect'),
            key: 'connect',
            disabled: true,
          },
        ]
      : []),
    ...(canEdit
      ? [
          {
            label: t('topology.openEditor'),
            key: 'edit',
          },
        ]
      : []),
  ]
}

function onCtxSelect(key: string | number) {
  if (key === 'query' && ctxMenu.value.assetId) {
    if (!cy) return
    const node = cy.$id(ctxMenu.value.elementId)
    if (node.nonempty() && !node.data('hasCredential')) {
      message.warning(t('topology.connectNeedsCredential'))
      hideCtxMenu()
      if (canEdit) void router.push({ name: 'graph' })
      return
    }
    openQuery(ctxMenu.value.assetId)
    return
  }
  if (key === 'connect' && ctxMenu.value.assetId) {
    if (!cy) return
    const node = cy.$id(ctxMenu.value.elementId)
    if (node.nonempty() && !node.data('hasCredential')) {
      message.warning(t('topology.connectNeedsCredential'))
      hideCtxMenu()
      if (canEdit) void router.push({ name: 'graph' })
      return
    }
    void connectAsset(ctxMenu.value.assetId, ctxMenu.value.elementId)
    return
  }
  if (key === 'edit') {
    hideCtxMenu()
    void router.push({ name: 'graph' })
  }
}

function initCy(elements: ElementDefinition[]) {
  if (!containerRef.value) return
  if (cy) {
    cy.destroy()
    cy = null
  }
  cy = cytoscape({
    container: containerRef.value,
    elements,
    style: [
      {
        selector: 'node',
        style: {
          label: 'data(label)',
          'text-wrap': 'wrap',
          'text-valign': 'center',
          'text-halign': 'center',
          'font-size': 12,
          color: '#e8eef7',
          'background-color': '#3b82f6',
          'border-width': 2,
          'border-color': '#93c5fd',
          width: 64,
          height: 64,
          'text-outline-width': 2,
          'text-outline-color': '#0f172a',
        },
      },
      {
        selector: 'node[kind = "CLUSTER"]',
        style: { 'background-color': '#a855f7', shape: 'round-rectangle', width: 80, height: 52 },
      },
      {
        selector: 'node[kind = "SERVICE"]',
        style: { 'background-color': '#6366f1' },
      },
      {
        selector: 'node[kind = "TAG"]',
        style: { 'background-color': '#14b8a6', shape: 'diamond', width: 48, height: 48 },
      },
      {
        selector: 'node[kind = "ENVIRONMENT"]',
        style: { 'background-color': '#22c55e', shape: 'round-rectangle', width: 72, height: 48 },
      },
      {
        selector: 'node[kind = "DATABASE"]',
        style: { 'background-color': '#f59e0b' },
      },
      {
        selector: 'node[kind = "NETWORK"]',
        style: { 'background-color': '#64748b', shape: 'hexagon', width: 52, height: 52 },
      },
      {
        selector: 'edge',
        style: {
          width: 2,
          'line-color': '#64748b',
          'target-arrow-color': '#64748b',
          'target-arrow-shape': 'triangle',
          'curve-style': 'bezier',
          label: 'data(label)',
          'font-size': 10,
          color: '#94a3b8',
          'text-rotation': 'autorotate',
        },
      },
    ],
    layout: { name: 'cose', animate: false, padding: 48 },
    userZoomingEnabled: true,
    userPanningEnabled: true,
  })

  cy.on('dblclick', 'node', (evt) => {
    tryConnectFromData(evt.target.data())
  })

  cy.on('cxttap', 'node', (evt) => {
    openCtxMenu(evt)
  })

  cy.on('tap', 'node', (evt) => {
    const data = evt.target.data()
    const pgAssetId = data.pgAssetId != null ? Number(data.pgAssetId) : null
    const elementId = data.id != null ? String(data.id) : null
    setNodeSelection(pgAssetId, elementId)
  })

  cy.on('tap', (evt) => {
    hideCtxMenu()
    if (evt.target === cy) {
      clearSelection()
    }
  })
}

async function loadSnapshot() {
  loading.value = true
  hideCtxMenu()
  try {
    const res = await getGraphSnapshot()
    if (!res.success || !res.data) {
      message.error(res.message || t('common.failed'))
      return
    }
    graphVersion.value = res.data.graphVersion
    await nextTick()
    initCy(toElements(res.data.nodes, res.data.edges))
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  void loadSnapshot()
  window.addEventListener('click', hideCtxMenu)
  window.addEventListener('scroll', hideCtxMenu, true)
})

onBeforeUnmount(() => {
  clearSelection()
  window.removeEventListener('click', hideCtxMenu)
  window.removeEventListener('scroll', hideCtxMenu, true)
  cy?.destroy()
  cy = null
})
</script>

<template>
  <div class="topology-page">
    <PageHeader :title="t('topology.title')" :description="t('topology.subtitle')">
      <template #extra>
        <NSpace>
          <NTag size="small">v{{ graphVersion }}</NTag>
          <NButton :loading="loading" @click="loadSnapshot">{{ t('common.refresh') }}</NButton>
          <NButton v-if="canEdit" @click="router.push({ name: 'graph' })">
            {{ t('topology.openEditor') }}
          </NButton>
        </NSpace>
      </template>
    </PageHeader>

    <p class="topology-hint">{{ t('topology.connectHint') }}</p>

    <div ref="containerRef" class="topology-canvas" />

    <NDropdown
      placement="bottom-start"
      trigger="manual"
      :show="ctxMenu.show"
      :x="ctxMenu.x"
      :y="ctxMenu.y"
      :options="ctxOptions"
      @select="onCtxSelect"
      @clickoutside="hideCtxMenu"
    />
  </div>
</template>

<style scoped>
.topology-page {
  display: flex;
  flex-direction: column;
  gap: var(--co-space-3);
  height: calc(100vh - var(--co-header-height) - var(--co-space-6) * 2);
  min-height: 480px;
}

.topology-hint {
  margin: 0;
  font-size: 0.8125rem;
  color: var(--co-text-muted);
}

.topology-canvas {
  flex: 1;
  min-height: 0;
  border: 1px solid var(--co-border);
  border-radius: 8px;
  background:
    radial-gradient(circle at 18% 22%, rgba(59, 130, 246, 0.1), transparent 42%),
    radial-gradient(circle at 82% 8%, rgba(20, 184, 166, 0.08), transparent 36%),
    #0b1220;
}
</style>
