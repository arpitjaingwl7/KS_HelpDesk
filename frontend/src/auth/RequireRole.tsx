import type { ReactNode } from 'react';
import type { Role } from '../api/types';
import { NotAuthorizedPage } from '../components/NotAuthorizedPage';
import { useAuth } from './useAuth';

export function RequireRole({ roles, children }: { roles: Role[]; children: ReactNode }) {
  const { hasRole } = useAuth();
  if (!hasRole(...roles)) return <NotAuthorizedPage />;
  return <>{children}</>;
}
