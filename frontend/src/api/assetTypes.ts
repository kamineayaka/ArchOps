import client from './client'
import type { ApiResponse } from './types'

export interface AssetTypeInfo {
  type: string
  defaultPort: number
  policyKind: string
  connectAction: 'terminal' | 'query' | 'page' | 'none'
  authMode: 'ssh' | 'password' | 'none'
  showHost: boolean
  showPort: boolean
  showDatabaseName: boolean
  supportsTest: boolean
}

export async function listAssetTypesApi() {
  const { data } = await client.get<ApiResponse<AssetTypeInfo[]>>('/api/asset-types')
  return data
}
