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

export function formatUnboundDraftItemKind(kind: string): string {
  if (kind === 'CREATE_CONTAINER_FROM_UNBOUND') {
    return '新建策展 Docker 容器';
  }
  if (kind === 'BIND_UNBOUND_TO_EXISTING') {
    return '绑到已有策展对象';
  }
  if (kind === 'CURATED_RUNS_ON_INSERT') {
    return '策展运行于（插入）';
  }
  return kind;
}

export function payloadString(
  payload: Record<string, unknown> | undefined,
  key: string,
): string | null {
  if (!payload) {
    return null;
  }
  const value = payload[key];
  return typeof value === 'string' && value.length > 0 ? value : null;
}

export function isAcceptedHandler(
  collaboration: { handlerUserId: string | null; handlerAcceptance: string } | null | undefined,
  userId: string | null,
): boolean {
  return (
    !!collaboration &&
    !!userId &&
    collaboration.handlerAcceptance === 'ACCEPTED' &&
    collaboration.handlerUserId === userId
  );
}
