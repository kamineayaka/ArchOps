import { apiRequest } from './client';
import type { UnboundCandidate } from './types';

export function listUnboundCandidates(): Promise<UnboundCandidate[]> {
  return apiRequest<UnboundCandidate[]>('/api/observed/unbound-candidates');
}
