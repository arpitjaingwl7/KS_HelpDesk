import {
  keepPreviousData,
  useMutation,
  useQuery,
  useQueryClient,
  type QueryClient,
} from '@tanstack/react-query';
import { api } from '../../api/client';
import { queryKeys } from '../../api/queryKeys';
import type {
  ChangeStatusRequest,
  CreateTicketRequest,
  Page,
  TicketDetail,
  TicketQuery,
  TicketStats,
  TicketSummary,
  UpdateTicketRequest,
  UserOption,
} from '../../api/types';

// ---------- raw requests ----------

export async function fetchTickets(query: TicketQuery): Promise<Page<TicketSummary>> {
  const { data } = await api.get<Page<TicketSummary>>('/tickets', { params: query });
  return data;
}

export async function fetchTicket(id: string): Promise<TicketDetail> {
  const { data } = await api.get<TicketDetail>(`/tickets/${encodeURIComponent(id)}`);
  return data;
}

export async function fetchTicketStats(): Promise<TicketStats> {
  const { data } = await api.get<TicketStats>('/tickets/stats');
  return data;
}

export async function fetchAgents(q = ''): Promise<UserOption[]> {
  const { data } = await api.get<UserOption[]>('/agents', { params: { q } });
  return data;
}

export async function fetchCustomers(q = ''): Promise<UserOption[]> {
  const { data } = await api.get<UserOption[]>('/customers', { params: { q } });
  return data;
}

// ---------- queries ----------

export function useTickets(query: TicketQuery) {
  return useQuery({
    queryKey: queryKeys.tickets(query),
    queryFn: () => fetchTickets(query),
    placeholderData: keepPreviousData,
  });
}

export function useTicketStats(enabled: boolean) {
  return useQuery({
    queryKey: queryKeys.ticketStats,
    queryFn: fetchTicketStats,
    enabled,
    staleTime: 30_000,
  });
}

export function useTicket(id: string) {
  return useQuery({
    queryKey: queryKeys.ticket(id),
    queryFn: () => fetchTicket(id),
  });
}

export function useAgents(enabled = true) {
  return useQuery({
    queryKey: queryKeys.agents(),
    queryFn: () => fetchAgents(),
    enabled,
    staleTime: 5 * 60_000,
  });
}

export function useCustomers(q: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.customers(q),
    queryFn: () => fetchCustomers(q),
    enabled,
    staleTime: 60_000,
    placeholderData: keepPreviousData,
  });
}

// ---------- mutations ----------

/** Puts the fresh ticket into the cache and invalidates everything derived from it. */
function onTicketChanged(
  queryClient: QueryClient,
  ticket: TicketDetail,
  { comments = false }: { comments?: boolean } = {},
) {
  queryClient.setQueryData(queryKeys.ticket(ticket.id), ticket);
  void queryClient.invalidateQueries({ queryKey: queryKeys.ticketsAll });
  void queryClient.invalidateQueries({ queryKey: queryKeys.history(ticket.id) });
  if (comments) void queryClient.invalidateQueries({ queryKey: queryKeys.comments(ticket.id) });
}

export function useCreateTicket() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: CreateTicketRequest) => {
      const { data } = await api.post<TicketDetail>('/tickets', body);
      return data;
    },
    onSuccess: (ticket) => {
      queryClient.setQueryData(queryKeys.ticket(ticket.id), ticket);
      void queryClient.invalidateQueries({ queryKey: queryKeys.ticketsAll });
    },
  });
}

export function useUpdateTicket(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: UpdateTicketRequest) => {
      const { data } = await api.patch<TicketDetail>(`/tickets/${encodeURIComponent(id)}`, body);
      return data;
    },
    onSuccess: (ticket) => onTicketChanged(queryClient, ticket),
  });
}

export function useAssignTicket(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (assigneeId: string | null) => {
      const url = `/tickets/${encodeURIComponent(id)}/assignee`;
      const { data } = assigneeId
        ? await api.put<TicketDetail>(url, { assigneeId })
        : await api.delete<TicketDetail>(url);
      return data;
    },
    onSuccess: (ticket) => onTicketChanged(queryClient, ticket),
  });
}

export function useChangeStatus(id: string) {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async (body: ChangeStatusRequest) => {
      const { data } = await api.put<TicketDetail>(
        `/tickets/${encodeURIComponent(id)}/status`,
        body,
      );
      return data;
    },
    onSuccess: (ticket, body) =>
      onTicketChanged(queryClient, ticket, { comments: Boolean(body.comment) }),
  });
}
