import AddIcon from '@mui/icons-material/Add';
import SearchOffOutlinedIcon from '@mui/icons-material/SearchOffOutlined';
import { Box, Button, Divider, LinearProgress, Paper } from '@mui/material';
import { useEffect, useMemo } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import { useCurrentUser } from '../../auth/useAuth';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { PageHeader } from '../../components/PageHeader';
import { Pagination } from '../../components/Pagination';
import { isStaff } from '../../utils/labels';
import { useTickets } from './api';
import { TicketFilters } from './TicketFilters';
import {
  isFiltered,
  toTicketQuery,
  useTicketListState,
  type TicketSortField,
} from './ticketListState';
import { TicketStatsStrip } from './TicketStatsStrip';
import { TicketTable } from './TicketTable';

export function TicketListPage() {
  const user = useCurrentUser();
  const staff = isStaff(user.role);
  const [state, setState] = useTicketListState();

  const query = useMemo(
    () => toTicketQuery(state, { currentUserId: user.id, allowAssigneeFilter: staff }),
    [state, user.id, staff],
  );
  const ticketsQuery = useTickets(query);
  const page = ticketsQuery.data?.page;
  const tickets = ticketsQuery.data?.content ?? [];

  // If filters shrink the result set below the current page, jump to the last page.
  useEffect(() => {
    if (page && page.totalPages > 0 && state.page > page.totalPages) {
      setState({ page: page.totalPages }, { replace: true });
    }
  }, [page, state.page, setState]);

  const onSort = (field: TicketSortField) => {
    const sortDir = state.sortField === field && state.sortDir === 'desc' ? 'asc' : 'desc';
    setState({ sortField: field, sortDir, page: 1 });
  };

  const filtered = isFiltered(state);
  const empty = filtered ? (
    <EmptyState
      icon={<SearchOffOutlinedIcon fontSize="inherit" />}
      title="No tickets match your filters"
      description="Try a different search or clear the filters."
      action={
        <Button variant="outlined" component={RouterLink} to="/tickets">
          Clear filters
        </Button>
      }
    />
  ) : (
    <EmptyState
      title="No tickets yet"
      description={
        staff
          ? 'Tickets raised by your customers will appear here.'
          : 'Need help? Raise your first ticket.'
      }
      action={
        <Button
          variant="contained"
          component={RouterLink}
          to="/tickets/new"
          startIcon={<AddIcon />}
        >
          Create a ticket
        </Button>
      }
    />
  );

  return (
    <>
      <PageHeader
        title={staff ? 'Tickets' : 'My tickets'}
        subtitle={staff ? `All tickets in ${user.organization.name}` : 'Tickets you have raised'}
        actions={
          <Button
            variant="contained"
            component={RouterLink}
            to="/tickets/new"
            startIcon={<AddIcon />}
          >
            New ticket
          </Button>
        }
      />

      {staff && <TicketStatsStrip />}

      <Paper variant="outlined" sx={{ overflow: 'hidden' }}>
        <Box sx={{ p: 2 }}>
          <TicketFilters showAssignee={staff} />
        </Box>
        <Box sx={{ height: 4 }}>
          {ticketsQuery.isFetching && !ticketsQuery.isPending && <LinearProgress />}
        </Box>
        <Divider />
        {ticketsQuery.isError ? (
          <Box sx={{ p: 2 }}>
            <ErrorAlert
              error={ticketsQuery.error}
              title="Couldn't load tickets"
              onRetry={() => void ticketsQuery.refetch()}
            />
          </Box>
        ) : (
          <>
            <TicketTable
              tickets={tickets}
              loading={ticketsQuery.isPending || ticketsQuery.isPlaceholderData}
              showRequester={staff}
              sortField={state.sortField}
              sortDir={state.sortDir}
              onSort={onSort}
              empty={empty}
            />
            {page && page.totalElements > 0 && (
              <>
                <Divider />
                <Pagination
                  meta={page}
                  itemLabel={page.totalElements === 1 ? 'ticket' : 'tickets'}
                  onPageChange={(p) => setState({ page: p })}
                  onSizeChange={(size) => setState({ size, page: 1 })}
                />
              </>
            )}
          </>
        )}
      </Paper>
    </>
  );
}
