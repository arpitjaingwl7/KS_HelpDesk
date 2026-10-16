import { lazy } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { LoginPage } from './auth/LoginPage';
import { RegisterPage } from './auth/RegisterPage';
import { RequireAuth } from './auth/RequireAuth';
import { RequireRole } from './auth/RequireRole';
import { AppLayout } from './components/AppLayout';
import { NotFoundPage } from './components/NotFoundPage';
import { TicketListPage } from './features/tickets/TicketListPage';

// Less frequently visited screens are code-split.
const TicketDetailPage = lazy(() =>
  import('./features/tickets/TicketDetailPage').then((m) => ({ default: m.TicketDetailPage })),
);
const CreateTicketPage = lazy(() =>
  import('./features/tickets/CreateTicketPage').then((m) => ({ default: m.CreateTicketPage })),
);
const UsersPage = lazy(() =>
  import('./features/admin/UsersPage').then((m) => ({ default: m.UsersPage })),
);
const OrganizationPage = lazy(() =>
  import('./features/admin/OrganizationPage').then((m) => ({ default: m.OrganizationPage })),
);

/** Route table (docs/06 §2). */
export function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      <Route
        element={
          <RequireAuth>
            <AppLayout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/tickets" replace />} />
        <Route path="/tickets" element={<TicketListPage />} />
        <Route path="/tickets/new" element={<CreateTicketPage />} />
        <Route path="/tickets/:ticketId" element={<TicketDetailPage />} />
        <Route
          path="/admin/users"
          element={
            <RequireRole roles={['ORG_ADMIN']}>
              <UsersPage />
            </RequireRole>
          }
        />
        <Route
          path="/admin/organization"
          element={
            <RequireRole roles={['ORG_ADMIN']}>
              <OrganizationPage />
            </RequireRole>
          }
        />
        <Route path="/admin" element={<Navigate to="/admin/users" replace />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
