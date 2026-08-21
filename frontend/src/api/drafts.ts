import { apiRequest } from './client';
import type { CuratedDraft } from './types';

export function getOpenDraft(conflictId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/curated-drafts/open`,
  );
}

export function getDraftById(conflictId: string, draftId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/curated-drafts/${encodeURIComponent(draftId)}`,
  );
}

export function acceptDraftItem(conflictId: string, itemId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/curated-drafts/open/items/${encodeURIComponent(itemId)}/accept`,
    { method: 'POST', body: {} },
  );
}

export function rejectDraftItem(conflictId: string, itemId: string): Promise<CuratedDraft> {
  return apiRequest<CuratedDraft>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/curated-drafts/open/items/${encodeURIComponent(itemId)}/reject`,
    { method: 'POST', body: {} },
  );
}
