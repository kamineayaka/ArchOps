import client from './client'
import type { ApiResponse } from './types'

export type ProposalStatus =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'REJECTED'
  | 'AUTO_MERGED'
  | 'MERGED'

export interface PartitionSummary {
  id: number
  partitionKey: string
  title: string
  highImpact: boolean
  latestVersion: number | null
  activeFactCount: number
}

export interface FactResponse {
  id: number
  factType: string
  subject: string
  predicate: string
  object: string
  assetId: number | null
  confidence: number | null
  status: string
  provenanceJson: string | null
  revisionId: number | null
  createdAt: string
}

export interface PartitionDetail {
  id: number
  partitionKey: string
  title: string
  highImpact: boolean
  latestRevisionId: number | null
  latestVersion: number | null
  summary: string | null
  bodyMd: string | null
  structuredJson: string | null
  createdBy: number | null
  revisedAt: string | null
  facts: FactResponse[]
}

export interface ProposalResponse {
  id: number
  partitionKey: string
  status: ProposalStatus
  summary: string | null
  diffJson: string | null
  factOps: string | null
  evidence: string | null
  risk: string | null
  confidence: number | null
  requesterId: number | null
  reviewerId: number | null
  conversationId: number | null
  baseVersion: number | null
  relatedApprovalId: number | null
  createdAt: string
  decidedAt: string | null
}

function partitionPath(key: string) {
  return `/api/architecture/partitions/${encodeURIComponent(key)}`
}

export async function listPartitions() {
  const { data } = await client.get<ApiResponse<PartitionSummary[]>>('/api/architecture/partitions')
  return data
}

export async function getPartition(key: string) {
  const { data } = await client.get<ApiResponse<PartitionDetail>>(partitionPath(key))
  return data
}

export async function rollbackPartition(key: string, targetVersion: number) {
  const { data } = await client.post<ApiResponse<PartitionDetail>>(`${partitionPath(key)}/rollback`, {
    targetVersion,
  })
  return data
}

export async function listProposals(status?: ProposalStatus | null, partitionKey?: string | null) {
  const { data } = await client.get<ApiResponse<ProposalResponse[]>>('/api/architecture/proposals', {
    params: {
      ...(status ? { status } : {}),
      ...(partitionKey ? { partitionKey } : {}),
    },
  })
  return data
}

export async function getProposal(id: number) {
  const { data } = await client.get<ApiResponse<ProposalResponse>>(`/api/architecture/proposals/${id}`)
  return data
}

export async function decideProposal(id: number, decision: 'APPROVE' | 'REJECT', comment?: string) {
  const { data } = await client.post<ApiResponse<ProposalResponse>>(
    `/api/architecture/proposals/${id}/decide`,
    { decision, comment },
  )
  return data
}
