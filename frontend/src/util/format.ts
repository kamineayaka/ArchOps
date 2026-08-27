import type { ObservedValue, TrackValue } from '../api/types';

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
  if (track.availability === 'IDENTITY_LOST') {
    return '身份失联';
  }
  if (track.availability === 'PRESENT') {
    return track.hostName
      ? `${track.hostName} (${track.hostId ?? ''})`
      : (track.hostId ?? 'PRESENT');
  }
  return track.availability;
}

/** 规范问法「实际在哪」：失联不得把旧宿主写成可用实际，也不得展示为 PRESENT。 */
export function formatActualWhereValue(
  observed: ObservedValue | null | undefined,
  identityLost: boolean,
): string {
  if (!observed) {
    return identityLost ? '身份失联' : '—';
  }
  if (identityLost || observed.availability === 'IDENTITY_LOST') {
    return '身份失联';
  }
  return formatTrack(observed);
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
