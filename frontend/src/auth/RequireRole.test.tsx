import { screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { adminUser, agentUser } from '../test/fixtures';
import { renderWithProviders } from '../test/renderWithProviders';
import { RequireRole } from './RequireRole';

describe('RequireRole', () => {
  it('shows "Not authorized" for other roles', () => {
    renderWithProviders(
      <RequireRole roles={['ORG_ADMIN']}>
        <p>secret admin page</p>
      </RequireRole>,
      { user: agentUser },
    );
    expect(screen.getByRole('heading', { name: 'Not authorized' })).toBeInTheDocument();
    expect(screen.queryByText('secret admin page')).not.toBeInTheDocument();
  });

  it('renders children for allowed roles', () => {
    renderWithProviders(
      <RequireRole roles={['ORG_ADMIN']}>
        <p>secret admin page</p>
      </RequireRole>,
      { user: adminUser },
    );
    expect(screen.getByText('secret admin page')).toBeInTheDocument();
  });
});
