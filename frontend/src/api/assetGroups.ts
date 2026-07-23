import client from './client'
import type { ApiResponse } from './types'

export interface AssetGroupMember {
  id: number
  name: string
  kind: string
  host: string | null
}

export interface AssetGroup {
  id: number
  name: string
  description: string | null
  enabled: boolean
  memberCount: number
  members: AssetGroupMember[]
  createdAt: string
  updatedAt: string
}

export interface AssetGroupRequest {
  name: string
  description?: string
  enabled?: boolean
}

export async function listAssetGroups() {
  const { data } = await client.get<ApiResponse<AssetGroup[]>>('/api/asset-groups')
  return data
}

export async function getAssetGroup(id: number) {
  const { data } = await client.get<ApiResponse<AssetGroup>>(`/api/asset-groups/${id}`)
  return data
}

export async function createAssetGroup(payload: AssetGroupRequest) {
  const { data } = await client.post<ApiResponse<AssetGroup>>('/api/asset-groups', payload)
  return data
}

export async function updateAssetGroup(id: number, payload: AssetGroupRequest) {
  const { data } = await client.put<ApiResponse<AssetGroup>>(`/api/asset-groups/${id}`, payload)
  return data
}

export async function deleteAssetGroup(id: number) {
  const { data } = await client.delete<ApiResponse<null>>(`/api/asset-groups/${id}`)
  return data
}

export async function replaceAssetGroupMembers(id: number, assetIds: number[]) {
  const { data } = await client.put<ApiResponse<AssetGroup>>(`/api/asset-groups/${id}/members`, { assetIds })
  return data
}

export async function addAssetGroupMembers(id: number, assetIds: number[]) {
  const { data } = await client.post<ApiResponse<AssetGroup>>(`/api/asset-groups/${id}/members`, { assetIds })
  return data
}
