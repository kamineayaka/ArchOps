import { apiRequest } from './client';
import type { CurrentUser } from './types';

export function fetchCurrentUser(userId: string): Promise<CurrentUser> {
  return apiRequest<CurrentUser>('/api/auth/me', { userId });
}
