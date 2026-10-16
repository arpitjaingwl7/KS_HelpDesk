import { describe, expect, it } from 'vitest';
import { DEFAULT_TICKET_LIST_STATE, ticketListCodec, toTicketQuery } from './ticketListState';

const AGENT_ID = '0192a6f3-0000-4000-8000-000000000001';

describe('ticketListCodec', () => {
  it('parses both repeated and comma-separated multi values and ignores junk', () => {
    const state = ticketListCodec.parse(
      new URLSearchParams(
        'status=OPEN&status=IN_PROGRESS,BOGUS&priority=HIGH&page=3&size=500&sort=priority,asc&assignee=nope',
      ),
    );
    expect(state).toMatchObject({
      status: ['OPEN', 'IN_PROGRESS'],
      priority: ['HIGH'],
      page: 3,
      size: 100,
      sortField: 'priority',
      sortDir: 'asc',
      assignee: 'any',
    });
  });

  it('omits defaults when serialising', () => {
    expect(ticketListCodec.serialize(DEFAULT_TICKET_LIST_STATE).toString()).toBe('');
  });

  it('round-trips', () => {
    const state = {
      ...DEFAULT_TICKET_LIST_STATE,
      q: 'vpn',
      status: ['OPEN' as const],
      assignee: AGENT_ID,
      page: 2,
    };
    expect(ticketListCodec.parse(ticketListCodec.serialize(state))).toEqual(state);
  });
});

describe('toTicketQuery', () => {
  const base = { ...DEFAULT_TICKET_LIST_STATE, page: 2 };

  it('converts to 0-based page and the sort param', () => {
    expect(toTicketQuery(base, { currentUserId: 'me-id', allowAssigneeFilter: true })).toEqual({
      page: 1,
      size: 20,
      sort: 'createdAt,desc',
    });
  });

  it('maps assignee=me to assigneeId and unassigned to unassigned=true (never both)', () => {
    const me = toTicketQuery(
      { ...base, assignee: 'me' },
      { currentUserId: 'me-id', allowAssigneeFilter: true },
    );
    expect(me.assigneeId).toBe('me-id');
    expect(me.unassigned).toBeUndefined();
    const un = toTicketQuery(
      { ...base, assignee: 'unassigned' },
      { currentUserId: 'me-id', allowAssigneeFilter: true },
    );
    expect(un.unassigned).toBe(true);
    expect(un.assigneeId).toBeUndefined();
  });

  it('drops the assignee filter for customers', () => {
    const q = toTicketQuery(
      { ...base, assignee: AGENT_ID },
      { currentUserId: 'me-id', allowAssigneeFilter: false },
    );
    expect(q.assigneeId).toBeUndefined();
    expect(q.unassigned).toBeUndefined();
  });
});
