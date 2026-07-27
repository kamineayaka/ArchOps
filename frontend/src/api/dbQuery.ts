import client from './client'
import type { ApiResponse } from './types'

export interface DbQueryResult {
  status: string
  approvalId: number | null
  mutating: boolean
  riskLevel: string | null
  columns: string[]
  rows: unknown[][]
  rowCount: number
  truncated: boolean
  updateCount: number
  elapsedMs: number | null
  message: string | null
}

export async function runAssetQuery(assetId: number, sql: string, approvalId?: number | null) {
  const { data } = await client.post<ApiResponse<DbQueryResult>>(
    `/api/assets/${assetId}/query`,
    {
      sql,
      approvalId: approvalId ?? null,
    },
    { timeout: 60_000 },
  )
  return data
}
