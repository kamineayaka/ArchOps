import { apiRequest } from './client';
import type { CuratedDraft } from './types';

export function getOpenDraft(conflictId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/curated-drafts/open`,
  );
}

export function acceptDraftItem(draftId: string, itemId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/curated-drafts/${encodeURIComponent(draftId)}/items/${encodeURIComponent(itemId)}/accept`,
    { method: 'POST' },
  );
}

export function rejectDraftItem(draftId: string, itemId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/curated-drafts/${encodeURIComponent(draftId)}/items/${encodeURIComponent(itemId)}/reject`,
    { method: 'POST' },
  );
}
