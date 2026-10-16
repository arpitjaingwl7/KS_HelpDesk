import type { Role, TicketPriority, TicketStatus } from '../api/types';

export const STATUS_LABELS: Record<TicketStatus, string> = {
  OPEN: 'Open',
  IN_PROGRESS: 'In progress',
  ON_HOLD: 'On hold',
  RESOLVED: 'Resolved',
  CLOSED: 'Closed',
};

export const PRIORITY_LABELS: Record<TicketPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
  URGENT: 'Urgent',
};

export const ROLE_LABELS: Record<Role, string> = {
  ORG_ADMIN: 'Admin',
  AGENT: 'Agent',
  CUSTOMER: 'Customer',
};

export function isStaff(role: Role | undefined | null): boolean {
  return role === 'ORG_ADMIN' || role === 'AGENT';
}

export function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  const first = parts[0]?.[0] ?? '';
  const last = parts.length > 1 ? (parts[parts.length - 1]?.[0] ?? '') : '';
  return (first + last).toUpperCase() || '?';
}

/** Accent colour per status (always paired with a text label). */
export const STATUS_COLORS: Record<TicketStatus, string> = {
  OPEN: '#2563eb',
  IN_PROGRESS: '#7c3aed',
  ON_HOLD: '#d97706',
  RESOLVED: '#059669',
  CLOSED: '#64748b',
};
