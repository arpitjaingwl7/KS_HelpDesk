import { api } from '../api/client';
import type {
  AuthResponse,
  CurrentUser,
  LoginRequest,
  RegisterOrganizationRequest,
} from '../api/types';

export async function loginRequest(body: LoginRequest): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', body);
  return data;
}

export async function registerOrganizationRequest(
  body: RegisterOrganizationRequest,
): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register-organization', body);
  return data;
}

export async function fetchMe(): Promise<CurrentUser> {
  const { data } = await api.get<CurrentUser>('/auth/me');
  return data;
}
