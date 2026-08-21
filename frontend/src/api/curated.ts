import { apiRequest } from './client';

export type ShouldWhere = {
  question: string;
  track: string;
  curatedValue: {
    hostId: string;
    hostName: string | null;
  };
};

export function getShouldWhere(containerId: string): Promise<ShouldWhere> {
  return apiRequest<ShouldWhere>(
    `/api/curated/asks/should-where?containerId=${encodeURIComponent(containerId)}`,
  );
}
