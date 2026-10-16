import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AxiosError, AxiosHeaders, type AxiosResponse } from 'axios';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../api/client';
import type { Page, TicketSummary } from '../api/types';
import { AppRoutes } from '../router';
import { adminUser, agentUser, customerUser, makeTicket } from './fixtures';
import { renderWithProviders } from './renderWithProviders';

const ticket = makeTicket();
const summary: TicketSummary = {
  id: ticket.id,
  number: ticket.number,
  subject: ticket.subject,
  status: ticket.status,
  priority: ticket.priority,
  requester: { id: ticket.requester.id, fullName: ticket.requester.fullName },
  assignee: null,
  createdAt: ticket.createdAt,
  updatedAt: ticket.updatedAt,
};
const page = <T,>(content: T[]): Page<T> => ({
  content,
  page: { number: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0 },
});

function notFound(): AxiosError {
  const config = { headers: new AxiosHeaders() };
  const data = {
    type: 'x',
    title: 'Not found',
    status: 404,
    code: 'RESOURCE_NOT_FOUND',
    detail: 'Ticket not found',
  };
  return new AxiosError('nf', 'ERR_BAD_REQUEST', config, {}, {
    status: 404,
    data,
    config,
    headers: {},
    statusText: '',
  } as AxiosResponse);
}

function mockApi() {
  return vi.spyOn(api, 'get').mockImplementation((url: string) => {
    if (url === '/tickets') return Promise.resolve({ data: page([summary]) });
    if (url === '/tickets/stats')
      return Promise.resolve({
        data: { total: 1, unassigned: 1, byStatus: { OPEN: 1 }, byPriority: { HIGH: 1 } },
      });
    if (url === '/agents') return Promise.resolve({ data: [] });
    if (url === `/tickets/${ticket.id}`) return Promise.resolve({ data: ticket });
    if (url === `/tickets/${ticket.id}/comments`)
      return Promise.resolve({
        data: page([
          {
            id: 'c1',
            body: 'Probably SMTP relay',
            visibility: 'INTERNAL',
            author: { id: agentUser.id, fullName: agentUser.fullName, role: 'AGENT' },
            createdAt: '2026-10-15T10:00:00Z',
          },
        ]),
      });
    if (url === `/tickets/${ticket.id}/history`) return Promise.resolve({ data: [] });
    return Promise.reject(notFound());
  });
}

describe('page smoke tests', () => {
  it('ticket list renders rows and the staff summary strip', async () => {
    mockApi();
    renderWithProviders(<AppRoutes />, { route: '/tickets', user: agentUser });
    expect(await screen.findByRole('link', { name: 'Cannot reset password' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Tickets' })).toBeInTheDocument();
    expect(await screen.findByText('Unassigned', { selector: 'span' })).toBeInTheDocument();
  });

  it('customers see "My tickets" without an assignee filter', async () => {
    mockApi();
    renderWithProviders(<AppRoutes />, { route: '/tickets', user: customerUser });
    expect(await screen.findByRole('heading', { name: 'My tickets' })).toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Assignee' })).not.toBeInTheDocument();
  });

  it('ticket detail shows info, actions, and internal notes', async () => {
    mockApi();
    const user = userEvent.setup();
    renderWithProviders(<AppRoutes />, { route: `/tickets/${ticket.id}`, user: agentUser });
    expect(
      await screen.findByRole('heading', { name: /#42\s*Cannot reset password/ }),
    ).toBeInTheDocument();
    const note = await screen.findByRole('article', { name: /internal note by bob agent/i });
    expect(within(note).getByText('Probably SMTP relay')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /assign to me/i })).toBeInTheDocument();

    await user.click(screen.getByRole('tab', { name: 'History' }));
    expect(await screen.findByText('No history yet')).toBeInTheDocument();
  });

  it('unknown ticket shows an in-page "Ticket not found"', async () => {
    mockApi();
    renderWithProviders(<AppRoutes />, {
      route: '/tickets/00000000-0000-4000-8000-000000000000',
      user: agentUser,
    });
    expect(await screen.findByRole('heading', { name: 'Ticket not found' })).toBeInTheDocument();
  });

  it('admin pages are blocked for agents and render for admins', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({ data: page([]) });
    const { unmount } = renderWithProviders(<AppRoutes />, {
      route: '/admin/users',
      user: agentUser,
    });
    expect(await screen.findByRole('heading', { name: 'Not authorized' })).toBeInTheDocument();
    unmount();
    renderWithProviders(<AppRoutes />, { route: '/admin/users', user: adminUser });
    expect(await screen.findByRole('heading', { name: 'Users' })).toBeInTheDocument();
    expect(await screen.findByText('No users yet')).toBeInTheDocument();
  });

  it('unknown routes render the 404 page', async () => {
    renderWithProviders(<AppRoutes />, { route: '/nope', user: agentUser });
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument();
  });
});
