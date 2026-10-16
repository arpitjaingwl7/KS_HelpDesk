import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import EditOutlinedIcon from '@mui/icons-material/EditOutlined';
import FindInPageOutlinedIcon from '@mui/icons-material/FindInPageOutlined';
import {
  Alert,
  AlertTitle,
  Box,
  Button,
  Paper,
  Skeleton,
  Stack,
  Tab,
  Tabs,
  Tooltip,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom';
import { getProblem, problemMessage } from '../../api/problem';
import { ErrorAlert } from '../../components/ErrorAlert';
import { StatusPage } from '../../components/StatusPage';
import { formatDateTime, formatRelative } from '../../utils/formatDate';
import { CommentComposer } from '../comments/CommentComposer';
import { CommentList } from '../comments/CommentList';
import { HistoryTimeline } from '../history/HistoryTimeline';
import { useTicket } from './api';
import { EditTicketDialog } from './EditTicketDialog';
import { PriorityChip } from './PriorityChip';
import { StatusChip } from './StatusChip';
import { TicketActionsPanel } from './TicketActionsPanel';

type DetailTab = 'conversation' | 'history';

function DetailSkeleton() {
  return (
    <Box
      sx={{ display: 'grid', gap: 3, gridTemplateColumns: { xs: '1fr', md: '1fr 340px' } }}
      aria-busy
    >
      <Paper variant="outlined" sx={{ p: 3 }}>
        <Skeleton variant="text" width="60%" height={40} />
        <Skeleton variant="text" width="30%" />
        <Skeleton variant="rounded" height={140} sx={{ mt: 2 }} />
      </Paper>
      <Paper variant="outlined" sx={{ p: 3 }}>
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} variant="rounded" height={40} sx={{ mb: 2 }} />
        ))}
      </Paper>
    </Box>
  );
}

export function TicketDetailPage() {
  const { ticketId = '' } = useParams<{ ticketId: string }>();
  const [searchParams, setSearchParams] = useSearchParams();
  const tab: DetailTab = searchParams.get('tab') === 'history' ? 'history' : 'conversation';

  const ticketQuery = useTicket(ticketId);
  const [editOpen, setEditOpen] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [actionError, setActionError] = useState<unknown>(null);

  const reload = async () => {
    await ticketQuery.refetch();
    setConflict(false);
  };

  if (ticketQuery.isPending) return <DetailSkeleton />;

  if (ticketQuery.isError) {
    const status = getProblem(ticketQuery.error)?.status;
    if (status === 404 || status === 400) {
      return (
        <StatusPage
          icon={<FindInPageOutlinedIcon fontSize="inherit" />}
          title="Ticket not found"
          description="This ticket doesn't exist, or you don't have access to it."
        />
      );
    }
    return (
      <ErrorAlert
        error={ticketQuery.error}
        title="Couldn't load ticket"
        onRetry={() => void ticketQuery.refetch()}
      />
    );
  }

  const ticket = ticketQuery.data;
  const { permissions } = ticket;

  const setTab = (next: DetailTab) => {
    const params = new URLSearchParams(searchParams);
    if (next === 'conversation') params.delete('tab');
    else params.set('tab', next);
    setSearchParams(params, { replace: true });
  };

  return (
    <>
      <Button component={RouterLink} to="/tickets" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        Back to tickets
      </Button>

      {conflict && (
        <Alert
          severity="warning"
          sx={{ mb: 2 }}
          action={
            <Button
              color="inherit"
              size="small"
              variant="outlined"
              onClick={() => void reload()}
              disabled={ticketQuery.isFetching}
            >
              {ticketQuery.isFetching ? 'Reloading…' : 'Reload'}
            </Button>
          }
        >
          <AlertTitle>This ticket was changed by someone else</AlertTitle>
          Reload to see the latest version before making more changes.
        </Alert>
      )}

      {actionError != null && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setActionError(null)}>
          {problemMessage(actionError)}
        </Alert>
      )}

      <Box
        sx={{
          display: 'grid',
          gap: 3,
          gridTemplateColumns: { xs: '1fr', md: 'minmax(0, 1fr) 340px' },
          alignItems: 'start',
        }}
      >
        <Stack spacing={3} sx={{ minWidth: 0 }}>
          <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
            <Stack
              direction="row"
              spacing={2}
              alignItems="flex-start"
              justifyContent="space-between"
            >
              <Box sx={{ minWidth: 0 }}>
                <Typography variant="h5" component="h1" sx={{ overflowWrap: 'anywhere' }}>
                  <Box component="span" sx={{ color: 'text.secondary', fontWeight: 600, mr: 1 }}>
                    #{ticket.number}
                  </Box>
                  {ticket.subject}
                </Typography>
                <Stack
                  direction="row"
                  spacing={1}
                  alignItems="center"
                  sx={{ mt: 1, flexWrap: 'wrap', rowGap: 1 }}
                >
                  <StatusChip status={ticket.status} />
                  <PriorityChip priority={ticket.priority} />
                  <Typography variant="body2" color="text.secondary">
                    Requested by <strong>{ticket.requester.fullName}</strong> ·{' '}
                    <Tooltip title={formatDateTime(ticket.createdAt)}>
                      <span>{formatRelative(ticket.createdAt)}</span>
                    </Tooltip>
                  </Typography>
                </Stack>
              </Box>
              {permissions.canEdit && (
                <Button
                  variant="outlined"
                  startIcon={<EditOutlinedIcon />}
                  onClick={() => setEditOpen(true)}
                  sx={{ flexShrink: 0 }}
                >
                  Edit
                </Button>
              )}
            </Stack>
            <Typography
              variant="body1"
              sx={{ mt: 3, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', lineHeight: 1.7 }}
            >
              {ticket.description}
            </Typography>
          </Paper>

          <Paper variant="outlined">
            <Tabs
              value={tab}
              onChange={(_e, v: DetailTab) => setTab(v)}
              aria-label="Ticket sections"
              sx={{ px: 2, borderBottom: 1, borderColor: 'divider' }}
            >
              <Tab
                value="conversation"
                label="Conversation"
                id="tab-conversation"
                aria-controls="panel-conversation"
              />
              <Tab value="history" label="History" id="tab-history" aria-controls="panel-history" />
            </Tabs>
            <Box
              role="tabpanel"
              id={`panel-${tab}`}
              aria-labelledby={`tab-${tab}`}
              sx={{ p: { xs: 2, sm: 3 } }}
            >
              {tab === 'conversation' ? (
                <Stack spacing={3}>
                  <CommentList ticketId={ticket.id} />
                  <CommentComposer
                    ticketId={ticket.id}
                    canComment={permissions.canComment}
                    canAddInternalNote={permissions.canAddInternalNote}
                  />
                </Stack>
              ) : (
                <HistoryTimeline ticketId={ticket.id} />
              )}
            </Box>
          </Paper>
        </Stack>

        <Box sx={{ position: { md: 'sticky' }, top: { md: 88 } }}>
          <TicketActionsPanel
            ticket={ticket}
            onError={(err) => setActionError(err)}
            onConflict={() => setConflict(true)}
          />
        </Box>
      </Box>

      {permissions.canEdit && (
        <EditTicketDialog
          open={editOpen}
          ticket={ticket}
          onClose={() => setEditOpen(false)}
          onConflict={() => setConflict(true)}
          onReload={reload}
        />
      )}
    </>
  );
}
