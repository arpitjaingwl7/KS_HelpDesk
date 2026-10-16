import { useQuery, useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from 'react';
import { useLocation, useNavigate } from 'react-router-dom';
import { setUnauthorizedHandler } from '../api/client';
import { queryKeys } from '../api/queryKeys';
import type { AuthResponse, CurrentUser, Role } from '../api/types';
import { toastBus } from '../components/toastBus';
import { fetchMe, loginRequest, registerOrganizationRequest } from './api';
import { AuthContext, type AuthContextValue } from './AuthContext';
import { tokenStorage } from './tokenStorage';

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const location = useLocation();
  const [token, setToken] = useState<string | null>(() => tokenStorage.getToken());
  const [user, setUser] = useState<CurrentUser | null>(() => tokenStorage.getUser());

  // Keep the latest location in a ref so the 401 handler builds the right `next`.
  const locationRef = useRef(location);
  locationRef.current = location;

  const clearSession = useCallback(() => {
    tokenStorage.clear();
    setToken(null);
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  // Refresh the user profile (role/name may have changed since the token was issued).
  const meQuery = useQuery({
    queryKey: queryKeys.me,
    queryFn: fetchMe,
    enabled: Boolean(token),
    staleTime: 5 * 60_000,
  });

  useEffect(() => {
    if (meQuery.data) {
      tokenStorage.setUser(meQuery.data);
      setUser(meQuery.data);
    }
  }, [meQuery.data]);

  useEffect(() => {
    setUnauthorizedHandler(() => {
      clearSession();
      const { pathname, search } = locationRef.current;
      const next = pathname.startsWith('/login')
        ? ''
        : `?next=${encodeURIComponent(pathname + search)}`;
      toastBus.emit('Session expired. Please sign in again.', 'warning');
      navigate(`/login${next}`, { replace: true });
    });
    return () => setUnauthorizedHandler(null);
  }, [clearSession, navigate]);

  const startSession = useCallback(
    (response: AuthResponse) => {
      queryClient.clear();
      tokenStorage.set(response.accessToken, response.user);
      setToken(response.accessToken);
      setUser(response.user);
      queryClient.setQueryData(queryKeys.me, response.user);
      return response;
    },
    [queryClient],
  );

  const login = useCallback<AuthContextValue['login']>(
    async (body) => startSession(await loginRequest(body)),
    [startSession],
  );

  const register = useCallback<AuthContextValue['register']>(
    async (body) => startSession(await registerOrganizationRequest(body)),
    [startSession],
  );

  const logout = useCallback(() => {
    clearSession();
    navigate('/login', { replace: true });
  }, [clearSession, navigate]);

  const hasRole = useCallback(
    (...roles: Role[]) => Boolean(user && roles.includes(user.role)),
    [user],
  );

  const updateUser = useCallback((patch: Partial<CurrentUser>) => {
    setUser((prev) => {
      if (!prev) return prev;
      const next = { ...prev, ...patch };
      tokenStorage.setUser(next);
      return next;
    });
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user: token ? user : null,
      isAuthenticated: Boolean(token && user),
      login,
      register,
      logout,
      hasRole,
      updateUser,
    }),
    [token, user, login, register, logout, hasRole, updateUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
