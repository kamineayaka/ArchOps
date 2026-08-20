import { apiRequest } from './client';
import type { CuratedDraft } from './types';

export function getOpenDraft(conflictId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/curated-drafts/open`,
  );
}
