<script setup lang="ts">
import { t } from '@/messages'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { NButton, NCard, NIcon, NTag, useMessage } from 'naive-ui'
import { ChatbubbleEllipsesOutline, CloseOutline, RefreshOutline } from '@vicons/ionicons5'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { listAssets, type Asset } from '@/api/assets'
import { touchTerminalDock } from '@/api/graph'
import { listSshPool, warmSshPool, type SshPoolEntry } from '@/api/sshPool'
import { connectActionFor } from '@/assetTypes/registry'
import '@/assetTypes'
import { useAiWorkbenchShell } from '@/composables/useAiWorkbenchShell'
import { useTerminalSessions } from '@/composables/useTerminalSessions'
import { useTheme } from '@/composables/useTheme'
import TerminalSessionDock from '@/components/TerminalSessionDock.vue'
import { xtermTheme } from '@/theme/tokens'
import '@xterm/xterm/css/xterm.css'

interface LiveSession {
  term: Terminal
  fit: FitAddon
  ws: WebSocket | null
  el: HTMLDivElement | null
  onDataDisposable: { dispose: () => void } | null
}

const route = useRoute()
const router = useRouter()
const message = useMessage()
const { isDark } = useTheme()
const { setOpen } = useAiWorkbenchShell()
const { tabs, activeAssetId, activeTab, openOrFocus, closeTab, setStatus, setActive } =
  useTerminalSessions()

const assets = ref<Asset[]>([])
const poolEntries = ref<SshPoolEntry[]>([])
const paneRefs = new Map<number, HTMLDivElement>()
const live = new Map<number, LiveSession>()
const elapsedLabel = ref('--:--')
let elapsedTimer: ReturnType<typeof setInterval> | null = null

const statusLabel = computed(() => {
  const s = activeTab.value?.status
  if (s === 'connected') return t('terminal.statusConnected')
  if (s === 'connecting') return t('terminal.statusConnecting')
  if (s === 'error') return t('terminal.statusError')
  if (s === 'disconnected') return t('terminal.statusDisconnected')
  return t('terminal.statusIdle')
})

const statusType = computed(() => {
  const s = activeTab.value?.status
  if (s === 'connected') return 'success'
  if (s === 'error') return 'error'
  if (s === 'connecting') return 'warning'
  return 'default'
})

const statusDetail = computed(() => {
  const tab = activeTab.value
  if (!tab) return t('terminal.hintSelectTree')
  const userHost = tab.host ? `${tab.usernameHint}@${tab.host}` : tab.title
  if (tab.status === 'connected') return `${userHost} · ${elapsedLabel.value}`
  if (tab.status === 'error') return tab.errorMessage || t('terminal.statusError')
  return userHost
})

function terminalTheme() {
  return { ...xtermTheme }
}

function setPaneRef(assetId: number, el: unknown) {
  if (el instanceof HTMLDivElement) {
    paneRefs.set(assetId, el)
  } else {
    paneRefs.delete(assetId)
  }
}

function isPooled(assetId: number) {
  return poolEntries.value.some((e) => e.assetId === assetId && e.alive)
}

async function loadAssets() {
  const res = await listAssets()
  if (res.success && res.data) {
    assets.value = res.data.filter(
      (a) => a.hasSshCredential && connectActionFor(a.kind) === 'terminal',
    )
  }
}

async function refreshPool() {
  const res = await listSshPool()
  if (res.success && res.data) poolEntries.value = res.data
}

function ensureLive(assetId: number): LiveSession | null {
  const existing = live.get(assetId)
  if (existing) return existing
  const el = paneRefs.get(assetId)
  if (!el) return null
  const term = new Terminal({
    cursorBlink: true,
    fontSize: 14,
    theme: terminalTheme(),
    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace',
  })
  const fit = new FitAddon()
  term.loadAddon(fit)
  term.open(el)
  fit.fit()
  term.writeln(t('terminal.hintAutoConnect'))
  const session: LiveSession = { term, fit, ws: null, el, onDataDisposable: null }
  live.set(assetId, session)
  return session
}

function sendResize(assetId: number) {
  const session = live.get(assetId)
  if (!session?.ws || session.ws.readyState !== WebSocket.OPEN) return
  session.ws.send(JSON.stringify({ type: 'resize', cols: session.term.cols, rows: session.term.rows }))
}

function disconnectSession(assetId: number, disposeTerm = false) {
  const session = live.get(assetId)
  if (!session) return
  session.onDataDisposable?.dispose()
  session.onDataDisposable = null
  session.ws?.close()
  session.ws = null
  if (disposeTerm) {
    session.term.dispose()
    live.delete(assetId)
  }
}

async function connectSession(assetId: number) {
  await nextTick()
  const session = ensureLive(assetId)
  if (!session) return
  setStatus(assetId, 'connecting')
  try {
    await warmSshPool(assetId)
    await refreshPool()
    disconnectSession(assetId, false)
    const token = localStorage.getItem('accessToken')
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/ws/terminal?token=${token}&assetId=${assetId}`
    const ws = new WebSocket(url)
    session.ws = ws
    ws.onopen = () => {
      setStatus(assetId, 'connected')
      session.fit.fit()
      sendResize(assetId)
      session.term.writeln(`\r\n[ArchOps] ${t('terminal.statusConnected')} (${t('terminal.pooled')}).\r\n`)
    }
    ws.onmessage = (e) => session.term.write(e.data)
    ws.onclose = () => {
      if (tabs.value.some((tab) => tab.assetId === assetId && tab.status === 'connected')) {
        setStatus(assetId, 'disconnected')
        session.term.writeln(`\r\n[ArchOps] ${t('terminal.statusDisconnected')}.\r\n`)
      }
      void refreshPool()
    }
    ws.onerror = () => {
      setStatus(assetId, 'error', t('terminal.connectFailed'))
      session.term.writeln(`\r\n[ArchOps] ${t('terminal.statusError')}.\r\n`)
    }
    session.onDataDisposable = session.term.onData((data) => {
      if (ws.readyState === WebSocket.OPEN) ws.send(data)
    })
  } catch {
    setStatus(assetId, 'error', t('terminal.connectFailed'))
    message.error(t('terminal.connectFailed'))
  }
}

function openAssetById(assetId: number) {
  const asset = assets.value.find((a) => a.id === assetId)
  if (!asset) {
    message.warning(t('terminal.assetNotConnectable'))
    return
  }
  const already = tabs.value.some((tab) => tab.assetId === assetId)
  openOrFocus(asset)
  void touchTerminalDock({
    assetId: asset.id,
    elementId: asset.elementId,
  }).catch(() => undefined)
  void router.replace({ name: 'terminal', params: { assetId: String(assetId) } })
  void nextTick(async () => {
    ensureLive(assetId)
    if (!already || live.get(assetId)?.ws?.readyState !== WebSocket.OPEN) {
      await connectSession(assetId)
    } else {
      live.get(assetId)?.fit.fit()
    }
  })
}

function handleSelectTab(assetId: number) {
  setActive(assetId)
  void router.replace({ name: 'terminal', params: { assetId: String(assetId) } })
  void nextTick(() => live.get(assetId)?.fit.fit())
}

function handleCloseTab(assetId: number) {
  disconnectSession(assetId, true)
  closeTab(assetId)
  const next = activeAssetId.value
  if (next != null) {
    void router.replace({ name: 'terminal', params: { assetId: String(next) } })
  } else {
    void router.replace({ name: 'terminal' })
  }
}

function openAiRail() {
  setOpen(true)
}

function openInAgent() {
  const id = activeAssetId.value
  if (id != null) {
    void router.push({ name: 'ai', query: { assetId: String(id) } })
  } else {
    void router.push({ name: 'ai' })
  }
}

function tickElapsed() {
  const tab = activeTab.value
  if (!tab?.connectedAt || tab.status !== 'connected') {
    elapsedLabel.value = '--:--'
    return
  }
  const sec = Math.floor((Date.now() - tab.connectedAt) / 1000)
  const m = String(Math.floor(sec / 60)).padStart(2, '0')
  const s = String(sec % 60).padStart(2, '0')
  elapsedLabel.value = `${m}:${s}`
}

watch(isDark, () => {
  for (const session of live.values()) {
    session.term.options.theme = terminalTheme()
  }
})

watch(
  () => route.params.assetId,
  (raw) => {
    const id = Number(raw)
    if (!Number.isFinite(id) || id <= 0) return
    if (!assets.value.length) return
    openAssetById(id)
  },
)

onMounted(async () => {
  await Promise.all([loadAssets(), refreshPool()])
  elapsedTimer = setInterval(tickElapsed, 1000)
  const paramId = Number(route.params.assetId)
  if (Number.isFinite(paramId) && paramId > 0) {
    openAssetById(paramId)
  }
})

onBeforeUnmount(() => {
  if (elapsedTimer) clearInterval(elapsedTimer)
  for (const assetId of [...live.keys()]) {
    disconnectSession(assetId, true)
  }
})
</script>

<template>
  <div class="terminal-ide">
    <div class="terminal-ide__tabs">
      <button
        v-for="tab in tabs"
        :key="tab.assetId"
        type="button"
        class="terminal-tab"
        :class="{ 'terminal-tab--active': tab.assetId === activeAssetId }"
        @click="handleSelectTab(tab.assetId)"
      >
        <span class="terminal-tab__dot" :data-status="tab.status" />
        <span class="terminal-tab__label">{{ tab.title }}</span>
        <button
          type="button"
          class="terminal-tab__close"
          :aria-label="t('terminal.closeTab')"
          @click.stop="handleCloseTab(tab.assetId)"
        >
          <NIcon :component="CloseOutline" :size="14" />
        </button>
      </button>
      <div class="terminal-ide__tab-spacer" />
      <NButton size="small" quaternary @click="openAiRail">
        <template #icon><NIcon :component="ChatbubbleEllipsesOutline" /></template>
        {{ t('terminal.openAiRail') }}
      </NButton>
      <NButton size="small" quaternary @click="openInAgent">
        {{ t('terminal.openInAgent') }}
      </NButton>
    </div>

    <div class="terminal-ide__status">
      <NTag :type="statusType" size="small" round>{{ statusLabel }}</NTag>
      <span class="terminal-ide__status-text">{{ statusDetail }}</span>
      <NTag
        v-if="activeAssetId && isPooled(activeAssetId)"
        type="success"
        size="small"
        round
      >
        {{ t('terminal.pooled') }}
      </NTag>
      <NButton
        v-if="activeAssetId"
        size="tiny"
        quaternary
        :aria-label="t('terminal.reconnect')"
        @click="activeAssetId && connectSession(activeAssetId)"
      >
        <template #icon><NIcon :component="RefreshOutline" /></template>
        {{ t('terminal.reconnect') }}
      </NButton>
    </div>

    <TerminalSessionDock class="terminal-ide__dock" />

    <NCard class="terminal-ide__stage" :bordered="false">
      <div v-if="!tabs.length" class="terminal-ide__empty">
        {{ t('terminal.hintSelectTree') }}
      </div>
      <div
        v-for="tab in tabs"
        :key="tab.assetId"
        class="terminal-pane"
        :class="{ 'terminal-pane--active': tab.assetId === activeAssetId }"
        :ref="(el) => setPaneRef(tab.assetId, el)"
      />
    </NCard>
  </div>
</template>

<style scoped>
.terminal-ide {
  display: flex;
  flex-direction: column;
  height: calc(100vh - var(--ao-header-height));
  min-height: 420px;
  gap: 0;
  background: var(--ao-ink);
}

.terminal-ide__dock {
  margin: 0;
  border-bottom: 1px solid var(--ao-border);
}

.terminal-ide__tabs {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 0 4px;
  border-bottom: 1px solid var(--ao-border);
  background: var(--ao-slate);
  min-height: 36px;
  flex-shrink: 0;
}

.terminal-tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  max-width: 200px;
  padding: 7px 10px;
  border: 0;
  border-radius: var(--ao-radius-sm) var(--ao-radius-sm) 0 0;
  background: transparent;
  color: var(--ao-steel);
  cursor: pointer;
  font: inherit;
  font-size: 0.8125rem;
  font-family: var(--ao-font-mono);
}

.terminal-tab--active {
  background: var(--ao-ink);
  color: #e8eef7;
  box-shadow: inset 0 -2px 0 var(--ao-blueprint);
}

.terminal-tab__dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--ao-steel);
  flex-shrink: 0;
}

.terminal-tab__dot[data-status='connected'] {
  background: var(--ao-live);
}

.terminal-tab__dot[data-status='connecting'] {
  background: var(--ao-signal);
}

.terminal-tab__dot[data-status='error'] {
  background: var(--ao-error);
}

.terminal-tab__label {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.terminal-tab__close {
  display: inline-flex;
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
  padding: 2px;
  border-radius: 3px;
  opacity: 0.7;
}

.terminal-tab__close:hover {
  opacity: 1;
  background: color-mix(in srgb, currentColor 12%, transparent);
}

.terminal-ide__tab-spacer {
  flex: 1;
}

.terminal-ide__status {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 6px 12px;
  border-bottom: 1px solid var(--ao-border);
  flex-shrink: 0;
  font-size: 12px;
  background: var(--ao-slate);
  font-family: var(--ao-font-mono);
}

.terminal-ide__status-text {
  color: var(--ao-steel);
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.terminal-ide__stage {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--ao-ink) !important;
  border-radius: 0 !important;
}

.terminal-ide__stage :deep(.n-card__content) {
  flex: 1;
  min-height: 0;
  position: relative;
  padding: 0 !important;
}

.terminal-ide__empty {
  display: grid;
  place-items: center;
  height: 100%;
  min-height: 280px;
  color: var(--ao-steel);
  padding: 24px;
  text-align: center;
  font-size: 0.875rem;
}

.terminal-pane {
  position: absolute;
  inset: 0;
  display: none;
  padding: 8px;
  background: var(--ao-ink);
}

.terminal-pane--active {
  display: block;
}
</style>
