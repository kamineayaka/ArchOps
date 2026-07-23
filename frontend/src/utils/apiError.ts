import axios from 'axios'

/** Extract a human-readable message from an API failure or thrown error. */
export function apiErrorMessage(err: unknown, fallback: string): string {
  if (axios.isAxiosError(err)) {
    const msg = err.response?.data?.message
    if (typeof msg === 'string' && msg.trim()) return msg
  }
  if (err instanceof Error && err.message.trim()) return err.message
  return fallback
}

/** Whether a provider test status string indicates failure. */
export function isProviderTestFailed(status: string | undefined | null): boolean {
  if (!status) return true
  const s = status.trim().toLowerCase()
  return s !== 'ok' && !s.startsWith('ok')
}
