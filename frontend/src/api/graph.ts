import client from './client'
import type { ApiResponse } from './types'

export interface GraphNode {
  elementId: string
  pgAssetId: number | null
  kind: string
  name: string
  host: string | null
  port: number | null
  enabled: boolean
  hasCredential: boolean
  slug: string | null
  labels: string[]
  properties: Record<string, unknown>
}

export interface GraphEdge {
  elementId: string
  type: string
  fromElementId: string
  toElementId: string
  properties: Record<string, unknown>
}

export interface GraphSnapshot {
  graphVersion: number
  nodes: GraphNode[]
  edges: GraphEdge[]
}

export interface GraphQueryResult {
  columns: string[]
  rows: Record<string, unknown>[]
  matchedElementIds: string[]
  elapsedMs: number
}

export interface GraphPlanResult {
  baseGraphVersion: number
  partitionBaseVersion: number
  partitionKey: string
  changeSetJson: string
  estimatedRisk: string
  warnings: string[]
  preview: Record<string, unknown>
}

export interface TerminalDockItem {
  id: number
  elementId: string
  assetId: number
  name: string
  kind: string
  host: string | null
  pinned: boolean
  hasSshCredential: boolean
  lastOpenedAt: string
}

export async function getGraphMeta() {
  const { data } = await client.get<ApiResponse<{ graphVersion: number; partitionKey: string }>>(
    '/api/graph/meta',
  )
  return data
}

export async function getGraphSnapshot() {
  const { data } = await client.get<ApiResponse<GraphSnapshot>>('/api/graph/snapshot')
  return data
}

export async function queryGraph(cypher: string) {
  const { data } = await client.post<ApiResponse<GraphQueryResult>>('/api/graph/query', { cypher })
  return data
}

export async function planGraph(payload: {
  summary?: string
  ops: Record<string, unknown>[]
  pgSideEffects?: Record<string, unknown>[]
}) {
  const { data } = await client.post<ApiResponse<GraphPlanResult>>('/api/graph/plan', payload)
  return data
}

export async function stageCredential(payload: {
  username: string
  authType: string
  secret: string
  assetId?: number
  tempRef?: string
}) {
  const { data } = await client.post<
    ApiResponse<{ stagingId: string; expiresAt: string; tempRef: string | null; assetId: number | null }>
  >('/api/graph/credential-staging', payload)
  return data
}

export async function listTerminalDock() {
  const { data } = await client.get<ApiResponse<TerminalDockItem[]>>('/api/terminal/dock')
  return data
}

export async function touchTerminalDock(payload: {
  elementId?: string
  assetId?: number
  pinned?: boolean
}) {
  const { data } = await client.post<ApiResponse<TerminalDockItem>>('/api/terminal/dock/touch', payload)
  return data
}

export async function pinTerminalDock(elementId: string, pinned: boolean) {
  const { data } = await client.put<ApiResponse<TerminalDockItem>>(
    `/api/terminal/dock/${elementId}/pin`,
    { pinned },
  )
  return data
}

export async function removeTerminalDock(elementId: string) {
  const { data } = await client.delete<ApiResponse<null>>(`/api/terminal/dock/${elementId}`)
  return data
}
