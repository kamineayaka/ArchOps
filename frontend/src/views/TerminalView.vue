<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { NButton, NCard, NSelect, NSpace, NTag, useMessage } from 'naive-ui'
import { Terminal } from '@xterm/xterm'
import { FitAddon } from '@xterm/addon-fit'
import { listAssets, type Asset } from '@/api/assets'
import { listSshPool, warmSshPool, type SshPoolEntry } from '@/api/sshPool'
import '@xterm/xterm/css/xterm.css'

const { t } = useI18n()
const route = useRoute()
const message = useMessage()

const assets = ref<Asset[]>([])
const poolEntries = ref<SshPoolEntry[]>([])
const selectedAssetId = ref<number | null>(null)
const connecting = ref(false)
const terminalRef = ref<HTMLDivElement | null>(null)
let term: Terminal | null = null
let fitAddon: FitAddon | null = null
let ws: WebSocket | null = null
let resizeObserver: ResizeObserver | null = null

const assetOptions = ref<{ label: string; value: number }[]>([])

function isPooled(assetId: number) {
  return poolEntries.value.some((e) => e.assetId === assetId && e.alive)
}

async function loadAssets() {
  const res = await listAssets()
  if (res.success && res.data) {
    assets.value = res.data.filter((a) => a.hasSshCredential)
    assetOptions.value = assets.value.map((a) => ({ label: `${a.name} (${a.host})`, value: a.id }))
    const paramId = Number(route.params.assetId)
    if (paramId) selectedAssetId.value = paramId
  }
}

async function refreshPool() {
  const res = await listSshPool()
  if (res.success && res.data) {
    poolEntries.value = res.data
  }
}

function initTerminal() {
  if (!terminalRef.value) return
  term = new Terminal({ cursorBlink: true, fontSize: 14, theme: { background: '#0f172a' } })
  fitAddon = new FitAddon()
  term.loadAddon(fitAddon)
  term.open(terminalRef.value)
  fitAddon.fit()
  term.writeln('[CloudOps] Select an asset and click Connect.')

  resizeObserver = new ResizeObserver(() => {
    fitAddon?.fit()
    sendResize()
  })
  resizeObserver.observe(terminalRef.value)
}

function sendResize() {
  if (!ws || ws.readyState !== WebSocket.OPEN || !term) return
  ws.send(JSON.stringify({ type: 'resize', cols: term.cols, rows: term.rows }))
}

async function connect() {
  if (!selectedAssetId.value || !term) return
  connecting.value = true
  try {
    await warmSshPool(selectedAssetId.value)
    await refreshPool()
    disconnect()
    const token = localStorage.getItem('accessToken')
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const host = window.location.host
    const url = `${protocol}://${host}/ws/terminal?token=${token}&assetId=${selectedAssetId.value}`
    ws = new WebSocket(url)
    ws.onopen = () => {
      fitAddon?.fit()
      sendResize()
      term?.writeln('\r\n[CloudOps] Connected (pooled).\r\n')
    }
    ws.onmessage = (e) => term?.write(e.data)
    ws.onclose = () => {
      term?.writeln('\r\n[CloudOps] Disconnected.\r\n')
      refreshPool()
    }
    ws.onerror = () => term?.writeln('\r\n[CloudOps] Connection error.\r\n')

    term.onData((data) => {
      if (ws?.readyState === WebSocket.OPEN) ws.send(data)
    })
  } catch {
    message.error(t('terminal.connectFailed'))
  } finally {
    connecting.value = false
  }
}

function disconnect() {
  ws?.close()
  ws = null
}

onMounted(async () => {
  await Promise.all([loadAssets(), refreshPool()])
  initTerminal()
})

onBeforeUnmount(() => {
  disconnect()
  resizeObserver?.disconnect()
  term?.dispose()
})
</script>

<template>
  <NCard :title="t('terminal.title')">
    <NSpace style="margin-bottom: 12px" align="center">
      <NSelect
        v-model:value="selectedAssetId"
        :options="assetOptions"
        :placeholder="t('terminal.selectAsset')"
        style="width: 320px"
        clearable
      />
      <NButton type="primary" :disabled="!selectedAssetId" :loading="connecting" @click="connect">
        {{ t('terminal.connect') }}
      </NButton>
      <NTag v-if="selectedAssetId && isPooled(selectedAssetId)" type="success" size="small">
        {{ t('terminal.pooled') }}
      </NTag>
    </NSpace>
    <div ref="terminalRef" class="terminal-container" />
  </NCard>
</template>

<style scoped>
.terminal-container {
  height: calc(100vh - 220px);
  border-radius: 6px;
  overflow: hidden;
}
</style>
