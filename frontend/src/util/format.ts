import type { ObservedValue, OperationPlan, TrackValue } from '../api/types';

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

/**
 * 规范问法「实际」投影。空洞与失联可并存（ticket 09 HTTP：`availability=HOLLOW` 且
 * `identityLost=true`）；不得互相吞掉。失联时不得把旧宿主写成 PRESENT。
 */
export function formatObservedActual(
  observed: TrackValue | ObservedValue | null | undefined,
  flags: { observationHollow?: boolean; identityLost?: boolean } = {},
): string {
  const hollow =
    Boolean(flags.observationHollow) || observed?.availability === 'HOLLOW';
  const lost =
    Boolean(flags.identityLost) || observed?.availability === 'IDENTITY_LOST';
  if (hollow && lost) {
    return '空洞（不可信）；身份失联';
  }
  if (hollow) {
    return '空洞（不可信）';
  }
  if (lost) {
    return '身份失联';
  }
  return formatTrack(observed);
}

/** 规范问法「实际在哪」：与冲突页同一套双轨说法。 */
export function formatActualWhereValue(
  observed: ObservedValue | null | undefined,
  identityLost: boolean,
): string {
  if (!observed) {
    return identityLost ? '身份失联' : '—';
  }
  return formatObservedActual(observed, { identityLost });
}

export function formatExpected(expected: Record<string, string> | null | undefined): string | null {
  if (!expected) {
    return null;
  }
  const entries = Object.entries(expected);
  if (entries.length === 0) {
    return null;
  }
  return entries.map(([key, value]) => `${key}=${value}`).join(', ');
}

export function formatStructuredOutput(output: string | null | undefined): string | null {
  if (output == null) {
    return null;
  }
  const trimmed = output.trim();
  if (trimmed.length === 0) {
    return null;
  }
  try {
    return JSON.stringify(JSON.parse(trimmed), null, 2);
  } catch {
    return trimmed;
  }
}

export function isActiveOperationPlan(plan: OperationPlan | null | undefined): boolean {
  return (
    !!plan &&
    (plan.status === 'DRAFT_REVIEW' ||
      plan.status === 'APPROVED' ||
      plan.status === 'EXECUTING')
  );
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
