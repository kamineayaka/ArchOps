export type AiStreamEventType =
  | 'conversation'
  | 'user'
  | 'token'
  | 'tool_start'
  | 'tool_result'
  | 'approval_required'
  | 'resume_start'
  | 'architecture_proposal_created'
  | 'work_log_appended'
  | 'done'
  | 'error'

export interface AiStreamEvent {
  type: AiStreamEventType
  content?: string | null
  tool?: string | null
  status?: string | null
  approvalId?: number | null
  risk?: string | null
  conversationId?: number | null
  proposalId?: number | null
}

export interface AiStreamClient {
  connect(): Promise<void>
  disconnect(): void
  sendChat(message: string, conversationId?: number, providerId?: number): void
  readonly ready: boolean
}

export function createAiStreamClient(onEvent: (event: AiStreamEvent) => void): AiStreamClient {
  let ws: WebSocket | null = null
  let openPromise: Promise<void> | null = null

  function connect(): Promise<void> {
    if (ws?.readyState === WebSocket.OPEN) {
      return Promise.resolve()
    }
    if (openPromise) {
      return openPromise
    }

    const token = localStorage.getItem('accessToken')
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${protocol}://${window.location.host}/ws/ai?token=${token}`

    openPromise = new Promise<void>((resolve, reject) => {
      ws = new WebSocket(url)
      ws.onopen = () => resolve()
      ws.onerror = () => reject(new Error('WebSocket connection failed'))
      ws.onclose = () => {
        openPromise = null
      }
      ws.onmessage = (event) => {
        try {
          onEvent(JSON.parse(event.data) as AiStreamEvent)
        } catch {
          onEvent({ type: 'error', content: 'Invalid stream payload' })
        }
      }
    })

    return openPromise.finally(() => {
      openPromise = null
    })
  }

  function disconnect() {
    ws?.close()
    ws = null
    openPromise = null
  }

  function sendChat(message: string, conversationId?: number, providerId?: number) {
    if (!ws || ws.readyState !== WebSocket.OPEN) {
      onEvent({ type: 'error', content: 'WebSocket not connected' })
      return
    }
    ws.send(
      JSON.stringify({
        type: 'chat',
        message,
        conversationId,
        providerId,
      }),
    )
  }

  return {
    connect,
    disconnect,
    sendChat,
    get ready() {
      return ws?.readyState === WebSocket.OPEN
    },
  }
}
