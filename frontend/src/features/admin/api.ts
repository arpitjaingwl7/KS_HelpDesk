import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '../../api/client';
import { queryKeys } from '../../api/queryKeys';
import type {
  CreateUserRequest,
  Organization,
  Page,
  UpdateOrganizationRequest,
  UpdateUserRequest,
  UserDetail,
  UserFilters,
  UserSummary,
} from '../../api/types';

export async function fetchUsers(filters: UserFilters): Promise<Page<UserSummary>> {
  const { data } = await api.get<Page<UserSummary>>('/users', { params: filters });
  return data;
}

export async function fetchUser(id: string): Promise<UserDetail> {
  const { data } = await api.get<UserDetail>(`/users/${encodeURIComponent(id)}`);
  return data;
}

export function useUsers(filters: UserFilters) {
  return useQuery({
    queryKey: queryKeys.users(filters),
    queryFn: () => fetchUsers(filters),
    placeholderData: keepPreviousData,
  });
}

export function useUser(id: string | null) {
  return useQuery({
    queryKey: queryKeys.user(id ?? ''),
    queryFn: () => fetchUser(id as string),
    enabled: Boolean(id),
    staleTime: 0,
  });
}

function useInvalidateUsers() {
  const queryClient = useQueryClient();
  return (user: UserDetail) => {
    queryClient.setQueryData(queryKeys.user(user.id), user);
    void queryClient.invalidateQueries({ queryKey: queryKeys.usersAll });
    // Role/active changes affect the agent & customer pickers.
    void queryClient.invalidateQueries({ queryKey: ['agents'] });
    void queryClient.invalidateQueries({ queryKey: ['customers'] });
  };
}

export function useCreateUser() {
  const onChanged = useInvalidateUsers();
  return useMutation({
    mutationFn: async (body: CreateUserRequest) => {
      const { data } = await api.post<UserDetail>('/users', body);
      return data;
    },
    onSuccess: onChanged,
  });
}

export function useUpdateUser() {
  const onChanged = useInvalidateUsers();
  return useMutation({
    mutationFn: async ({ id, body }: { id: string; body: UpdateUserRequest }) => {
      const { data } = await api.patch<UserDetail>(`/users/${encodeURIComponent(id)}`, body);
      return data;
    },
    onSuccess: onChanged,
  });
}

/** Toggle active from the table: fetch the current version first, then PATCH. */
export function useSetUserActive() {
  const onChanged = useInvalidateUsers();
  return useMutation({
    mutationFn: async ({ id, active }: { id: string; active: boolean }) => {
      const current = await fetchUser(id);
      const { data } = await api.patch<UserDetail>(`/users/${encodeURIComponent(id)}`, {
        active,
        version: current.version,
      } satisfies UpdateUserRequest);
      return data;
    },
    onSuccess: onChanged,
  });
}

export function useOrganization() {
  return useQuery({
    queryKey: queryKeys.organization,
    queryFn: async () => {
      const { data } = await api.get<Organization>('/organization');
      return data;
    },
  });
}

export function useUpdateOrganization() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateOrganizationRequest) => {
      const { data } = await api.patch<Organization>('/organization', body);
      return data;
    },
    onSuccess: (org) => {
      queryClient.setQueryData(queryKeys.organization, org);
      void queryClient.invalidateQueries({ queryKey: queryKeys.me });
    },
  });
}
