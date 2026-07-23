import { computed, ref } from 'vue'

export type TerminalConnStatus = 'idle' | 'connecting' | 'connected' | 'error' | 'disconnected'

export interface TerminalSessionTab {
  assetId: number
  title: string
  host: string | null
  usernameHint: string
  status: TerminalConnStatus
  connectedAt: number | null
  errorMessage: string | null
}

const tabs = ref<TerminalSessionTab[]>([])
const activeAssetId = ref<number | null>(null)

/**
 * Multi-tab terminal session state (IDE window metaphor).
 * Module singleton so route watchers and the view share one source of truth.
 */
export function useTerminalSessions() {
  const activeTab = computed(() => tabs.value.find((t) => t.assetId === activeAssetId.value) ?? null)

  function openOrFocus(asset: {
    id: number
    name: string
    host: string | null
  }) {
    const existing = tabs.value.find((t) => t.assetId === asset.id)
    if (!existing) {
      tabs.value.push({
        assetId: asset.id,
        title: asset.name,
        host: asset.host,
        usernameHint: 'ssh',
        status: 'idle',
        connectedAt: null,
        errorMessage: null,
      })
    }
    activeAssetId.value = asset.id
  }

  function closeTab(assetId: number) {
    const idx = tabs.value.findIndex((t) => t.assetId === assetId)
    if (idx < 0) return
    tabs.value.splice(idx, 1)
    if (activeAssetId.value === assetId) {
      const next = tabs.value[Math.min(idx, tabs.value.length - 1)]
      activeAssetId.value = next?.assetId ?? null
    }
  }

  function setStatus(assetId: number, status: TerminalConnStatus, errorMessage: string | null = null) {
    const tab = tabs.value.find((t) => t.assetId === assetId)
    if (!tab) return
    tab.status = status
    tab.errorMessage = errorMessage
    if (status === 'connected') {
      tab.connectedAt = Date.now()
    }
    if (status === 'disconnected' || status === 'idle' || status === 'error') {
      if (status !== 'error') tab.connectedAt = null
    }
  }

  function setActive(assetId: number) {
    if (tabs.value.some((t) => t.assetId === assetId)) {
      activeAssetId.value = assetId
    }
  }

  return {
    tabs,
    activeAssetId,
    activeTab,
    openOrFocus,
    closeTab,
    setStatus,
    setActive,
  }
}
