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
import { useAuthStore } from '@/stores/auth'
import { isOperatorOrAdmin } from '@/utils/roles'

const message = useMessage()
const router = useRouter()
const auth = useAuthStore()
const canEdit = computed(() => isOperatorOrAdmin(auth.user?.roles))

const loading = ref(false)
const graphEnabled = ref(false)
const graphVersion = ref(0)
const cypher = ref('MATCH (n:Asset) WHERE n.kind = \'SERVER\' RETURN n LIMIT 50')
const cypherBusy = ref(false)
const matchedIds = ref<string[]>([])
const draftOps = ref<Record<string, unknown>[]>([])
const draftSideEffects = ref<Record<string, unknown>[]>([])
const edgeMode = ref(false)
const edgeType = ref('MEMBER_OF')
const showAddNode = ref(false)
const addForm = ref({
  name: '',
  kind: 'SERVER',
  host: '',
  port: 22 as number | null,
  withCredential: false,
  username: '',
  authType: 'PASSWORD',
  secret: '',
})
const submitBusy = ref(false)
const addBusy = ref(false)
const containerRef = ref<HTMLDivElement | null>(null)

let cy: Core | null = null
let edgeSourceId: string | null = null

const edgeTypeOptions = [
  { label: 'MEMBER_OF', value: 'MEMBER_OF' },
  { label: 'RUNS_ON', value: 'RUNS_ON' },
  { label: 'DEPENDS_ON', value: 'DEPENDS_ON' },
  { label: 'CONNECTS_VIA', value: 'CONNECTS_VIA' },
  { label: 'HAS_TAG', value: 'HAS_TAG' },
]

const kindOptions = [
  { label: t('assets.kindServer'), value: 'SERVER' },
  { label: t('assets.kindCluster'), value: 'CLUSTER' },
  { label: t('assets.kindService'), value: 'SERVICE' },
  { label: t('assets.kindDatabase'), value: 'DATABASE' },
  { label: t('assets.kindNetwork'), value: 'NETWORK' },
  { label: t('assets.kindTag'), value: 'TAG' },
  { label: t('assets.kindEnvironment'), value: 'ENVIRONMENT' },
]

const draftCount = computed(() => draftOps.value.length)

const authTypeOptions = [
  { label: t('assets.password'), value: 'PASSWORD' },
  { label: t('assets.privateKey'), value: 'PRIVATE_KEY' },
]

const needsCredentialKinds = new Set(['SERVER', 'DATABASE'])
const showCredentialFields = computed(
  () => addForm.value.withCredential && needsCredentialKinds.has(addForm.value.kind),
)

function nodeLabel(n: GraphNode) {
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
        selector: 'node[kind = "TAG"]',
        style: { 'background-color': '#14b8a6', shape: 'diamond', width: 44, height: 44 },
      },
      {
        selector: 'node[kind = "DATABASE"]',
        style: { 'background-color': '#f59e0b' },
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
        selector: '.draft',
        style: {
          'border-style': 'dashed',
          'border-color': '#34d399',
          'line-style': 'dashed',
          'line-color': '#34d399',
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
  })

  cy.on('cxttap', 'node', (evt) => {
    const data = evt.target.data()
    if (data.kind === 'SERVER' && data.pgAssetId) {
      void openTerminal(Number(data.pgAssetId), String(data.id))
    }
  })

  applyHighlight(matchedIds.value)
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
    // re-apply draft overlays
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
      data: { id, source, target, label: String(op.type), type: String(op.type), draft: true },
      classes: 'draft',
    })
  }
}

async function addDraftNode() {
  if (!addForm.value.name.trim()) {
    message.warning(t('graph.nameRequired'))
    return
  }
  if (showCredentialFields.value) {
    if (!addForm.value.username.trim() || !addForm.value.secret.trim()) {
      message.warning(t('graph.credentialRequired'))
      return
    }
  }
  addBusy.value = true
  try {
    const tempId = `tmp:node:${Date.now()}`
    const elementId = crypto.randomUUID()
    const op = {
      op: 'NODE_CREATE',
      tempId,
      labels: ['Asset', kindLabel(addForm.value.kind)],
      properties: {
        elementId,
        name: addForm.value.name.trim(),
        kind: addForm.value.kind,
        host: addForm.value.host || null,
        port: addForm.value.port,
        enabled: true,
        hasCredential: showCredentialFields.value,
      },
    }
    draftOps.value.push(op)
    if (showCredentialFields.value) {
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
    addForm.value = {
      name: '',
      kind: 'SERVER',
      host: '',
      port: 22,
      withCredential: false,
      username: '',
      authType: 'PASSWORD',
      secret: '',
    }
    message.success(t('graph.draftAdded'))
  } finally {
    addBusy.value = false
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

function addDraftEdge(fromId: string, toId: string, type: string) {
  const op = {
    op: 'REL_CREATE',
    type,
    from: { elementId: fromId },
    to: { elementId: toId },
    properties: { elementId: crypto.randomUUID() },
  }
  draftOps.value.push(op)
  overlayDraftOp(op)
  message.success(t('graph.edgeDrafted', { type }))
}

function clearDraft() {
  draftOps.value = []
  draftSideEffects.value = []
  edgeSourceId = null
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
          <NTag size="small" :type="graphEnabled ? 'success' : 'warning'">
            {{ graphEnabled ? t('graph.enabled') : t('graph.disabled') }}
          </NTag>
          <NTag size="small">v{{ graphVersion }}</NTag>
          <NButton @click="loadSnapshot" :loading="loading">{{ t('common.refresh') }}</NButton>
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
          <NButton v-if="draftCount" @click="clearDraft">{{ t('graph.clearDraft') }} ({{ draftCount }})</NButton>
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

    <div ref="containerRef" class="graph-canvas" />

    <NModal v-model:show="showAddNode" preset="card" :title="t('graph.addNode')" style="width: 480px">
      <NForm label-placement="top">
        <NFormItem :label="t('assets.name')">
          <NInput v-model:value="addForm.name" />
        </NFormItem>
        <NFormItem :label="t('assets.kind')">
          <NSelect v-model:value="addForm.kind" :options="kindOptions" />
        </NFormItem>
        <NFormItem :label="t('assets.host')">
          <NInput v-model:value="addForm.host" />
        </NFormItem>
        <NFormItem :label="t('assets.port')">
          <NInputNumber v-model:value="addForm.port" :min="0" :max="65535" style="width: 100%" />
        </NFormItem>
        <NFormItem v-if="needsCredentialKinds.has(addForm.kind)" :label="t('graph.attachCredential')">
          <NSpace align="center">
            <NButton
              size="small"
              :type="addForm.withCredential ? 'primary' : 'default'"
              @click="addForm.withCredential = !addForm.withCredential"
            >
              {{ addForm.withCredential ? t('graph.credentialOn') : t('graph.credentialOff') }}
            </NButton>
          </NSpace>
        </NFormItem>
        <template v-if="showCredentialFields">
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

.graph-canvas {
  flex: 1;
  min-height: 520px;
  border: 1px solid var(--co-border);
  border-radius: 8px;
  background:
    radial-gradient(circle at 20% 20%, rgba(59, 130, 246, 0.08), transparent 40%),
    radial-gradient(circle at 80% 0%, rgba(168, 85, 247, 0.06), transparent 35%),
    #0b1220;
}
</style>
