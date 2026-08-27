import type { TrackValue } from '../api/types';

export function formatUnboundLabels(labels: Record<string, string> | null | undefined): string {
  if (!labels || Object.keys(labels).length === 0) {
    return '（无标签）';
  }
  return Object.entries(labels)
    .map(([key, value]) => `${key}=${value}`)
    .join(', ');
}

export function formatTrack(track: TrackValue | null | undefined): string {
  if (!track) {
    return '—';
  }
  if (track.availability === 'HOLLOW') {
    return '空洞';
  }
  if (track.availability === 'ABSENT') {
    return '不存在';
  }
  if (track.availability === 'PRESENT') {
    return track.hostName
      ? `${track.hostName} (${track.hostId ?? ''})`
      : (track.hostId ?? 'PRESENT');
  }
  return track.availability;
}

export function isAcceptedHandler(
  collaboration: { handlerUserId: string | null; handlerAcceptance: string } | null | undefined,
  userId: string,
): boolean {
  return (
    !!collaboration &&
    collaboration.handlerAcceptance === 'ACCEPTED' &&
    collaboration.handlerUserId === userId
  );
}
