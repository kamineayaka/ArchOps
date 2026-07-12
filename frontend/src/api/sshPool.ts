import client from './client'
import type { ApiResponse } from './types'

export interface SshPoolEntry {
  assetId: number
  refCount: number
  lastUsed: string
  alive: boolean
}

export async function listSshPool() {
  const { data } = await client.get<ApiResponse<SshPoolEntry[]>>('/api/ssh/pool')
  return data
}

export async function warmSshPool(assetId: number) {
  const { data } = await client.post<ApiResponse<null>>(`/api/ssh/pool/${assetId}/warm`)
  return data
}

export async function evictSshPool(assetId: number) {
  const { data } = await client.delete<ApiResponse<null>>(`/api/ssh/pool/${assetId}`)
  return data
}
