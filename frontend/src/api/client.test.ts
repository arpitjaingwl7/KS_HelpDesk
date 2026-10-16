import { describe, expect, it } from 'vitest';
import { serializeParams } from './client';

describe('serializeParams', () => {
  it('repeats array params and drops empty values', () => {
    expect(
      serializeParams({
        q: 'printer',
        status: ['OPEN', 'IN_PROGRESS'],
        priority: [],
        assigneeId: undefined,
        unassigned: true,
        page: 0,
        empty: '',
      }),
    ).toBe('q=printer&status=OPEN&status=IN_PROGRESS&unassigned=true&page=0');
  });
});
