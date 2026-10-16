import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { api } from '../../api/client';
import { agentUser, customerUser } from '../../test/fixtures';
import { renderWithProviders } from '../../test/renderWithProviders';
import { CommentComposer } from './CommentComposer';

const TICKET_ID = '44444444-4444-4444-8444-444444444444';

describe('CommentComposer', () => {
  it('hides the Public/Internal toggle for customers and always posts PUBLIC', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({
      data: {
        id: 'c1',
        body: 'Any update?',
        visibility: 'PUBLIC',
        author: { id: customerUser.id, fullName: customerUser.fullName, role: 'CUSTOMER' },
        createdAt: '2026-10-15T10:00:00Z',
      },
    });
    const user = userEvent.setup();
    renderWithProviders(
      <CommentComposer ticketId={TICKET_ID} canComment canAddInternalNote={false} />,
      {
        user: customerUser,
      },
    );

    expect(screen.queryByRole('button', { name: 'Internal note' })).not.toBeInTheDocument();
    expect(screen.queryByRole('group', { name: 'Comment visibility' })).not.toBeInTheDocument();

    await user.type(screen.getByRole('textbox'), 'Any update?');
    await user.click(screen.getByRole('button', { name: /send/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(`/tickets/${TICKET_ID}/comments`, {
        body: 'Any update?',
        visibility: 'PUBLIC',
      }),
    );
    await waitFor(() => expect(screen.getByRole('textbox')).toHaveValue(''));
  });

  it('shows the toggle for staff and sends INTERNAL notes', async () => {
    const post = vi.spyOn(api, 'post').mockResolvedValue({ data: { id: 'c2' } });
    const user = userEvent.setup();
    renderWithProviders(<CommentComposer ticketId={TICKET_ID} canComment canAddInternalNote />, {
      user: agentUser,
    });

    expect(screen.getByRole('button', { name: 'Public reply' })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await user.click(screen.getByRole('button', { name: 'Internal note' }));
    expect(screen.getByText(/customers won’t see this note/i)).toBeInTheDocument();

    await user.type(screen.getByRole('textbox'), 'Check SMTP logs');
    await user.click(screen.getByRole('button', { name: /add note/i }));

    await waitFor(() =>
      expect(post).toHaveBeenCalledWith(`/tickets/${TICKET_ID}/comments`, {
        body: 'Check SMTP logs',
        visibility: 'INTERNAL',
      }),
    );
  });

  it('validates an empty body client-side', async () => {
    const post = vi.spyOn(api, 'post');
    const user = userEvent.setup();
    renderWithProviders(
      <CommentComposer ticketId={TICKET_ID} canComment canAddInternalNote={false} />,
      {
        user: customerUser,
      },
    );
    await user.click(screen.getByRole('button', { name: /send/i }));
    expect(await screen.findByText('Write something first')).toBeInTheDocument();
    expect(post).not.toHaveBeenCalled();
  });

  it('is replaced by a notice when the ticket cannot be commented on (CLOSED)', () => {
    renderWithProviders(
      <CommentComposer ticketId={TICKET_ID} canComment={false} canAddInternalNote />,
      {
        user: agentUser,
      },
    );
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
    expect(screen.getByText(/this ticket is closed/i)).toBeInTheDocument();
  });
});
