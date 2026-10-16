import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, type RenderOptions } from '@testing-library/react';
import type { ReactElement, ReactNode } from 'react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router-dom';
import type { CurrentUser } from '../api/types';
import { AuthContext, type AuthContextValue } from '../auth/AuthContext';
import { SnackbarProvider } from '../components/SnackbarProvider';
import { ROUTER_FUTURE } from '../routerFuture';
import { theme } from '../theme';

/** Renders the current location so tests can assert on URL state. */
export function LocationDisplay() {
  const location = useLocation();
  return <div data-testid="location">{location.pathname + location.search}</div>;
}

export function currentSearch(): URLSearchParams {
  const text = document.querySelector('[data-testid="location"]')?.textContent ?? '';
  const index = text.indexOf('?');
  return new URLSearchParams(index >= 0 ? text.slice(index + 1) : '');
}

export function fakeAuth(user: CurrentUser | null): AuthContextValue {
  return {
    user,
    isAuthenticated: Boolean(user),
    login: () => Promise.reject(new Error('not implemented in tests')),
    register: () => Promise.reject(new Error('not implemented in tests')),
    logout: () => undefined,
    hasRole: (...roles) => Boolean(user && roles.includes(user.role)),
    updateUser: () => undefined,
  };
}

interface Options extends Omit<RenderOptions, 'wrapper'> {
  route?: string;
  /** Route pattern the UI is mounted at (for useParams). */
  path?: string;
  user?: CurrentUser | null;
  queryClient?: QueryClient;
}

export function createTestQueryClient() {
  return new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  });
}

export function renderWithProviders(
  ui: ReactElement,
  {
    route = '/',
    path = '*',
    user = null,
    queryClient = createTestQueryClient(),
    ...options
  }: Options = {},
) {
  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <QueryClientProvider client={queryClient}>
          <SnackbarProvider>
            <MemoryRouter initialEntries={[route]} future={ROUTER_FUTURE}>
              <AuthContext.Provider value={fakeAuth(user)}>
                <Routes>
                  <Route
                    path={path}
                    element={
                      <>
                        {children}
                        <LocationDisplay />
                      </>
                    }
                  />
                </Routes>
              </AuthContext.Provider>
            </MemoryRouter>
          </SnackbarProvider>
        </QueryClientProvider>
      </ThemeProvider>
    );
  }
  return { queryClient, ...render(ui, { wrapper: Wrapper, ...options }) };
}
