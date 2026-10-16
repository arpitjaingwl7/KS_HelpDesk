import { createContext } from 'react';
import type {
  AuthResponse,
  CurrentUser,
  LoginRequest,
  RegisterOrganizationRequest,
  Role,
} from '../api/types';

export interface AuthContextValue {
  user: CurrentUser | null;
  isAuthenticated: boolean;
  login(body: LoginRequest): Promise<AuthResponse>;
  register(body: RegisterOrganizationRequest): Promise<AuthResponse>;
  logout(): void;
  hasRole(...roles: Role[]): boolean;
  /** Merge fresh data into the cached user (e.g. after renaming the organization). */
  updateUser(patch: Partial<CurrentUser>): void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);
