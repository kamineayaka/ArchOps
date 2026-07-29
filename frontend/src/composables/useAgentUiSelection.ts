import { computed, ref } from 'vue'

/**
 * Cross-view selection for Agent UiContext (topology / graph workbench → side rail).
 * Module singleton so views share one source of truth without props drilling.
 */
export interface AgentUiSelection {
  pgAssetIds: number[]
  elementIds: string[]
}

const selectedPgAssetIds = ref<number[]>([])
const selectedElementIds = ref<string[]>([])

export function useAgentUiSelection() {
  const selection = computed<AgentUiSelection>(() => ({
    pgAssetIds: [...selectedPgAssetIds.value],
    elementIds: [...selectedElementIds.value],
  }))

  function setSelection(pgAssetIds: number[], elementIds: string[] = []) {
    selectedPgAssetIds.value = [...new Set(pgAssetIds.filter((id) => Number.isFinite(id) && id > 0))]
    selectedElementIds.value = [...new Set(elementIds.filter((id) => id && id.trim()))]
  }

  function setNodeSelection(pgAssetId: number | null | undefined, elementId?: string | null) {
    if (pgAssetId == null || !Number.isFinite(pgAssetId) || pgAssetId <= 0) {
      clearSelection()
      return
    }
    setSelection([pgAssetId], elementId ? [elementId] : [])
  }

  function clearSelection() {
    selectedPgAssetIds.value = []
    selectedElementIds.value = []
  }

  return {
    selectedPgAssetIds,
    selectedElementIds,
    selection,
    setSelection,
    setNodeSelection,
    clearSelection,
  }
}
