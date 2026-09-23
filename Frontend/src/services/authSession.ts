import type { LoginRequest, User } from '../types';

interface AuthApi {
  currentUser: () => Promise<User>;
  login: (data: LoginRequest) => Promise<unknown>;
  logout: () => Promise<unknown>;
}

export interface AuthSnapshot {
  user: User | null;
  isLoading: boolean;
  error: string | null;
}

export function authErrorStatus(error: unknown): number | undefined {
  return (error as { response?: { status?: number } } | null)?.response?.status;
}

export function authErrorMessage(error: unknown, fallback: string): string {
  const message = (error as { response?: { data?: { message?: unknown } } } | null)?.response?.data?.message;
  return typeof message === 'string' && message.length > 0 ? message : fallback;
}

export function verifiedUser(value: unknown): User {
  if (!value || typeof value !== 'object') throw new Error('Invalid current-user response');
  const candidate = value as Record<string, unknown>;
  if (typeof candidate.username !== 'string' || !candidate.username.trim()
      || typeof candidate.email !== 'string'
      || (candidate.nickname !== undefined && typeof candidate.nickname !== 'string')) {
    throw new Error('Invalid current-user response');
  }
  // Keep only the public identity fields; never persist credentials or arbitrary response fields.
  return { username: candidate.username, email: candidate.email,
    ...(typeof candidate.nickname === 'string' ? { nickname: candidate.nickname } : {}) };
}

export function createAuthSession(api: AuthApi, storage: Pick<Storage, 'removeItem'>) {
  let snapshot: AuthSnapshot = { user: null, isLoading: true, error: null };
  let revision: number = 0;
  let pendingRestore: { revision: number; promise: Promise<User | null> } | null = null;
  const listeners = new Set<() => void>();

  const remove = (key: string): void => {
    try { storage.removeItem(key); } catch { /* Disabled storage must not prevent cookie authentication. */ }
  };
  const clearRoom = (): void => { remove('bobgourmet_current_room'); };
  const publish = (state: AuthSnapshot): void => {
    snapshot = state;
    listeners.forEach(listener => listener());
  };
  remove('user'); // Discard the old unverified localStorage identity, never read it.

  const restore = (): Promise<User | null> => {
    if (pendingRestore?.revision === revision) return pendingRestore.promise;
    const operation: number = ++revision;
    publish({ user: null, isLoading: true, error: null });
    const promise: Promise<User | null> = (async () => {
      try {
        const user: User = verifiedUser(await api.currentUser());
        if (operation !== revision) return null;
        publish({ user, isLoading: false, error: null });
        return user;
      } catch (error: unknown) {
        if (operation !== revision) return null;
        if (authErrorStatus(error) === 401) clearRoom();
        publish({ user: null, isLoading: false, error: authErrorStatus(error) === 401
          ? null : '서버에서 로그인 상태를 확인하지 못했습니다. 연결 상태를 확인하고 다시 시도해주세요.' });
        return null;
      }
    })();
    pendingRestore = { revision: operation, promise };
    void promise.then(() => { if (pendingRestore?.promise === promise) pendingRestore = null; });
    return promise;
  };

  const login = async (data: LoginRequest): Promise<User | null> => {
    const operation: number = ++revision;
    clearRoom();
    publish({ user: null, isLoading: false, error: null });
    try {
      await api.login(data);
      if (operation !== revision) return null;
      const user: User = verifiedUser(await api.currentUser());
      if (operation !== revision) return null;
      publish({ user, isLoading: false, error: null });
      return user;
    } catch (error: unknown) {
      if (operation !== revision) return null;
      publish({ user: null, isLoading: false, error: null });
      throw error;
    }
  };

  const logout = async (): Promise<boolean> => {
    const operation: number = ++revision;
    try {
      await api.logout();
    } catch (error: unknown) {
      if (operation !== revision) return false;
      if (authErrorStatus(error) !== 401) {
        publish({ ...snapshot, isLoading: false });
        throw error;
      }
    }
    if (operation !== revision) return false;
    remove('user');
    clearRoom();
    publish({ user: null, isLoading: false, error: null });
    return true;
  };

  return {
    getSnapshot: (): AuthSnapshot => snapshot,
    subscribe: (listener: () => void): (() => void) => {
      listeners.add(listener);
      return () => { listeners.delete(listener); };
    },
    restore, login, logout,
  };
}
