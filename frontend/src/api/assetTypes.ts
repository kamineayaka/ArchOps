import client from './client'
import type { ApiResponse } from './types'

export interface AssetTypeInfo {
  type: string
  defaultPort: number
  policyKind: string
}

export async function listAssetTypesApi() {
  const { data } = await client.get<ApiResponse<AssetTypeInfo[]>>('/api/asset-types')
  return data
}
