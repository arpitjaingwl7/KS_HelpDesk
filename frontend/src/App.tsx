import { CssBaseline, ThemeProvider } from '@mui/material';
import { QueryClientProvider } from '@tanstack/react-query';
import { useState } from 'react';
import { BrowserRouter } from 'react-router-dom';
import { AuthProvider } from './auth/AuthProvider';
import { SnackbarProvider } from './components/SnackbarProvider';
import { createQueryClient } from './queryClient';
import { AppRoutes } from './router';
import { ROUTER_FUTURE } from './routerFuture';
import { theme } from './theme';

export function App() {
  const [queryClient] = useState(createQueryClient);
  return (
    <ThemeProvider theme={theme}>
      <CssBaseline />
      <QueryClientProvider client={queryClient}>
        <SnackbarProvider>
          <BrowserRouter future={ROUTER_FUTURE}>
            <AuthProvider>
              <AppRoutes />
            </AuthProvider>
          </BrowserRouter>
        </SnackbarProvider>
      </QueryClientProvider>
    </ThemeProvider>
  );
}
