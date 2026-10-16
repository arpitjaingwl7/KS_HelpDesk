import AddCircleOutlineIcon from '@mui/icons-material/AddCircleOutline';
import FlagOutlinedIcon from '@mui/icons-material/FlagOutlined';
import HistoryIcon from '@mui/icons-material/History';
import PersonOutlineIcon from '@mui/icons-material/PersonOutline';
import SyncAltIcon from '@mui/icons-material/SyncAlt';
import { Avatar, Box, Skeleton, Stack, Tooltip, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import {
  TICKET_PRIORITIES,
  TICKET_STATUSES,
  type HistoryEntry,
  type TicketPriority,
  type TicketStatus,
} from '../../api/types';
import { EmptyState } from '../../components/EmptyState';
import { ErrorAlert } from '../../components/ErrorAlert';
import { formatDateTime, formatRelative } from '../../utils/formatDate';
import { PriorityChip } from '../tickets/PriorityChip';
import { StatusChip } from '../tickets/StatusChip';
import { useHistory } from './api';

function statusNode(value: string | null): ReactNode {
  if (value && (TICKET_STATUSES as readonly string[]).includes(value)) {
    return <StatusChip status={value as TicketStatus} />;
  }
  return <strong>{value ?? '—'}</strong>;
}

function priorityNode(value: string | null): ReactNode {
  if (value && (TICKET_PRIORITIES as readonly string[]).includes(value)) {
    return <PriorityChip priority={value as TicketPriority} />;
  }
  return <strong>{value ?? '—'}</strong>;
}

function describeEntry(entry: HistoryEntry): { icon: ReactNode; text: ReactNode } {
  const actor = <strong>{entry.actor.fullName}</strong>;
  switch (entry.eventType) {
    case 'CREATED':
      return {
        icon: <AddCircleOutlineIcon fontSize="small" />,
        text: (
          <>
            {actor} created the ticket
            {entry.newValue ? <> as {statusNode(entry.newValue)}</> : null}
          </>
        ),
      };
    case 'STATUS_CHANGED':
      return {
        icon: <SyncAltIcon fontSize="small" />,
        text: (
          <>
            {actor} changed status {statusNode(entry.oldValue)} → {statusNode(entry.newValue)}
          </>
        ),
      };
    case 'PRIORITY_CHANGED':
      return {
        icon: <FlagOutlinedIcon fontSize="small" />,
        text: (
          <>
            {actor} changed priority {priorityNode(entry.oldValue)} → {priorityNode(entry.newValue)}
          </>
        ),
      };
    case 'ASSIGNEE_CHANGED': {
      const from = entry.oldValue?.fullName;
      const to = entry.newValue?.fullName;
      let text: ReactNode;
      if (!from && to)
        text = (
          <>
            {actor} assigned the ticket to <strong>{to}</strong>
          </>
        );
      else if (from && !to)
        text = (
          <>
            {actor} unassigned <strong>{from}</strong>
          </>
        );
      else
        text = (
          <>
            {actor} reassigned the ticket from <strong>{from ?? '—'}</strong> to{' '}
            <strong>{to ?? '—'}</strong>
          </>
        );
      return { icon: <PersonOutlineIcon fontSize="small" />, text };
    }
    default:
      return { icon: <HistoryIcon fontSize="small" />, text: <>{actor} updated the ticket</> };
  }
}

export function HistoryTimeline({ ticketId }: { ticketId: string }) {
  const query = useHistory(ticketId);

  if (query.isPending) {
    return (
      <Stack spacing={2} aria-busy>
        {[0, 1, 2].map((i) => (
          <Stack key={i} direction="row" spacing={2} alignItems="center">
            <Skeleton variant="circular" width={32} height={32} />
            <Skeleton variant="text" width="60%" />
          </Stack>
        ))}
      </Stack>
    );
  }
  if (query.isError) {
    return (
      <ErrorAlert
        error={query.error}
        title="Couldn't load history"
        onRetry={() => void query.refetch()}
      />
    );
  }
  if (query.data.length === 0) {
    return <EmptyState icon={<HistoryIcon fontSize="inherit" />} title="No history yet" />;
  }

  return (
    <Box component="ol" aria-label="Ticket history" sx={{ listStyle: 'none', m: 0, p: 0 }}>
      {query.data.map((entry, index) => {
        const { icon, text } = describeEntry(entry);
        const last = index === query.data.length - 1;
        return (
          <Box
            component="li"
            key={entry.id}
            sx={{ display: 'flex', gap: 2, position: 'relative', pb: last ? 0 : 2.5 }}
          >
            {!last && (
              <Box
                aria-hidden
                sx={{
                  position: 'absolute',
                  left: 15,
                  top: 34,
                  bottom: 0,
                  width: 2,
                  bgcolor: 'divider',
                }}
              />
            )}
            <Avatar
              sx={{
                width: 32,
                height: 32,
                bgcolor: 'background.paper',
                color: 'primary.main',
                border: 1,
                borderColor: 'divider',
              }}
            >
              {icon}
            </Avatar>
            <Box sx={{ pt: 0.5, minWidth: 0 }}>
              <Typography
                variant="body2"
                component="div"
                sx={{ display: 'flex', flexWrap: 'wrap', alignItems: 'center', gap: 0.75 }}
              >
                {text}
              </Typography>
              <Tooltip title={formatDateTime(entry.createdAt)}>
                <Typography
                  variant="caption"
                  color="text.secondary"
                  component="time"
                  dateTime={entry.createdAt}
                >
                  {formatRelative(entry.createdAt)}
                </Typography>
              </Tooltip>
            </Box>
          </Box>
        );
      })}
    </Box>
  );
}
