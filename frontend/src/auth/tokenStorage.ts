import type { CurrentUser } from '../api/types';

/**
 * The access token lives in memory and is mirrored to sessionStorage so a page reload
 * (same tab) keeps the session, while closing the tab ends it.
 */
const TOKEN_KEY = 'helpdesk.token';
const USER_KEY = 'helpdesk.user';

let memoryToken: string | null = null;
let memoryUser: CurrentUser | null = null;
let hydrated = false;

function safeGet(key: string): string | null {
  try {
    return sessionStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSet(key: string, value: string | null): void {
  try {
    if (value === null) sessionStorage.removeItem(key);
    else sessionStorage.setItem(key, value);
  } catch {
    /* storage unavailable (private mode) – memory copy still works */
  }
}

function hydrate(): void {
  if (hydrated) return;
  hydrated = true;
  memoryToken = safeGet(TOKEN_KEY);
  const rawUser = safeGet(USER_KEY);
  if (rawUser) {
    try {
      memoryUser = JSON.parse(rawUser) as CurrentUser;
    } catch {
      memoryUser = null;
    }
  }
}

export const tokenStorage = {
  getToken(): string | null {
    hydrate();
    return memoryToken;
  },
  getUser(): CurrentUser | null {
    hydrate();
    return memoryUser;
  },
  set(token: string, user: CurrentUser): void {
    hydrated = true;
    memoryToken = token;
    memoryUser = user;
    safeSet(TOKEN_KEY, token);
    safeSet(USER_KEY, JSON.stringify(user));
  },
  setUser(user: CurrentUser): void {
    memoryUser = user;
    safeSet(USER_KEY, JSON.stringify(user));
  },
  clear(): void {
    hydrated = true;
    memoryToken = null;
    memoryUser = null;
    safeSet(TOKEN_KEY, null);
    safeSet(USER_KEY, null);
  },
  /** Test helper: forget the in-memory copy so the next read re-hydrates from storage. */
  reset(): void {
    hydrated = false;
    memoryToken = null;
    memoryUser = null;
  },
};
