import client from './client'
import type { ApiResponse } from './types'

export type ProviderType = 'OPENAI_COMPAT' | 'ANTHROPIC'

export interface AiProvider {
  id: number
  name: string
  providerType: ProviderType
  baseUrl: string
  apiKeyMasked: string | null
  chatModel: string | null
  embeddingModel: string | null
  embeddingDims: number | null
  supportsChat: boolean
  supportsEmbedding: boolean
  enabled: boolean
  timeoutMs: number
  defaultChat: boolean
  defaultEmbedding: boolean
  createdAt: string
  updatedAt: string
}

export interface AiProviderRequest {
  name: string
  providerType: ProviderType
  baseUrl?: string
  apiKey?: string
  chatModel?: string
  embeddingModel?: string
  embeddingDims?: number
  supportsChat?: boolean
  supportsEmbedding?: boolean
  enabled?: boolean
  timeoutMs?: number
}

export interface PlatformAiSettings {
  defaultChatProviderId: number | null
  defaultEmbeddingProviderId: number | null
  ragEnabled: boolean
  ragTopK: number
  ragMinSimilarity: number
}

export async function listAllProviders() {
  const { data } = await client.get<ApiResponse<AiProvider[]>>('/api/ai/providers/all')
  return data
}

export async function listChatProviders() {
  const { data } = await client.get<ApiResponse<AiProvider[]>>('/api/ai/providers')
  return data
}

export async function createProvider(body: AiProviderRequest) {
  const { data } = await client.post<ApiResponse<AiProvider>>('/api/ai/providers', body)
  return data
}

export async function updateProvider(id: number, body: AiProviderRequest) {
  const { data } = await client.put<ApiResponse<AiProvider>>(`/api/ai/providers/${id}`, body)
  return data
}

export async function deleteProvider(id: number) {
  const { data } = await client.delete<ApiResponse<void>>(`/api/ai/providers/${id}`)
  return data
}

export async function testProvider(id: number) {
  const { data } = await client.post<ApiResponse<{ status: string }>>(`/api/ai/providers/${id}/test`)
  return data
}

export async function fetchProviderModels(id: number) {
  const { data } = await client.get<ApiResponse<string[]>>(`/api/ai/providers/${id}/models`)
  return data
}

export async function getAiSettings() {
  const { data } = await client.get<ApiResponse<PlatformAiSettings>>('/api/ai/settings')
  return data
}

export async function updateAiSettings(body: Partial<PlatformAiSettings>) {
  const { data } = await client.put<ApiResponse<PlatformAiSettings>>('/api/ai/settings', body)
  return data
}
