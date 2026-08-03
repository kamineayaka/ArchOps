import client from './client'
import type { ApiResponse } from './types'

export interface Asset {
  id: number
  elementId: string
  name: string
  kind: string
  host: string | null
  port: number | null
  metadata: string | null
  description: string | null
  enabled: boolean
  hasCredential: boolean
  hasSshCredential?: boolean
  deletedAt: string | null
  createdAt: string
  updatedAt: string
}

export interface TestConnectionResponse {
  ok: boolean
  latencyMs: number
  message: string
}

export async function listAssets() {
  const { data } = await client.get<ApiResponse<Asset[]>>('/api/assets')
  return data
}

export async function testSavedAssetConnection(assetId: number) {
  const { data } = await client.post<ApiResponse<TestConnectionResponse>>(
    `/api/assets/${assetId}/test-connection`,
  )
  return data
}
