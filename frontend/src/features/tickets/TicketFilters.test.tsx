import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import type { UserOption } from '../../api/types';
import { agentUser, customerUser } from '../../test/fixtures';
import { currentSearch, renderWithProviders } from '../../test/renderWithProviders';
import { TicketFilters } from './TicketFilters';

const AGENTS: UserOption[] = [
  { id: agentUser.id, fullName: 'Bob Agent', email: 'agent1@acme.test', role: 'AGENT' },
  {
    id: '55555555-5555-4555-8555-555555555555',
    fullName: 'Brenda Agent',
    email: 'agent2@acme.test',
    role: 'AGENT',
  },
];

describe('TicketFilters URL sync', () => {
  beforeEach(() => {
    vi.spyOn(api, 'get').mockImplementation((url: string) => {
      if (url === '/agents') return Promise.resolve({ data: AGENTS });
      return Promise.reject(new Error(`unexpected GET ${url}`));
    });
  });

  it('initialises the controls from the URL', async () => {
    renderWithProviders(<TicketFilters showAssignee />, {
      route: '/tickets?q=printer&status=OPEN&status=IN_PROGRESS&priority=HIGH&assignee=unassigned',
      user: agentUser,
    });

    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('printer');
    expect(screen.getByRole('combobox', { name: 'Status' })).toHaveTextContent('Open, In progress');
    expect(screen.getByRole('combobox', { name: 'Priority' })).toHaveTextContent('High');
    expect(screen.getByRole('combobox', { name: 'Assignee' })).toHaveTextContent('Unassigned');
  });

  it('writes status selections to the URL and resets the page', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TicketFilters showAssignee />, {
      route: '/tickets?page=3',
      user: agentUser,
    });

    await user.click(screen.getByRole('combobox', { name: 'Status' }));
    const listbox = await screen.findByRole('listbox');
    await user.click(within(listbox).getByRole('option', { name: /^Open$/ }));
    await user.click(within(listbox).getByRole('option', { name: /Resolved/ }));

    expect(currentSearch().getAll('status')).toEqual(['OPEN', 'RESOLVED']);
    expect(currentSearch().get('page')).toBeNull();
  });

  it('debounces the search box into ?q=', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TicketFilters showAssignee />, { route: '/tickets', user: agentUser });

    await user.type(screen.getByRole('textbox', { name: 'Search' }), 'vpn');
    expect(currentSearch().get('q')).toBeNull(); // not yet committed
    await waitFor(() => expect(currentSearch().get('q')).toBe('vpn'));
  });

  it('supports assignee = me / a specific agent', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TicketFilters showAssignee />, { route: '/tickets', user: agentUser });

    await user.click(screen.getByRole('combobox', { name: 'Assignee' }));
    await user.click(await screen.findByRole('option', { name: 'Assigned to me' }));
    expect(currentSearch().get('assignee')).toBe('me');

    await user.click(screen.getByRole('combobox', { name: 'Assignee' }));
    await user.click(await screen.findByRole('option', { name: 'Brenda Agent' }));
    expect(currentSearch().get('assignee')).toBe('55555555-5555-4555-8555-555555555555');
  });

  it('Reset clears filters but keeps page size', async () => {
    const user = userEvent.setup();
    renderWithProviders(<TicketFilters showAssignee />, {
      route: '/tickets?q=x&status=OPEN&priority=LOW&assignee=me&page=2&size=50',
      user: agentUser,
    });

    await user.click(screen.getByRole('button', { name: /reset/i }));

    const params = currentSearch();
    expect(params.toString()).toBe('size=50');
    expect(screen.getByRole('textbox', { name: 'Search' })).toHaveValue('');
  });

  it('hides the assignee filter for customers', () => {
    renderWithProviders(<TicketFilters showAssignee={false} />, {
      route: '/tickets',
      user: customerUser,
    });
    expect(screen.queryByRole('combobox', { name: 'Assignee' })).not.toBeInTheDocument();
    expect(api.get).not.toHaveBeenCalled();
  });
});
