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
import { getGraphSnapshot, touchTerminalDock, type GraphEdge, type GraphNode } from '@/api/graph'
import { useAgentUiSelection } from '@/composables/useAgentUiSelection'
import { useAuthStore } from '@/stores/auth'
import { isOperatorOrAdmin } from '@/utils/roles'
import { createGraphStylesheet } from '@/theme/graphStyles'
import { aoColors } from '@/theme/tokens'
import { connectActionFor } from '@/assetTypes/registry'
import '@/assetTypes'

const { setNodeSelection, clearSelection } = useAgentUiSelection()

const message = useMessage()
const router = useRouter()
const auth = useAuthStore()
const canEdit = isOperatorOrAdmin(auth.user?.roles)

const loading = ref(false)
const graphVersion = ref(0)
const containerRef = ref<HTMLDivElement | null>(null)
const edgeTip = ref({ show: false, x: 0, y: 0, text: '' })

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
    const desc =
      e.properties?.description != null && String(e.properties.description).trim()
        ? String(e.properties.description)
        : ''
    els.push({
      group: 'edges',
      data: {
        id: e.elementId || `${e.fromElementId}-${e.type}-${e.toElementId}`,
        source: e.fromElementId,
        target: e.toElementId,
        label: e.type,
        type: e.type,
        description: desc,
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
  const action = connectActionFor(kind)
  if (action === 'query') {
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
  if (action !== 'terminal') {
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
  const action = connectActionFor(kind)
  const canConnect = action === 'terminal' && Boolean(pgAssetId)
  const canQuery = action === 'query' && Boolean(pgAssetId)
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
    style: createGraphStylesheet(),
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

  cy.on('mouseover', 'edge', (evt) => {
    const data = evt.target.data()
    const desc = data.description != null ? String(data.description).trim() : ''
    if (!desc) {
      edgeTip.value = { show: false, x: 0, y: 0, text: '' }
      return
    }
    const rendered = evt.renderedPosition || evt.target.midpoint()
    const box = containerRef.value?.getBoundingClientRect()
    edgeTip.value = {
      show: true,
      x: (box?.left ?? 0) + (rendered?.x ?? 0),
      y: (box?.top ?? 0) + (rendered?.y ?? 0) - 12,
      text: `${data.type || data.label}: ${desc}`,
    }
  })

  cy.on('mouseout', 'edge', () => {
    edgeTip.value = { show: false, x: 0, y: 0, text: '' }
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
    <div class="topology-toolbar">
      <div class="topology-toolbar__meta">
        <span class="topology-toolbar__title">{{ t('topology.title') }}</span>
        <NTag size="small" :bordered="false">v{{ graphVersion }}</NTag>
        <span class="topology-toolbar__hint">{{ t('topology.connectHint') }}</span>
      </div>
      <NSpace :size="8">
        <NButton size="small" :loading="loading" @click="loadSnapshot">{{ t('common.refresh') }}</NButton>
        <NButton v-if="canEdit" size="small" type="primary" @click="router.push({ name: 'graph' })">
          {{ t('topology.openEditor') }}
        </NButton>
      </NSpace>
    </div>

    <div ref="containerRef" class="topology-canvas ao-blueprint-grid" />
    <div
      v-if="edgeTip.show"
      class="edge-tip"
      :style="{ left: `${edgeTip.x}px`, top: `${edgeTip.y}px` }"
    >
      {{ edgeTip.text }}
    </div>

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
  position: relative;
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--ao-header-height));
  min-height: 480px;
  background: v-bind('aoColors.ink');
}

.topology-toolbar {
  position: absolute;
  z-index: 2;
  top: var(--ao-space-3);
  left: var(--ao-space-3);
  right: var(--ao-space-3);
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--ao-space-3);
  padding: 8px 12px;
  border: 1px solid var(--ao-border);
  border-radius: var(--ao-radius);
  background: color-mix(in srgb, var(--ao-slate) 92%, transparent);
  backdrop-filter: blur(8px);
}

.topology-toolbar__meta {
  display: flex;
  align-items: center;
  gap: var(--ao-space-2);
  min-width: 0;
  flex-wrap: wrap;
}

.topology-toolbar__title {
  font-size: 0.875rem;
  font-weight: 600;
  color: #e8eef7;
  letter-spacing: -0.01em;
}

.topology-toolbar__hint {
  font-size: 0.75rem;
  color: var(--ao-steel);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.topology-canvas {
  flex: 1;
  min-height: 0;
  width: 100%;
}

.edge-tip {
  position: fixed;
  z-index: 40;
  max-width: 320px;
  padding: 6px 10px;
  border-radius: var(--ao-radius-sm);
  border: 1px solid var(--ao-border);
  background: rgba(15, 23, 36, 0.96);
  color: #e8eef7;
  font-size: 0.75rem;
  line-height: 1.35;
  pointer-events: none;
  transform: translate(-50%, -100%);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.35);
}
</style>
