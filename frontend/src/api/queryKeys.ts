import type { TicketQuery, UserFilters } from './types';

/** Central query-key factory (see docs/06 §5). */
export const queryKeys = {
  me: ['me'] as const,
  organization: ['organization'] as const,
  ticketsAll: ['tickets'] as const,
  tickets: (filters: TicketQuery) => ['tickets', filters] as const,
  ticketStats: ['tickets', 'stats'] as const,
  ticket: (id: string) => ['ticket', id] as const,
  comments: (id: string) => ['comments', id] as const,
  history: (id: string) => ['history', id] as const,
  agents: (q = '') => ['agents', q] as const,
  customers: (q = '') => ['customers', q] as const,
  usersAll: ['users'] as const,
  users: (filters: UserFilters) => ['users', filters] as const,
  user: (id: string) => ['user', id] as const,
};
