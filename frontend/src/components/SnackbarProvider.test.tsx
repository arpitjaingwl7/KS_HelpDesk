import { act, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { SnackbarProvider } from './SnackbarProvider';
import { toastBus } from './toastBus';

describe('SnackbarProvider', () => {
  it('closes a toast when its close button is clicked, and shows the next one', async () => {
    render(<SnackbarProvider>app</SnackbarProvider>);

    act(() => {
      toastBus.emit('Ticket #42 created', 'success');
      toastBus.emit('Second message', 'info');
    });
    expect(await screen.findByText('Ticket #42 created')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /close/i }));

    // The first toast must stay closed (it used to reopen immediately)…
    await waitFor(() => expect(screen.queryByText('Ticket #42 created')).not.toBeInTheDocument());
    // …and the queued one is shown next.
    expect(await screen.findByText('Second message')).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: /close/i }));
    await waitFor(() => expect(screen.queryByText('Second message')).not.toBeInTheDocument());
  });
});
