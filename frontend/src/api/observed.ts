import { apiRequest } from './client';
import type { ActualWhere, IdentityLostMark, UnboundCandidate } from './types';

export function listUnboundCandidates(): Promise<UnboundCandidate[]> {
  return apiRequest<UnboundCandidate[]>('/api/observed/unbound-candidates');
}

export function getIdentityLost(curatedObjectId: string): Promise<IdentityLostMark> {
  return apiRequest<IdentityLostMark>(
    `/api/observed/identity-lost/${encodeURIComponent(curatedObjectId)}`,
  );
}

export function getActualWhere(containerId: string): Promise<ActualWhere> {
  return apiRequest<ActualWhere>(
    `/api/observed/asks/actual-where?containerId=${encodeURIComponent(containerId)}`,
  );
}
