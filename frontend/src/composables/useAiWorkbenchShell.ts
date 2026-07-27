import { computed, ref, watch } from 'vue'
import { useRoute } from 'vue-router'

const STORAGE_KEY = 'archops.aiWorkbenchShell'
const CONVERSATION_KEY = 'archops.aiSideRail.conversationId'

/** Routes where the AI side rail may dock beside the main work surface. */
export const AI_RAIL_ROUTE_NAMES = new Set([
  'terminal',
  'topology',
  'graph',
  'architecture',
])

export type AiRailSurface = 'terminal' | 'topology' | 'graph' | 'architecture'

interface ShellState {
  pinned: boolean
  open: boolean
}

function readShellState(): ShellState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY)
    if (!raw) return { pinned: false, open: false }
    const parsed = JSON.parse(raw) as Partial<ShellState>
    return {
      pinned: Boolean(parsed.pinned),
      open: Boolean(parsed.open),
    }
  } catch {
    return { pinned: false, open: false }
  }
}

const saved = typeof localStorage !== 'undefined' ? readShellState() : {
  pinned: false,
  open: false,
}

const pinned = ref(saved.pinned)
const open = ref(saved.open)
const isMobile = ref(false)

let mediaBound = false

function bindMedia() {
  if (mediaBound || typeof window === 'undefined') return
  const mq = window.matchMedia('(max-width: 900px)')
  isMobile.value = mq.matches
  mq.addEventListener('change', (event) => {
    isMobile.value = event.matches
  })
  mediaBound = true
}

bindMedia()

watch([pinned, open], () => {
  if (typeof localStorage === 'undefined') return
  localStorage.setItem(
    STORAGE_KEY,
    JSON.stringify({
      pinned: pinned.value,
      open: open.value,
    }),
  )
})

/**
 * Workbench shell UI state: pin/open AI rail.
 * Module-singleton so AppLayout and rail share one source of truth.
 */
export function useAiWorkbenchShell() {
  const route = useRoute()
  bindMedia()

  const railAllowed = computed(() => {
    const name = typeof route.name === 'string' ? route.name : ''
    return AI_RAIL_ROUTE_NAMES.has(name)
  })

  const surface = computed<AiRailSurface | null>(() => {
    const name = typeof route.name === 'string' ? route.name : ''
    if (!AI_RAIL_ROUTE_NAMES.has(name)) return null
    return name as AiRailSurface
  })

  /** Desktop: right sider docked when pinned on allowed routes (open controls width). */
  const showDesktopRail = computed(
    () => railAllowed.value && pinned.value && !isMobile.value,
  )

  /** Mobile: drawer when open on allowed routes. */
  const showMobileRailDrawer = computed(
    () => railAllowed.value && open.value && isMobile.value,
  )

  function toggleOpen() {
    open.value = !open.value
    if (open.value && !pinned.value && !isMobile.value) {
      pinned.value = true
    }
  }

  function togglePin() {
    pinned.value = !pinned.value
    if (pinned.value) {
      open.value = true
    }
  }

  function setOpen(value: boolean) {
    open.value = value
    if (value && !isMobile.value) {
      pinned.value = true
    }
  }

  function readConversationId(): number | null {
    const raw = localStorage.getItem(CONVERSATION_KEY)
    if (!raw) return null
    const id = Number(raw)
    return Number.isFinite(id) && id > 0 ? id : null
  }

  function writeConversationId(id: number | null) {
    if (id == null) {
      localStorage.removeItem(CONVERSATION_KEY)
      return
    }
    localStorage.setItem(CONVERSATION_KEY, String(id))
  }

  return {
    pinned,
    open,
    isMobile,
    railAllowed,
    surface,
    showDesktopRail,
    showMobileRailDrawer,
    toggleOpen,
    togglePin,
    setOpen,
    readConversationId,
    writeConversationId,
  }
}
