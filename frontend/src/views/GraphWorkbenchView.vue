<script setup lang="ts">
import { t } from '@/messages'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import {
  NAlert,
  NButton,
  NForm,
  NFormItem,
  NInput,
  NInputNumber,
  NModal,
  NSelect,
  NSpace,
  NSwitch,
  NTag,
  useMessage,
} from 'naive-ui'
import cytoscape, { type Core, type ElementDefinition } from 'cytoscape'
import PageHeader from '@/components/PageHeader.vue'
import { createProposal } from '@/api/architecture'
import {
  getGraphSnapshot,
  planGraph,
  queryGraph,
  stageCredential,
  touchTerminalDock,
  type GraphEdge,
  type GraphNode,
} from '@/api/graph'
import { testSavedAssetConnection } from '@/api/assets'
import '@/assetTypes'
import {
  connectActionFor,
  defaultPortFor,
  getAssetType,
  listAssetTypes,
} from '@/assetTypes/registry'
import { useAuthStore } from '@/stores/auth'
import { newId } from '@/utils/id'
import { isOperatorOrAdmin } from '@/utils/roles'

interface NodeSelection {
  type: 'node'
  elementId: string
  name: string
  kind: string
  host: string | null
  port: number | null
  enabled: boolean
  hasCredential: boolean
  pgAssetId: number | null
  slug: string | null
  draft: boolean
}

interface EdgeSelection {
  type: 'edge'
  elementId: string
  relType: string
  source: string
  target: string
  draft: boolean
}

type Selection = NodeSelection | EdgeSelection | null

const message = useMessage()
const router = useRouter()
const auth = useAuthStore()
const canEdit = computed(() => isOperatorOrAdmin(auth.user?.roles))

const loading = ref(false)
const graphEnabled = ref(false)
const graphVersion = ref(0)
const cypher = ref("MATCH (n:Asset) WHERE n.kind = 'SERVER' RETURN n LIMIT 50")
const cypherBusy = ref(false)
const matchedIds = ref<string[]>([])
const draftOps = ref<Record<string, unknown>[]>([])
const draftSideEffects = ref<Record<string, unknown>[]>([])
const planWarnings = ref<string[]>([])
const edgeMode = ref(false)
const edgeType = ref('MEMBER_OF')
const showAddNode = ref(false)
const showEditNode = ref(false)
const showCredential = ref(false)
const selection = ref<Selection>(null)
const testBusy = ref(false)
const showDraftPanel = ref(false)
const submitBusy = ref(false)
const addBusy = ref(false)
const editBusy = ref(false)
const credBusy = ref(false)
const containerRef = ref<HTMLDivElement | null>(null)

const emptyNodeForm = () => ({
  name: '',
  kind: 'SERVER',
  host: '',
  port: 22 as number | null,
  slug: '',
  enabled: true,
  withCredential: false,
  username: '',
  authType: 'PASSWORD',
  secret: '',
})

const addForm = ref(emptyNodeForm())
const editForm = ref(emptyNodeForm())
const credForm = ref({
  username: '',
  authType: 'PASSWORD',
  secret: '',
})

let cy: Core | null = null
let edgeSourceId: string | null = null

const edgeTypeOptions = [
  { label: 'MEMBER_OF', value: 'MEMBER_OF' },
  { label: 'RUNS_ON', value: 'RUNS_ON' },
  { label: 'DEPENDS_ON', value: 'DEPENDS_ON' },
  { label: 'CONNECTS_VIA', value: 'CONNECTS_VIA' },
  { label: 'HAS_TAG', value: 'HAS_TAG' },
]

const kindOptions = computed(() =>
  listAssetTypes().map((def) => ({
    label: t(def.labelKey),
    value: def.kind,
  })),
)

const draftCount = computed(() => draftOps.value.length)

const authTypeOptions = [
  { label: t('assets.password'), value: 'PASSWORD' },
  { label: t('assets.privateKey'), value: 'PRIVATE_KEY' },
]

const addTypeDef = computed(() => getAssetType(addForm.value.kind))
const editTypeDef = computed(() => getAssetType(editForm.value.kind))
const showAddHost = computed(() => Boolean(addTypeDef.value?.showHost))
const showAddPort = computed(() => Boolean(addTypeDef.value?.showPort && (addTypeDef.value.defaultPort ?? 0) > 0))
const showAddSlug = computed(() => addForm.value.kind === 'TAG')
const addNeedsCredential = computed(() => {
  const mode = addTypeDef.value?.authMode
  return mode === 'ssh' || mode === 'password'
})
const showAddCredentialFields = computed(
  () => addForm.value.withCredential && addNeedsCredential.value,
)

const showEditHostFields = computed(() => Boolean(editTypeDef.value?.showHost))
const showEditPortFields = computed(
  () => Boolean(editTypeDef.value?.showPort && (editTypeDef.value.defaultPort ?? 0) > 0),
)
const showEditSlug = computed(() => editForm.value.kind === 'TAG')

const selectedNode = computed(() => (selection.value?.type === 'node' ? selection.value : null))
const selectedEdge = computed(() => (selection.value?.type === 'edge' ? selection.value : null))
const selectedIsMerged = computed(() => Boolean(selectedNode.value && !selectedNode.value.draft && selectedNode.value.elementId))
const canOpenTerminal = computed(
  () =>
    selectedNode.value &&
    !selectedNode.value.draft &&
    selectedNode.value.pgAssetId &&
    connectActionFor(selectedNode.value.kind) === 'terminal',
)
const canTestConnection = computed(
  () =>
    selectedNode.value &&
    !selectedNode.value.draft &&
    selectedNode.value.pgAssetId &&
    Boolean(getAssetType(selectedNode.value.kind)?.supportsTest),
)
const canUpdateCredential = computed(
  () =>
    selectedNode.value &&
    !selectedNode.value.draft &&
    selectedNode.value.pgAssetId &&
    (getAssetType(selectedNode.value.kind)?.authMode === 'ssh' ||
      getAssetType(selectedNode.value.kind)?.authMode === 'password'),
)

const draftSummaries = computed(() =>
  draftOps.value.map((op, index) => ({
    index,
    text: summarizeOp(op),
  })),
)

watch(
  () => addForm.value.kind,
  (kind) => {
    const port = defaultPortFor(kind)
    addForm.value.port = port
    if (!addNeedsCredential.value) {
      addForm.value.withCredential = false
    }
  },
)

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
      port: n.port,
      enabled: n.enabled,
      slug: n.slug,
      draft: false,
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
        draft: false,
      },
    })
  }
  return els
}

function applyHighlight(ids: string[]) {
  if (!cy) return
  const set = new Set(ids)
  const dim = ids.length > 0
  cy.batch(() => {
    cy!.nodes().forEach((node) => {
      const hit = set.has(node.id())
      node.toggleClass('highlighted', hit)
      node.toggleClass('dimmed', dim && !hit)
    })
    cy!.edges().forEach((edge) => {
      const hit = set.has(edge.source().id()) || set.has(edge.target().id())
      edge.toggleClass('highlighted', hit && dim)
      edge.toggleClass('dimmed', dim && !hit)
    })
  })
}

function clearCySelectionClasses() {
  if (!cy) return
  cy.elements().removeClass('selected')
}

function applyCySelection(sel: Selection) {
  clearCySelectionClasses()
  if (!cy || !sel) return
  const el = cy.$id(sel.elementId)
  if (el.nonempty()) el.addClass('selected')
}

function selectNodeFromEle(ele: cytoscape.NodeSingular) {
  const data = ele.data()
  selection.value = {
    type: 'node',
    elementId: String(data.id),
    name: String(data.name || data.label || data.id),
    kind: String(data.kind || 'SERVER'),
    host: data.host != null && data.host !== '' ? String(data.host) : null,
    port: data.port != null ? Number(data.port) : null,
    enabled: data.enabled !== false,
    hasCredential: Boolean(data.hasCredential),
    pgAssetId: data.pgAssetId != null ? Number(data.pgAssetId) : null,
    slug: data.slug != null ? String(data.slug) : null,
    draft: Boolean(data.draft),
  }
  applyCySelection(selection.value)
}

function selectEdgeFromEle(ele: cytoscape.EdgeSingular) {
  const data = ele.data()
  selection.value = {
    type: 'edge',
    elementId: String(data.id),
    relType: String(data.type || data.label || ''),
    source: String(data.source),
    target: String(data.target),
    draft: Boolean(data.draft),
  }
  applyCySelection(selection.value)
}

function clearSelection() {
  selection.value = null
  clearCySelectionClasses()
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
          'font-size': 11,
          color: '#e8eef7',
          'background-color': '#3b82f6',
          'border-width': 2,
          'border-color': '#93c5fd',
          width: 56,
          height: 56,
          'text-outline-width': 2,
          'text-outline-color': '#0f172a',
        },
      },
      {
        selector: 'node[kind = "CLUSTER"]',
        style: { 'background-color': '#a855f7', shape: 'round-rectangle', width: 70, height: 48 },
      },
      {
        selector: 'node[kind = "SERVICE"]',
        style: { 'background-color': '#6366f1' },
      },
      {
        selector: 'node[kind = "TAG"]',
        style: { 'background-color': '#14b8a6', shape: 'diamond', width: 44, height: 44 },
      },
      {
        selector: 'node[kind = "ENVIRONMENT"]',
        style: { 'background-color': '#22c55e', shape: 'round-rectangle', width: 64, height: 44 },
      },
      {
        selector: 'node[kind = "DATABASE"]',
        style: { 'background-color': '#f59e0b' },
      },
      {
        selector: 'node[kind = "NETWORK"]',
        style: { 'background-color': '#64748b', shape: 'hexagon', width: 48, height: 48 },
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
          'font-size': 9,
          color: '#94a3b8',
          'text-rotation': 'autorotate',
        },
      },
      {
        selector: '.dimmed',
        style: { opacity: 0.18 },
      },
      {
        selector: '.highlighted',
        style: {
          opacity: 1,
          'border-width': 4,
          'border-color': '#fbbf24',
          'line-color': '#fbbf24',
          'target-arrow-color': '#fbbf24',
          'z-index': 999,
        },
      },
      {
        selector: '.selected',
        style: {
          'border-width': 4,
          'border-color': '#38bdf8',
          'line-color': '#38bdf8',
          'target-arrow-color': '#38bdf8',
          'z-index': 1000,
        },
      },
      {
        selector: '.draft',
        style: {
          'border-style': 'dashed',
          'border-color': '#34d399',
          'line-style': 'dashed',
          'line-color': '#34d399',
        },
      },
      {
        selector: '.pending-delete',
        style: {
          opacity: 0.45,
          'border-color': '#f87171',
          'line-color': '#f87171',
        },
      },
    ],
    layout: { name: 'cose', animate: false, padding: 40 },
  })

  cy.on('tap', 'node', (evt) => {
    const id = evt.target.id()
    if (edgeMode.value && canEdit.value) {
      if (!edgeSourceId) {
        edgeSourceId = id
        message.info(t('graph.pickEdgeTarget'))
        return
      }
      if (edgeSourceId === id) {
        edgeSourceId = null
        return
      }
      addDraftEdge(edgeSourceId, id, edgeType.value)
      edgeSourceId = null
      return
    }
    selectNodeFromEle(evt.target)
  })

  cy.on('tap', 'edge', (evt) => {
    if (edgeMode.value) return
    selectEdgeFromEle(evt.target)
  })

  cy.on('tap', (evt) => {
    if (evt.target === cy) {
      clearSelection()
    }
  })

  cy.on('cxttap', 'node', (evt) => {
    const data = evt.target.data()
    if (data.kind === 'SERVER' && data.pgAssetId) {
      void openTerminal(Number(data.pgAssetId), String(data.id))
    } else if (data.kind === 'DATABASE' && data.pgAssetId) {
      void router.push({ name: 'query', params: { assetId: String(data.pgAssetId) } })
    }
  })

  applyHighlight(matchedIds.value)
  if (selection.value) applyCySelection(selection.value)
}

async function loadSnapshot() {
  loading.value = true
  try {
    const res = await getGraphSnapshot()
    if (!res.success || !res.data) {
      message.error(res.message || t('common.failed'))
      return
    }
    graphEnabled.value = res.data.graphEnabled
    graphVersion.value = res.data.graphVersion
    await nextTick()
    initCy(toElements(res.data.nodes, res.data.edges))
    for (const op of draftOps.value) {
      overlayDraftOp(op)
    }
  } finally {
    loading.value = false
  }
}

function overlayDraftOp(op: Record<string, unknown>) {
  if (!cy) return
  if (op.op === 'NODE_CREATE') {
    const props = (op.properties || {}) as Record<string, unknown>
    const id = String(props.elementId || op.tempId)
    if (cy.$id(id).nonempty()) return
    cy.add({
      group: 'nodes',
      data: {
        id,
        label: String(props.name || id),
        kind: String(props.kind || 'SERVER'),
        name: String(props.name || ''),
        host: props.host != null ? String(props.host) : null,
        port: props.port != null ? Number(props.port) : null,
        enabled: props.enabled !== false,
        hasCredential: Boolean(props.hasCredential),
        slug: props.slug != null ? String(props.slug) : null,
        pgAssetId: null,
        draft: true,
      },
      classes: 'draft',
    })
  }
  if (op.op === 'REL_CREATE') {
    const from = op.from as { elementId?: string; tempId?: string }
    const to = op.to as { elementId?: string; tempId?: string }
    const source = from?.elementId || from?.tempId
    const target = to?.elementId || to?.tempId
    const props = (op.properties || {}) as Record<string, unknown>
    const id = String(props.elementId || `${source}-${op.type}-${target}`)
    if (!source || !target || cy.$id(id).nonempty()) return
    cy.add({
      group: 'edges',
      data: {
        id,
        source,
        target,
        label: String(op.type),
        type: String(op.type),
        draft: true,
      },
      classes: 'draft',
    })
  }
  if (op.op === 'NODE_SOFT_DELETE' || op.op === 'REL_DELETE') {
    const ref = op.ref as { elementId?: string } | undefined
    const id = ref?.elementId || (op.properties as { elementId?: string } | undefined)?.elementId
    if (!id) return
    const el = cy.$id(String(id))
    if (el.nonempty()) el.addClass('pending-delete')
  }
  if (op.op === 'NODE_UPDATE') {
    const ref = op.ref as { elementId?: string }
    const set = (op.set || {}) as Record<string, unknown>
    if (!ref?.elementId) return
    const el = cy.$id(ref.elementId)
    if (el.empty()) return
    const data = { ...el.data() }
    if (set.name != null) {
      data.name = String(set.name)
      data.label = nodeLabel({ name: data.name, host: (set.host as string) ?? data.host })
    }
    if ('host' in set) data.host = set.host == null ? null : String(set.host)
    if ('port' in set) data.port = set.port == null ? null : Number(set.port)
    if ('enabled' in set) data.enabled = Boolean(set.enabled)
    if ('slug' in set) data.slug = set.slug == null ? null : String(set.slug)
    if ('hasCredential' in set) data.hasCredential = Boolean(set.hasCredential)
    el.data(data)
    el.addClass('draft')
  }
}

function kindLabel(kind: string) {
  const map: Record<string, string> = {
    SERVER: 'Server',
    CLUSTER: 'Cluster',
    SERVICE: 'Service',
    DATABASE: 'Database',
    NETWORK: 'Network',
    TAG: 'Tag',
    ENVIRONMENT: 'Environment',
  }
  return map[kind] || 'Server'
}

function summarizeOp(op: Record<string, unknown>): string {
  const kind = String(op.op || '')
  if (kind === 'NODE_CREATE') {
    const props = (op.properties || {}) as Record<string, unknown>
    return t('graph.draftNodeCreate', {
      kind: String(props.kind || ''),
      name: String(props.name || ''),
    })
  }
  if (kind === 'NODE_UPDATE') {
    const ref = op.ref as { elementId?: string }
    const set = (op.set || {}) as Record<string, unknown>
    return t('graph.draftNodeUpdate', {
      name: String(set.name || ref?.elementId || ''),
    })
  }
  if (kind === 'NODE_SOFT_DELETE') {
    const ref = op.ref as { elementId?: string }
    return t('graph.draftNodeDelete', { id: String(ref?.elementId || '') })
  }
  if (kind === 'REL_CREATE') {
    const from = op.from as { elementId?: string }
    const to = op.to as { elementId?: string }
    return t('graph.draftRelCreate', {
      type: String(op.type || ''),
      from: String(from?.elementId || '').slice(0, 8),
      to: String(to?.elementId || '').slice(0, 8),
    })
  }
  if (kind === 'REL_DELETE') {
    const ref = op.ref as { elementId?: string }
    return t('graph.draftRelDelete', { id: String(ref?.elementId || '').slice(0, 8) })
  }
  return kind
}

async function addDraftNode() {
  if (!addForm.value.name.trim()) {
    message.warning(t('graph.nameRequired'))
    return
  }
  if (showAddSlug.value && !addForm.value.slug.trim()) {
    message.warning(t('graph.slugRequired'))
    return
  }
  if (showAddCredentialFields.value) {
    if (!addForm.value.username.trim() || !addForm.value.secret.trim()) {
      message.warning(t('graph.credentialRequired'))
      return
    }
  }
  addBusy.value = true
  try {
    const tempId = `tmp:node:${Date.now()}`
    const elementId = newId()
    const props: Record<string, unknown> = {
      elementId,
      name: addForm.value.name.trim(),
      kind: addForm.value.kind,
      enabled: true,
      hasCredential: showAddCredentialFields.value,
    }
    if (showAddHost.value) {
      props.host = addForm.value.host.trim() || null
    }
    if (showAddPort.value) {
      props.port = addForm.value.port
    }
    if (showAddSlug.value) {
      props.slug = addForm.value.slug.trim().toLowerCase()
    }
    const op = {
      op: 'NODE_CREATE',
      tempId,
      labels: ['Asset', kindLabel(addForm.value.kind)],
      properties: props,
    }
    draftOps.value.push(op)
    if (showAddCredentialFields.value) {
      const stageRes = await stageCredential({
        username: addForm.value.username.trim(),
        authType: addForm.value.authType,
        secret: addForm.value.secret,
        tempRef: tempId,
      })
      if (!stageRes.success || !stageRes.data) {
        draftOps.value.pop()
        message.error(stageRes.message || t('common.failed'))
        return
      }
      draftSideEffects.value.push({
        effect: 'CREDENTIAL_UPSERT_REF',
        tempId,
        credentialStagingId: stageRes.data.stagingId,
      })
    }
    overlayDraftOp(op)
    showAddNode.value = false
    addForm.value = emptyNodeForm()
    planWarnings.value = []
    message.success(t('graph.draftAdded'))
  } catch (e) {
    message.error(e instanceof Error ? e.message : t('common.failed'))
  } finally {
    addBusy.value = false
  }
}

function addDraftEdge(fromId: string, toId: string, type: string) {
  const op = {
    op: 'REL_CREATE',
    type,
    from: { elementId: fromId },
    to: { elementId: toId },
    properties: {
      elementId: newId(),
      ...(type === 'CONNECTS_VIA' ? { order: 0, protocol: 'ssh' } : {}),
    },
  }
  draftOps.value.push(op)
  overlayDraftOp(op)
  planWarnings.value = []
  message.success(t('graph.edgeDrafted', { type }))
}

function openEditModal() {
  const node = selectedNode.value
  if (!node || node.draft) return
  editForm.value = {
    ...emptyNodeForm(),
    name: node.name,
    kind: node.kind,
    host: node.host || '',
    port: node.port,
    slug: node.slug || '',
    enabled: node.enabled,
  }
  showEditNode.value = true
}

function saveEditDraft() {
  const node = selectedNode.value
  if (!node || node.draft) return
  if (!editForm.value.name.trim()) {
    message.warning(t('graph.nameRequired'))
    return
  }
  if (showEditSlug.value && !editForm.value.slug.trim()) {
    message.warning(t('graph.slugRequired'))
    return
  }
  editBusy.value = true
  try {
    const set: Record<string, unknown> = {
      name: editForm.value.name.trim(),
      enabled: editForm.value.enabled,
    }
    if (showEditHostFields.value) {
      set.host = editForm.value.host.trim() || null
    }
    if (showEditPortFields.value) {
      set.port = editForm.value.port
    }
    if (showEditSlug.value) {
      set.slug = editForm.value.slug.trim().toLowerCase()
    }
    const op = {
      op: 'NODE_UPDATE',
      ref: { elementId: node.elementId },
      set,
    }
    draftOps.value.push(op)
    overlayDraftOp(op)
    showEditNode.value = false
    planWarnings.value = []
    selection.value = {
      ...node,
      name: String(set.name),
      host: 'host' in set ? (set.host as string | null) : node.host,
      port: 'port' in set ? (set.port as number | null) : node.port,
      enabled: Boolean(set.enabled),
      slug: 'slug' in set ? (set.slug as string | null) : node.slug,
    }
    message.success(t('graph.draftAdded'))
  } finally {
    editBusy.value = false
  }
}

function openCredentialModal() {
  if (!canUpdateCredential.value) return
  credForm.value = { username: '', authType: 'PASSWORD', secret: '' }
  showCredential.value = true
}

async function saveCredentialDraft() {
  const node = selectedNode.value
  if (!node?.pgAssetId) return
  if (!credForm.value.username.trim() || !credForm.value.secret.trim()) {
    message.warning(t('graph.credentialRequired'))
    return
  }
  credBusy.value = true
  try {
    const stageRes = await stageCredential({
      username: credForm.value.username.trim(),
      authType: credForm.value.authType,
      secret: credForm.value.secret,
      assetId: node.pgAssetId,
    })
    if (!stageRes.success || !stageRes.data) {
      message.error(stageRes.message || t('common.failed'))
      return
    }
    draftSideEffects.value.push({
      effect: 'CREDENTIAL_UPSERT_REF',
      pgAssetId: node.pgAssetId,
      credentialStagingId: stageRes.data.stagingId,
    })
    // Ensure change set is non-empty for plan: noop-friendly NODE_UPDATE hasCredential
    draftOps.value.push({
      op: 'NODE_UPDATE',
      ref: { elementId: node.elementId },
      set: { hasCredential: true },
    })
    overlayDraftOp(draftOps.value[draftOps.value.length - 1])
    showCredential.value = false
    planWarnings.value = []
    if (selection.value?.type === 'node') {
      selection.value = { ...selection.value, hasCredential: true }
    }
    message.success(t('graph.credentialDrafted'))
  } finally {
    credBusy.value = false
  }
}

function softDeleteSelectedNode() {
  const node = selectedNode.value
  if (!node || node.draft || !canEdit.value) return
  if (!window.confirm(t('graph.confirmSoftDelete', { name: node.name }))) return
  const op = {
    op: 'NODE_SOFT_DELETE',
    ref: { elementId: node.elementId },
    reason: 'ui_canvas',
  }
  draftOps.value.push(op)
  overlayDraftOp(op)
  planWarnings.value = []
  message.success(t('graph.draftAdded'))
}

function deleteSelectedEdge() {
  const edge = selectedEdge.value
  if (!edge || !canEdit.value) return
  if (edge.draft) {
    removeDraftByElementId(edge.elementId)
    clearSelection()
    return
  }
  if (!window.confirm(t('graph.confirmDeleteEdge', { type: edge.relType }))) return
  const op = {
    op: 'REL_DELETE',
    ref: { elementId: edge.elementId },
    soft: true,
  }
  draftOps.value.push(op)
  overlayDraftOp(op)
  planWarnings.value = []
  message.success(t('graph.draftAdded'))
}

function removeDraftAt(index: number) {
  const op = draftOps.value[index]
  if (!op) return
  if (op.op === 'NODE_CREATE' && typeof op.tempId === 'string') {
    const tempId = op.tempId
    draftSideEffects.value = draftSideEffects.value.filter((e) => e.tempId !== tempId)
  }
  if (op.op === 'NODE_UPDATE') {
    const ref = op.ref as { elementId?: string } | undefined
    const set = (op.set || {}) as Record<string, unknown>
    if (ref?.elementId && set.hasCredential != null && cy) {
      const pgAssetId = cy.$id(ref.elementId).data('pgAssetId')
      if (pgAssetId != null) {
        draftSideEffects.value = draftSideEffects.value.filter(
          (e) => !(e.effect === 'CREDENTIAL_UPSERT_REF' && Number(e.pgAssetId) === Number(pgAssetId)),
        )
      }
    }
  }
  draftOps.value.splice(index, 1)
  planWarnings.value = []
  clearSelection()
  void loadSnapshot()
}

function removeDraftByElementId(elementId: string) {
  const idx = draftOps.value.findIndex((op) => {
    if (op.op === 'REL_CREATE') {
      const props = (op.properties || {}) as Record<string, unknown>
      return String(props.elementId) === elementId
    }
    if (op.op === 'NODE_CREATE') {
      const props = (op.properties || {}) as Record<string, unknown>
      return String(props.elementId) === elementId
    }
    return false
  })
  if (idx >= 0) removeDraftAt(idx)
}

function clearDraft() {
  draftOps.value = []
  draftSideEffects.value = []
  planWarnings.value = []
  edgeSourceId = null
  clearSelection()
  void loadSnapshot()
}

async function runCypher() {
  cypherBusy.value = true
  try {
    const res = await queryGraph(cypher.value)
    if (!res.success || !res.data) {
      message.error(res.message || t('common.failed'))
      return
    }
    matchedIds.value = res.data.matchedElementIds || []
    applyHighlight(matchedIds.value)
    message.success(
      t('graph.queryDone', {
        n: matchedIds.value.length,
        ms: res.data.elapsedMs,
      }),
    )
  } finally {
    cypherBusy.value = false
  }
}

function clearHighlight() {
  matchedIds.value = []
  applyHighlight([])
}

async function submitProposal() {
  if (!draftOps.value.length) {
    message.warning(t('graph.draftEmpty'))
    return
  }
  submitBusy.value = true
  try {
    const planRes = await planGraph({
      summary: t('graph.defaultSummary'),
      ops: draftOps.value,
      pgSideEffects: draftSideEffects.value,
    })
    if (!planRes.success || !planRes.data) {
      message.error(planRes.message || t('common.failed'))
      return
    }
    planWarnings.value = planRes.data.warnings || []
    if (planWarnings.value.length) {
      message.warning(t('graph.planHasWarnings', { n: planWarnings.value.length }))
    }
    const createRes = await createProposal({
      partitionKey: planRes.data.partitionKey,
      summary: t('graph.defaultSummary'),
      changeSetJson: planRes.data.changeSetJson,
      risk: planRes.data.estimatedRisk,
      baseVersion: planRes.data.partitionBaseVersion,
      baseGraphVersion: planRes.data.baseGraphVersion,
      source: 'ui_canvas',
      factOps: [],
    })
    if (!createRes.success) {
      message.error(createRes.message || t('common.failed'))
      return
    }
    message.success(t('graph.proposalSubmitted', { id: createRes.data?.id }))
    draftOps.value = []
    draftSideEffects.value = []
    planWarnings.value = []
    await router.push({ name: 'architecture-proposals' })
  } finally {
    submitBusy.value = false
  }
}

async function openTerminal(assetId: number, elementId: string) {
  try {
    await touchTerminalDock({ assetId, elementId })
  } catch {
    // non-blocking
  }
  void router.push({ name: 'terminal', params: { assetId: String(assetId) } })
}

async function openSelectedTerminal() {
  const node = selectedNode.value
  if (!node?.pgAssetId) return
  await openTerminal(node.pgAssetId, node.elementId)
}

async function testSelectedConnection() {
  const node = selectedNode.value
  if (!node?.pgAssetId) return
  testBusy.value = true
  try {
    const res = await testSavedAssetConnection(node.pgAssetId)
    if (!res.success || !res.data) {
      message.error(res.message || t('common.failed'))
      return
    }
    if (res.data.ok) {
      message.success(t('graph.testOk', { ms: res.data.latencyMs, detail: res.data.message }))
    } else {
      message.error(t('graph.testFail', { detail: res.data.message }))
    }
  } finally {
    testBusy.value = false
  }
}

watch(edgeMode, (v) => {
  if (!v) edgeSourceId = null
})

onMounted(() => {
  void loadSnapshot()
})

onBeforeUnmount(() => {
  cy?.destroy()
  cy = null
})
</script>

<template>
  <div class="graph-page">
    <PageHeader :title="t('graph.title')" :description="t('graph.subtitle')">
      <template #extra>
        <NSpace>
          <NButton @click="router.push({ name: 'topology' })">{{ t('graph.backToTopology') }}</NButton>
          <NTag size="small" :type="graphEnabled ? 'success' : 'warning'">
            {{ graphEnabled ? t('graph.enabled') : t('graph.disabled') }}
          </NTag>
          <NTag size="small">v{{ graphVersion }}</NTag>
          <NButton :loading="loading" @click="loadSnapshot">{{ t('common.refresh') }}</NButton>
          <NButton v-if="canEdit" @click="showAddNode = true">{{ t('graph.addNode') }}</NButton>
          <NButton
            v-if="canEdit"
            :type="edgeMode ? 'primary' : 'default'"
            @click="edgeMode = !edgeMode"
          >
            {{ edgeMode ? t('graph.edgeModeOn') : t('graph.edgeMode') }}
          </NButton>
          <NSelect
            v-if="edgeMode"
            v-model:value="edgeType"
            :options="edgeTypeOptions"
            style="width: 160px"
            size="small"
          />
          <NButton v-if="draftCount" @click="showDraftPanel = true">
            {{ t('graph.draftTitle') }} ({{ draftCount }})
          </NButton>
          <NButton v-if="draftCount" @click="clearDraft">{{ t('graph.clearDraft') }}</NButton>
          <NButton
            v-if="canEdit"
            type="primary"
            :disabled="!draftCount"
            :loading="submitBusy"
            @click="submitProposal"
          >
            {{ t('graph.submitProposal') }}
          </NButton>
        </NSpace>
      </template>
    </PageHeader>

    <NAlert v-if="!graphEnabled" type="warning" class="graph-alert" :bordered="false">
      {{ t('graph.disabledHint') }}
    </NAlert>

    <div class="graph-cypher">
      <NInput
        v-model:value="cypher"
        type="textarea"
        :rows="2"
        :placeholder="t('graph.cypherPlaceholder')"
      />
      <NSpace class="graph-cypher__actions">
        <NButton type="primary" :loading="cypherBusy" @click="runCypher">{{ t('graph.runCypher') }}</NButton>
        <NButton @click="clearHighlight">{{ t('graph.clearHighlight') }}</NButton>
        <span class="hint">{{ t('graph.cypherHint') }}</span>
      </NSpace>
    </div>

    <div class="graph-workbench">
      <div ref="containerRef" class="graph-canvas" />

      <div v-if="selection" class="graph-float">
        <template v-if="selectedNode">
          <div class="graph-float__meta">
            <strong>{{ selectedNode.name }}</strong>
            <span>{{ selectedNode.kind }}</span>
            <span v-if="selectedNode.host">{{ selectedNode.host }}</span>
          </div>
          <NSpace size="small">
            <NButton
              v-if="canOpenTerminal"
              size="small"
              type="primary"
              @click="openSelectedTerminal"
            >
              {{ t('graph.openTerminal') }}
            </NButton>
            <NButton
              v-if="canTestConnection"
              size="small"
              :loading="testBusy"
              @click="testSelectedConnection"
            >
              {{ t('graph.testConnection') }}
            </NButton>
            <NButton
              v-if="canEdit && selectedIsMerged"
              size="small"
              @click="openEditModal"
            >
              {{ t('graph.editNode') }}
            </NButton>
            <NButton
              v-if="canEdit && canUpdateCredential"
              size="small"
              @click="openCredentialModal"
            >
              {{ t('graph.updateCredential') }}
            </NButton>
            <NButton
              v-if="canEdit && selectedIsMerged"
              size="small"
              type="error"
              secondary
              @click="softDeleteSelectedNode"
            >
              {{ t('graph.softDelete') }}
            </NButton>
            <NButton
              v-if="canEdit && selectedNode.draft"
              size="small"
              secondary
              @click="removeDraftByElementId(selectedNode.elementId)"
            >
              {{ t('graph.removeFromDraft') }}
            </NButton>
            <NButton size="small" quaternary @click="clearSelection">{{ t('common.cancel') }}</NButton>
          </NSpace>
        </template>
        <template v-else-if="selectedEdge">
          <div class="graph-float__meta">
            <strong>{{ selectedEdge.relType }}</strong>
          </div>
          <NSpace size="small">
            <NButton
              v-if="canEdit"
              size="small"
              type="error"
              secondary
              @click="deleteSelectedEdge"
            >
              {{ selectedEdge.draft ? t('graph.removeFromDraft') : t('graph.deleteEdge') }}
            </NButton>
            <NButton size="small" quaternary @click="clearSelection">{{ t('common.cancel') }}</NButton>
          </NSpace>
        </template>
      </div>
    </div>

    <NModal v-model:show="showDraftPanel" preset="card" :title="`${t('graph.draftTitle')} (${draftCount})`" style="width: 520px">
      <p v-if="!draftCount" class="graph-panel__empty">{{ t('graph.draftListEmpty') }}</p>
      <ul v-else class="draft-list">
        <li v-for="item in draftSummaries" :key="item.index" class="draft-list__item">
          <span>{{ item.text }}</span>
          <NButton size="tiny" quaternary @click="removeDraftAt(item.index)">{{ t('common.delete') }}</NButton>
        </li>
      </ul>
      <NAlert
        v-if="planWarnings.length"
        type="warning"
        :bordered="false"
        class="plan-warnings"
        :title="t('graph.planWarningsTitle')"
      >
        <ul>
          <li v-for="(w, i) in planWarnings" :key="i">{{ w }}</li>
        </ul>
      </NAlert>
      <template #footer>
        <NSpace justify="end">
          <NButton :disabled="!draftCount" @click="clearDraft">{{ t('graph.clearDraft') }}</NButton>
          <NButton
            v-if="canEdit"
            type="primary"
            :disabled="!draftCount"
            :loading="submitBusy"
            @click="submitProposal"
          >
            {{ t('graph.submitProposal') }}
          </NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showAddNode" preset="card" :title="t('graph.addNode')" style="width: 480px">
      <NForm label-placement="top">
        <NFormItem :label="t('assets.name')">
          <NInput v-model:value="addForm.name" />
        </NFormItem>
        <NFormItem :label="t('assets.kind')">
          <NSelect v-model:value="addForm.kind" :options="kindOptions" />
        </NFormItem>
        <NFormItem v-if="showAddSlug" :label="t('graph.slug')">
          <NInput v-model:value="addForm.slug" :placeholder="t('graph.slugPlaceholder')" />
        </NFormItem>
        <NFormItem v-if="showAddHost" :label="t('assets.host')">
          <NInput v-model:value="addForm.host" />
        </NFormItem>
        <NFormItem v-if="showAddPort" :label="t('assets.port')">
          <NInputNumber v-model:value="addForm.port" :min="0" :max="65535" style="width: 100%" />
        </NFormItem>
        <NFormItem v-if="addNeedsCredential" :label="t('graph.attachCredential')">
          <NButton
            size="small"
            :type="addForm.withCredential ? 'primary' : 'default'"
            @click="addForm.withCredential = !addForm.withCredential"
          >
            {{ addForm.withCredential ? t('graph.credentialOn') : t('graph.credentialOff') }}
          </NButton>
        </NFormItem>
        <template v-if="showAddCredentialFields">
          <NFormItem :label="t('assets.sshUser')">
            <NInput v-model:value="addForm.username" />
          </NFormItem>
          <NFormItem :label="t('assets.authType')">
            <NSelect v-model:value="addForm.authType" :options="authTypeOptions" />
          </NFormItem>
          <NFormItem :label="t('assets.sshSecret')">
            <NInput
              v-model:value="addForm.secret"
              :type="addForm.authType === 'PRIVATE_KEY' ? 'textarea' : 'password'"
              :rows="addForm.authType === 'PRIVATE_KEY' ? 4 : 1"
              :placeholder="t('graph.credentialStagingHint')"
            />
          </NFormItem>
        </template>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showAddNode = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="addBusy" @click="addDraftNode">{{ t('graph.addToDraft') }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showEditNode" preset="card" :title="t('graph.editNode')" style="width: 480px">
      <NForm label-placement="top">
        <NFormItem :label="t('assets.name')">
          <NInput v-model:value="editForm.name" />
        </NFormItem>
        <NFormItem v-if="showEditSlug" :label="t('graph.slug')">
          <NInput v-model:value="editForm.slug" />
        </NFormItem>
        <NFormItem v-if="showEditHostFields" :label="t('assets.host')">
          <NInput v-model:value="editForm.host" />
        </NFormItem>
        <NFormItem v-if="showEditPortFields" :label="t('assets.port')">
          <NInputNumber v-model:value="editForm.port" :min="0" :max="65535" style="width: 100%" />
        </NFormItem>
        <NFormItem :label="t('graph.enabledFlag')">
          <NSwitch v-model:value="editForm.enabled" />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showEditNode = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="editBusy" @click="saveEditDraft">{{ t('graph.addToDraft') }}</NButton>
        </NSpace>
      </template>
    </NModal>

    <NModal v-model:show="showCredential" preset="card" :title="t('graph.updateCredential')" style="width: 480px">
      <NForm label-placement="top">
        <NFormItem :label="t('assets.sshUser')">
          <NInput v-model:value="credForm.username" />
        </NFormItem>
        <NFormItem :label="t('assets.authType')">
          <NSelect v-model:value="credForm.authType" :options="authTypeOptions" />
        </NFormItem>
        <NFormItem :label="t('assets.sshSecret')">
          <NInput
            v-model:value="credForm.secret"
            :type="credForm.authType === 'PRIVATE_KEY' ? 'textarea' : 'password'"
            :rows="credForm.authType === 'PRIVATE_KEY' ? 4 : 1"
            :placeholder="t('graph.credentialStagingHint')"
          />
        </NFormItem>
      </NForm>
      <template #footer>
        <NSpace justify="end">
          <NButton @click="showCredential = false">{{ t('common.cancel') }}</NButton>
          <NButton type="primary" :loading="credBusy" @click="saveCredentialDraft">{{ t('graph.addToDraft') }}</NButton>
        </NSpace>
      </template>
    </NModal>
  </div>
</template>

<style scoped>
.graph-page {
  display: flex;
  flex-direction: column;
  gap: var(--co-space-3);
  min-height: calc(100vh - 120px);
}

.graph-alert {
  margin-bottom: 0;
}

.graph-cypher {
  display: flex;
  flex-direction: column;
  gap: var(--co-space-2);
}

.graph-cypher__actions {
  align-items: center;
}

.hint {
  color: var(--co-text-muted);
  font-size: 0.8rem;
}

.graph-workbench {
  position: relative;
  flex: 1;
  min-height: 520px;
}

.graph-canvas {
  width: 100%;
  height: 100%;
  min-height: 520px;
  border: 1px solid var(--co-border);
  border-radius: 8px;
  background:
    radial-gradient(circle at 20% 20%, rgba(59, 130, 246, 0.08), transparent 40%),
    radial-gradient(circle at 80% 0%, rgba(168, 85, 247, 0.06), transparent 35%),
    #0b1220;
}

.graph-float {
  position: absolute;
  left: 50%;
  bottom: 16px;
  transform: translateX(-50%);
  z-index: 5;
  display: flex;
  flex-direction: column;
  gap: 8px;
  align-items: center;
  max-width: calc(100% - 24px);
  padding: 10px 14px;
  border-radius: 10px;
  border: 1px solid var(--co-border);
  background: color-mix(in srgb, var(--co-bg-card) 92%, transparent);
  backdrop-filter: blur(8px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.28);
}

.graph-float__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 12px;
  font-size: 0.8125rem;
  color: var(--co-text-secondary);
}

.graph-float__meta strong {
  color: var(--co-text);
}

.graph-panel__empty {
  margin: 0;
  color: var(--co-text-muted);
  font-size: 0.8125rem;
}

.draft-list {
  list-style: none;
  margin: 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow: auto;
}

.draft-list__item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
  font-size: 0.8125rem;
  line-height: 1.4;
  padding: 6px 8px;
  border-radius: 6px;
  background: var(--co-bg-page);
}

.plan-warnings {
  margin-top: 8px;
}

.plan-warnings ul {
  margin: 0;
  padding-left: 1.1rem;
}
</style>
