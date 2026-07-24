import client from './client'
import type { ApiResponse } from './types'

export interface Asset {
  id: number
  name: string
  kind: string
  host: string | null
  port: number | null
  metadata: string | null
  description: string | null
  parentId: number | null
  enabled: boolean
  hasSshCredential: boolean
  jumpAssetIds: number[]
  createdAt: string
  updatedAt: string
}

export interface AssetRequest {
  name: string
  kind: string
  host?: string
  port?: number
  metadata?: string
  parentId?: number
  enabled?: boolean
  description?: string
  groupId?: number
  username?: string
  authType?: string
  secret?: string
  jumpAssetIds?: number[]
  database?: string
}

export interface TestConnectionRequest {
  assetId?: number
  kind?: string
  host?: string
  port?: number
  username?: string
  authType?: string
  secret?: string
  jumpAssetIds?: number[]
  database?: string
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

export async function createAsset(payload: AssetRequest) {
  const { data } = await client.post<ApiResponse<Asset>>('/api/assets', payload)
  return data
}

export async function deleteAsset(id: number) {
  const { data } = await client.delete<ApiResponse<null>>(`/api/assets/${id}`)
  return data
}

export async function saveSshCredential(
  assetId: number,
  payload: { username: string; authType: string; secret: string; jumpAssetIds?: number[] },
) {
  const { data } = await client.post<ApiResponse<null>>(`/api/assets/${assetId}/ssh-credential`, payload)
  return data
}

export async function testAssetConnection(payload: TestConnectionRequest) {
  const { data } = await client.post<ApiResponse<TestConnectionResponse>>('/api/assets/test-connection', payload)
  return data
}

export async function testSavedAssetConnection(assetId: number) {
  const { data } = await client.post<ApiResponse<TestConnectionResponse>>(
    `/api/assets/${assetId}/test-connection`,
  )
  return data
}
