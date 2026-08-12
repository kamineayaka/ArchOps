import type { ApiResponse } from './types';
import { ApiError } from './types';

const USER_ID_HEADER = 'X-ArchOps-User-Id';

let currentUserId: string | null = null;

export function setApiUserId(userId: string | null): void {
  currentUserId = userId;
}

export function getApiUserId(): string | null {
  return currentUserId;
}

type RequestOptions = {
  method?: string;
  body?: unknown;
  /** Override demo user for a single call (rare). */
  userId?: string;
};

export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = {
    Accept: 'application/json',
  };
  const userId = options.userId ?? currentUserId;
  if (userId) {
    headers[USER_ID_HEADER] = userId;
  }
  if (options.body !== undefined) {
    headers['Content-Type'] = 'application/json';
  }

  const res = await fetch(path, {
    method: options.method ?? (options.body !== undefined ? 'POST' : 'GET'),
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  let envelope: ApiResponse<T> | null = null;
  try {
    envelope = (await res.json()) as ApiResponse<T>;
  } catch {
    throw new ApiError('HTTP_ERROR', `HTTP ${res.status}`, res.status);
  }

  if (!res.ok || !envelope.success) {
    throw new ApiError(
      envelope.code || 'HTTP_ERROR',
      envelope.message || `HTTP ${res.status}`,
      res.status,
    );
  }

  return envelope.data;
}
