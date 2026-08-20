import { apiRequest } from './client';
import type { ShouldWhere } from './types';

export function shouldWhere(containerId: string): Promise<ShouldWhere> {
  const params = new URLSearchParams({ containerId });
  return apiRequest<ShouldWhere>(`/api/curated/asks/should-where?${params.toString()}`);
}
