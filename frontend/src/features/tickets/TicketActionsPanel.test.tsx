import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { agentUser, customerUser, makeTicket } from '../../test/fixtures';
import { renderWithProviders } from '../../test/renderWithProviders';
import { TicketActionsPanel } from './TicketActionsPanel';

describe('TicketActionsPanel (role-aware via permissions/allowedTransitions)', () => {
  it('offers exactly the allowedTransitions from the API', async () => {
    vi.spyOn(api, 'get').mockResolvedValue({ data: [] });
    const user = userEvent.setup();
    renderWithProviders(
      <TicketActionsPanel
        ticket={makeTicket({ status: 'IN_PROGRESS', allowedTransitions: ['ON_HOLD', 'RESOLVED'] })}
        onError={vi.fn()}
        onConflict={vi.fn()}
      />,
      { user: agentUser },
    );

    await user.click(screen.getByRole('combobox', { name: 'Change status' }));
    const listbox = await screen.findByRole('listbox');
    const options = within(listbox)
      .getAllByRole('option')
      .filter((o) => o.getAttribute('aria-disabled') !== 'true')
      .map((o) => o.textContent);
    expect(options).toEqual(['Move to On hold', 'Move to Resolved']);
  });

  it('shows read-only values when the caller lacks permissions', () => {
    const get = vi.spyOn(api, 'get');
    renderWithProviders(
      <TicketActionsPanel
        ticket={makeTicket({
          allowedTransitions: ['CLOSED'],
          permissions: {
            canEdit: true,
            canChangePriority: false,
            canAssign: false,
            canAddInternalNote: false,
            canComment: true,
          },
        })}
        onError={vi.fn()}
        onConflict={vi.fn()}
      />,
      { user: customerUser },
    );

    expect(screen.queryByRole('combobox', { name: 'Priority' })).not.toBeInTheDocument();
    expect(screen.queryByRole('combobox', { name: 'Assignee' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /assign to me/i })).not.toBeInTheDocument();
    expect(screen.getByText('Not yet assigned')).toBeInTheDocument();
    expect(get).not.toHaveBeenCalled(); // no /agents call for customers
  });
});
