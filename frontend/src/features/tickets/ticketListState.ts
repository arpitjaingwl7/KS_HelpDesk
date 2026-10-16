import {
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  type TicketPriority,
  type TicketQuery,
  type TicketStatus,
} from '../../api/types';
import {
  parseEnumList,
  parsePositiveInt,
  useUrlState,
  type UrlStateCodec,
} from '../../utils/useUrlState';

/** `any` = no filter, `unassigned`, `me`, or a specific agent's UUID. */
export type AssigneeFilter = 'any' | 'unassigned' | 'me' | (string & {});

export const TICKET_SORT_FIELDS = [
  'createdAt',
  'updatedAt',
  'number',
  'priority',
  'status',
] as const;
export type TicketSortField = (typeof TICKET_SORT_FIELDS)[number];
export type SortDirection = 'asc' | 'desc';

export interface TicketListState {
  q: string;
  status: TicketStatus[];
  priority: TicketPriority[];
  assignee: AssigneeFilter;
  /** 1-based in the URL and in this state; converted to 0-based for the API. */
  page: number;
  size: number;
  sortField: TicketSortField;
  sortDir: SortDirection;
}

export const DEFAULT_TICKET_LIST_STATE: TicketListState = {
  q: '',
  status: [],
  priority: [],
  assignee: 'any',
  page: 1,
  size: 20,
  sortField: 'createdAt',
  sortDir: 'desc',
};

export const TICKET_LIST_KEYS = [
  'q',
  'status',
  'priority',
  'assignee',
  'page',
  'size',
  'sort',
] as const;

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function parseAssignee(raw: string | null): AssigneeFilter {
  if (raw === 'unassigned' || raw === 'me') return raw;
  if (raw && UUID_RE.test(raw)) return raw;
  return 'any';
}

function parseSort(raw: string | null): Pick<TicketListState, 'sortField' | 'sortDir'> {
  if (!raw)
    return {
      sortField: DEFAULT_TICKET_LIST_STATE.sortField,
      sortDir: DEFAULT_TICKET_LIST_STATE.sortDir,
    };
  const [field, dir] = raw.split(',');
  const sortField = (TICKET_SORT_FIELDS as readonly string[]).includes(field ?? '')
    ? (field as TicketSortField)
    : DEFAULT_TICKET_LIST_STATE.sortField;
  const sortDir: SortDirection = dir === 'asc' ? 'asc' : 'desc';
  return { sortField, sortDir };
}

export const ticketListCodec: UrlStateCodec<TicketListState> = {
  parse(params) {
    return {
      q: params.get('q') ?? '',
      status: parseEnumList(params, 'status', TICKET_STATUSES),
      priority: parseEnumList(params, 'priority', TICKET_PRIORITIES),
      assignee: parseAssignee(params.get('assignee')),
      page: parsePositiveInt(params.get('page'), 1),
      size: parsePositiveInt(params.get('size'), DEFAULT_TICKET_LIST_STATE.size, 100),
      ...parseSort(params.get('sort')),
    };
  },
  serialize(state) {
    const params = new URLSearchParams();
    const q = state.q.trim();
    if (q) params.set('q', q);
    state.status.forEach((s) => params.append('status', s));
    state.priority.forEach((p) => params.append('priority', p));
    if (state.assignee !== 'any') params.set('assignee', state.assignee);
    if (state.page > 1) params.set('page', String(state.page));
    if (state.size !== DEFAULT_TICKET_LIST_STATE.size) params.set('size', String(state.size));
    if (
      state.sortField !== DEFAULT_TICKET_LIST_STATE.sortField ||
      state.sortDir !== DEFAULT_TICKET_LIST_STATE.sortDir
    ) {
      params.set('sort', `${state.sortField},${state.sortDir}`);
    }
    return params;
  },
};

export function useTicketListState() {
  return useUrlState(ticketListCodec, TICKET_LIST_KEYS);
}

export function isFiltered(state: TicketListState): boolean {
  return Boolean(
    state.q.trim() || state.status.length || state.priority.length || state.assignee !== 'any',
  );
}

/**
 * Converts URL state into GET /tickets params.
 * Customers never send assignee filters (the backend ignores them anyway).
 */
export function toTicketQuery(
  state: TicketListState,
  ctx: { currentUserId: string; allowAssigneeFilter: boolean },
): TicketQuery {
  const query: TicketQuery = {
    page: state.page - 1,
    size: state.size,
    sort: `${state.sortField},${state.sortDir}`,
  };
  const q = state.q.trim();
  if (q) query.q = q;
  if (state.status.length) query.status = state.status;
  if (state.priority.length) query.priority = state.priority;
  if (ctx.allowAssigneeFilter) {
    if (state.assignee === 'unassigned') query.unassigned = true;
    else if (state.assignee === 'me') query.assigneeId = ctx.currentUserId;
    else if (state.assignee !== 'any') query.assigneeId = state.assignee;
  }
  return query;
}
