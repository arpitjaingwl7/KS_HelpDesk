import type { CurrentUser, TicketDetail } from '../api/types';

const org = { id: '00000000-0000-0000-0000-0000000000a1', name: 'Acme Corp', slug: 'acme' };

export const agentUser: CurrentUser = {
  id: '11111111-1111-4111-8111-111111111111',
  fullName: 'Bob Agent',
  email: 'agent1@acme.test',
  role: 'AGENT',
  organization: org,
};

export const customerUser: CurrentUser = {
  id: '22222222-2222-4222-8222-222222222222',
  fullName: 'Carol Customer',
  email: 'customer1@acme.test',
  role: 'CUSTOMER',
  organization: org,
};

export const adminUser: CurrentUser = {
  id: '33333333-3333-4333-8333-333333333333',
  fullName: 'Alice Admin',
  email: 'admin@acme.test',
  role: 'ORG_ADMIN',
  organization: org,
};

export function makeTicket(overrides: Partial<TicketDetail> = {}): TicketDetail {
  return {
    id: '44444444-4444-4444-8444-444444444444',
    number: 42,
    subject: 'Cannot reset password',
    description: 'The reset email never arrives.',
    status: 'OPEN',
    priority: 'HIGH',
    requester: { id: customerUser.id, fullName: customerUser.fullName, email: customerUser.email },
    assignee: null,
    createdAt: '2026-10-15T09:30:00Z',
    updatedAt: '2026-10-15T09:30:00Z',
    resolvedAt: null,
    closedAt: null,
    version: 0,
    allowedTransitions: ['IN_PROGRESS', 'ON_HOLD', 'RESOLVED', 'CLOSED'],
    permissions: {
      canEdit: true,
      canChangePriority: true,
      canAssign: true,
      canAddInternalNote: true,
      canComment: true,
    },
    ...overrides,
  };
}
