import { apiRequest } from './client';
import type { ConflictCase, ConflictDiagnosis } from './types';

export function listActiveConflicts(): Promise<ConflictCase[]> {
  return apiRequest<ConflictCase[]>('/api/conflicts');
}

export function getConflict(id: string): Promise<ConflictCase> {
  return apiRequest<ConflictCase>(`/api/conflicts/${encodeURIComponent(id)}`);
}

export function getDiagnosis(conflictId: string): Promise<ConflictDiagnosis> {
  return apiRequest<ConflictDiagnosis>(
    `/api/conflicts/${encodeURIComponent(conflictId)}/diagnosis`,
  );
}

export function claimConflict(id: string): Promise<ConflictCase> {
  return apiRequest<ConflictCase>(`/api/conflicts/${encodeURIComponent(id)}/claim`, {
    method: 'POST',
  });
}

export function acknowledgeConflict(id: string): Promise<ConflictCase> {
  return apiRequest<ConflictCase>(`/api/conflicts/${encodeURIComponent(id)}/acknowledge`, {
    method: 'POST',
  });
}

export function acknowledgeAndSelfAppoint(id: string): Promise<ConflictCase> {
  return apiRequest<ConflictCase>(
    `/api/conflicts/${encodeURIComponent(id)}/acknowledge-and-self-appoint`,
    { method: 'POST' },
  );
}

export function confirmCloseConflict(id: string): Promise<ConflictCase> {
  return apiRequest<ConflictCase>(`/api/conflicts/${encodeURIComponent(id)}/confirm-close`, {
    method: 'POST',
  });
}
