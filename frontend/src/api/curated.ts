import { apiRequest } from './client';

export type ShouldWhere = {
  question: string;
  track: string;
  curatedValue: {
    hostId: string;
    hostName: string | null;
  };
};

export function createHost(name: string): Promise<{ id: string; name: string }> {
  return apiRequest<{ id: string; name: string }>('/api/curated/hosts', {
    method: 'POST',
    body: { name },
  });
}

export function getShouldWhere(containerId: string): Promise<ShouldWhere> {
  return apiRequest<ShouldWhere>(
    `/api/curated/asks/should-where?containerId=${encodeURIComponent(containerId)}`,
    { omitUserId: true },
  );
}
