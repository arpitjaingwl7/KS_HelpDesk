import { useQuery } from '@tanstack/react-query';
import { api } from '../../api/client';
import { queryKeys } from '../../api/queryKeys';
import type { HistoryEntry } from '../../api/types';

export async function fetchHistory(ticketId: string): Promise<HistoryEntry[]> {
  const { data } = await api.get<HistoryEntry[]>(
    `/tickets/${encodeURIComponent(ticketId)}/history`,
  );
  return data;
}

export function useHistory(ticketId: string, enabled = true) {
  return useQuery({
    queryKey: queryKeys.history(ticketId),
    queryFn: () => fetchHistory(ticketId),
    enabled,
  });
}
