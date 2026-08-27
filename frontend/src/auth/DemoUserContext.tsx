import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import { message } from 'antd';
import { fetchCurrentUser } from '../api/auth';
import { setApiUserId } from '../api/client';
import type { CurrentUser } from '../api/types';
import { ApiError } from '../api/types';

export const DEMO_USERS = [
  { id: 'user-senior-demo', label: '演示主管（高级）' },
  { id: 'user-general-demo', label: '演示运维（一般）' },
] as const;

/** Select value for no X-ArchOps-User-Id header (not a real user id). */
export const UNAUTHENTICATED_VALUE = 'unauthenticated';

const STORAGE_KEY = 'archops.demoUserId';
const STORAGE_UNAUTHENTICATED = 'unauthenticated';

type DemoUserContextValue = {
  userId: string | null;
  user: CurrentUser | null;
  loading: boolean;
  setUserId: (id: string | null) => void;
};

const DemoUserContext = createContext<DemoUserContextValue | null>(null);

function readStoredUserId(): string | null {
  try {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored === STORAGE_UNAUTHENTICATED || stored === UNAUTHENTICATED_VALUE) {
      return null;
    }
    if (stored && DEMO_USERS.some((u) => u.id === stored)) {
      return stored;
    }
  } catch {
    /* ignore */
  }
  return DEMO_USERS[0].id;
}

export function DemoUserProvider({ children }: { children: ReactNode }) {
  const [userId, setUserIdState] = useState<string | null>(() => {
    const id = readStoredUserId();
    setApiUserId(id);
    return id;
  });
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  const setUserId = useCallback((id: string | null) => {
    setApiUserId(id);
    setUserIdState(id);
    try {
      localStorage.setItem(STORAGE_KEY, id ?? STORAGE_UNAUTHENTICATED);
    } catch {
      /* ignore */
    }
  }, []);

  useEffect(() => {
    setApiUserId(userId);
    let cancelled = false;
    if (!userId) {
      setUser(null);
      setLoading(false);
      return () => {
        cancelled = true;
      };
    }
    setLoading(true);
    (async () => {
      try {
        const me = await fetchCurrentUser(userId);
        if (!cancelled) {
          setUser(me);
        }
      } catch (err) {
        if (!cancelled) {
          setUser(null);
          const msg = err instanceof ApiError ? err.message : String(err);
          message.error(`身份加载失败：${msg}`);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    })();
    return () => {
      cancelled = true;
    };
  }, [userId]);

  const value = useMemo(
    () => ({ userId, user, loading, setUserId }),
    [userId, user, loading, setUserId],
  );

  return <DemoUserContext.Provider value={value}>{children}</DemoUserContext.Provider>;
}

export function useDemoUser(): DemoUserContextValue {
  const ctx = useContext(DemoUserContext);
  if (!ctx) {
    throw new Error('useDemoUser must be used within DemoUserProvider');
  }
  return ctx;
}
