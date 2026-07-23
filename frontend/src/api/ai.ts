import client from './client'
import type { ApiResponse } from './types'

export interface Conversation {
  id: number
  title: string
  targetAssetIds: number[]
  targetGroupIds: number[]
  resolvedAssetIds: number[]
  createdAt: string
  updatedAt: string
}

export interface ChatMessage {
  role: string
  content: string
  createdAt: string
}

export async function listConversations() {
  const { data } = await client.get<ApiResponse<Conversation[]>>('/api/ai/conversations')
  return data
}

export async function createConversation() {
  const { data } = await client.post<ApiResponse<Conversation>>('/api/ai/conversations')
  return data
}

export async function getMessages(conversationId: number) {
  const { data } = await client.get<ApiResponse<ChatMessage[]>>(`/api/ai/conversations/${conversationId}/messages`)
  return data
}

export async function sendChat(message: string, conversationId?: number, providerId?: number) {
  const { data } = await client.post<ApiResponse<{ conversationId: number; answer: string }>>('/api/ai/chat', {
    message,
    conversationId,
    providerId,
  })
  return data
}

export async function getConversationTargets(conversationId: number) {
  const { data } = await client.get<ApiResponse<Conversation>>(`/api/ai/conversations/${conversationId}/targets`)
  return data
}

export async function updateConversationTargets(
  conversationId: number,
  targetAssetIds: number[],
  targetGroupIds: number[] = [],
) {
  const { data } = await client.put<ApiResponse<Conversation>>(
    `/api/ai/conversations/${conversationId}/targets`,
    { targetAssetIds, targetGroupIds },
  )
  return data
}
