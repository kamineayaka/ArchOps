import { apiRequest } from './client';

export type ProbeTask = {
  id: string;
  hostId: string;
  command: string;
  intervalSeconds: number;
  curatedExpected: unknown;
  judgment: 'HOLLOW' | 'NORMAL' | 'CONFLICT' | string;
  conflictId: string | null;
  observationHollow: boolean;
  observedValue: unknown | null;
  evidence: string | null;
};

export function createProbeTask(body: {
  hostId: string;
  command: string;
  intervalSeconds: number;
  curatedExpected: unknown;
}): Promise<ProbeTask> {
  return apiRequest<ProbeTask>('/api/probe-tasks', { method: 'POST', body });
}

export function getProbeTask(id: string): Promise<ProbeTask> {
  return apiRequest<ProbeTask>(`/api/probe-tasks/${encodeURIComponent(id)}`);
}
