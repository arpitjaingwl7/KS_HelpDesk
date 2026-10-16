import {
  Link,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TableSortLabel,
  Tooltip,
  Typography,
} from '@mui/material';
import type { ReactNode } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import type { TicketSummary } from '../../api/types';
import { formatDateTime, formatRelative } from '../../utils/formatDate';
import { PriorityChip } from './PriorityChip';
import { StatusChip } from './StatusChip';
import type { SortDirection, TicketSortField } from './ticketListState';

interface TicketTableProps {
  tickets: TicketSummary[];
  loading: boolean;
  showRequester: boolean;
  sortField: TicketSortField;
  sortDir: SortDirection;
  onSort(field: TicketSortField): void;
  empty?: ReactNode;
}

interface Column {
  id: string;
  label: string;
  sortField?: TicketSortField;
  width?: number | string;
  hideBelow?: 'sm' | 'md';
}

const hide = (bp?: 'sm' | 'md') => (bp ? { display: { xs: 'none', [bp]: 'table-cell' } } : {});

export function TicketTable({
  tickets,
  loading,
  showRequester,
  sortField,
  sortDir,
  onSort,
  empty,
}: TicketTableProps) {
  const navigate = useNavigate();

  const columns: Column[] = [
    { id: 'number', label: '#', sortField: 'number', width: 72 },
    { id: 'subject', label: 'Subject' },
    { id: 'status', label: 'Status', sortField: 'status', width: 140 },
    { id: 'priority', label: 'Priority', sortField: 'priority', width: 120, hideBelow: 'sm' },
    ...(showRequester
      ? [{ id: 'requester', label: 'Requester', width: 160, hideBelow: 'md' } as Column]
      : []),
    { id: 'assignee', label: 'Assignee', width: 160, hideBelow: 'md' },
    { id: 'createdAt', label: 'Created', sortField: 'createdAt', width: 120, hideBelow: 'sm' },
    { id: 'updatedAt', label: 'Updated', sortField: 'updatedAt', width: 120, hideBelow: 'md' },
  ];

  return (
    <TableContainer>
      <Table size="medium" aria-label="Tickets" aria-busy={loading}>
        <TableHead>
          <TableRow>
            {columns.map((col) => (
              <TableCell
                key={col.id}
                sx={{ width: col.width, ...hide(col.hideBelow) }}
                sortDirection={col.sortField === sortField ? sortDir : false}
              >
                {col.sortField ? (
                  <TableSortLabel
                    active={col.sortField === sortField}
                    direction={col.sortField === sortField ? sortDir : 'desc'}
                    onClick={() => onSort(col.sortField as TicketSortField)}
                  >
                    {col.label}
                  </TableSortLabel>
                ) : (
                  col.label
                )}
              </TableCell>
            ))}
          </TableRow>
        </TableHead>
        <TableBody>
          {loading && tickets.length === 0
            ? Array.from({ length: 6 }).map((_, i) => (
                <TableRow key={`sk-${i}`}>
                  {columns.map((col) => (
                    <TableCell key={col.id} sx={hide(col.hideBelow)}>
                      <Skeleton variant="text" width={col.id === 'subject' ? '80%' : '60%'} />
                    </TableCell>
                  ))}
                </TableRow>
              ))
            : tickets.map((t) => (
                <TableRow
                  key={t.id}
                  hover
                  onClick={() => navigate(`/tickets/${t.id}`)}
                  sx={{ cursor: 'pointer', opacity: loading ? 0.6 : 1 }}
                >
                  <TableCell>
                    <Typography variant="body2" color="text.secondary" sx={{ fontWeight: 600 }}>
                      #{t.number}
                    </Typography>
                  </TableCell>
                  <TableCell sx={{ maxWidth: 0, width: '100%' }}>
                    <Link
                      component={RouterLink}
                      to={`/tickets/${t.id}`}
                      underline="hover"
                      color="text.primary"
                      onClick={(e) => e.stopPropagation()}
                      sx={{
                        fontWeight: 600,
                        display: 'block',
                        overflow: 'hidden',
                        textOverflow: 'ellipsis',
                        whiteSpace: 'nowrap',
                      }}
                    >
                      {t.subject}
                    </Link>
                  </TableCell>
                  <TableCell>
                    <StatusChip status={t.status} />
                  </TableCell>
                  <TableCell sx={hide('sm')}>
                    <PriorityChip priority={t.priority} />
                  </TableCell>
                  {showRequester && (
                    <TableCell sx={hide('md')}>
                      <Typography variant="body2" noWrap>
                        {t.requester.fullName}
                      </Typography>
                    </TableCell>
                  )}
                  <TableCell sx={hide('md')}>
                    <Typography
                      variant="body2"
                      noWrap
                      color={t.assignee ? 'text.primary' : 'text.disabled'}
                    >
                      {t.assignee?.fullName ?? 'Unassigned'}
                    </Typography>
                  </TableCell>
                  <TableCell sx={hide('sm')}>
                    <Tooltip title={formatDateTime(t.createdAt)}>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {formatRelative(t.createdAt)}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                  <TableCell sx={hide('md')}>
                    <Tooltip title={formatDateTime(t.updatedAt)}>
                      <Typography variant="body2" color="text.secondary" noWrap>
                        {formatRelative(t.updatedAt)}
                      </Typography>
                    </Tooltip>
                  </TableCell>
                </TableRow>
              ))}
          {!loading && tickets.length === 0 && empty && (
            <TableRow>
              <TableCell colSpan={columns.length} sx={{ borderBottom: 0 }}>
                {empty}
              </TableCell>
            </TableRow>
          )}
        </TableBody>
      </Table>
    </TableContainer>
  );
}
