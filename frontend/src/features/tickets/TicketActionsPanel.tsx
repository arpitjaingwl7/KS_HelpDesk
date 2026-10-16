import PersonAddAlt1OutlinedIcon from '@mui/icons-material/PersonAddAlt1Outlined';
import PersonRemoveOutlinedIcon from '@mui/icons-material/PersonRemoveOutlined';
import {
  Box,
  Button,
  CircularProgress,
  Divider,
  MenuItem,
  Paper,
  Select,
  Stack,
  Tooltip,
  Typography,
} from '@mui/material';
import { useState, type ReactNode } from 'react';
import { hasCode, isGloballyHandled, problemMessage } from '../../api/problem';
import {
  TICKET_PRIORITIES,
  type TicketDetail,
  type TicketPriority,
  type TicketStatus,
} from '../../api/types';
import { useCurrentUser } from '../../auth/useAuth';
import { useToast } from '../../components/useToast';
import { formatDateTime, formatRelative } from '../../utils/formatDate';
import { PRIORITY_LABELS, STATUS_LABELS } from '../../utils/labels';
import { useAgents, useAssignTicket, useChangeStatus, useUpdateTicket } from './api';
import { ChangeStatusDialog } from './ChangeStatusDialog';
import { PriorityChip } from './PriorityChip';
import { StatusChip } from './StatusChip';

interface TicketActionsPanelProps {
  ticket: TicketDetail;
  /** Non-field errors (400/409) from an action, shown by the page. */
  onError(error: unknown): void;
  onConflict(): void;
}

function Row({ label, children }: { label: string; children: ReactNode }) {
  return (
    <Box>
      <Typography variant="overline" color="text.secondary" sx={{ lineHeight: 1.8 }}>
        {label}
      </Typography>
      <Box>{children}</Box>
    </Box>
  );
}

function DateRow({ label, value }: { label: string; value: string | null }) {
  return (
    <Stack direction="row" justifyContent="space-between" spacing={2}>
      <Typography variant="body2" color="text.secondary">
        {label}
      </Typography>
      <Tooltip title={value ? formatDateTime(value) : ''}>
        <Typography variant="body2">{value ? formatRelative(value) : '—'}</Typography>
      </Tooltip>
    </Stack>
  );
}

const UNASSIGNED = '__unassigned__';

export function TicketActionsPanel({ ticket, onError, onConflict }: TicketActionsPanelProps) {
  const user = useCurrentUser();
  const toast = useToast();
  const { permissions, allowedTransitions } = ticket;
  const agentsQuery = useAgents(permissions.canAssign);
  const changeStatus = useChangeStatus(ticket.id);
  const updateTicket = useUpdateTicket(ticket.id);
  const assign = useAssignTicket(ticket.id);

  const [targetStatus, setTargetStatus] = useState<TicketStatus | null>(null);
  const [statusError, setStatusError] = useState<string | null>(null);

  const handleActionError = (err: unknown) => {
    if (isGloballyHandled(err)) return;
    if (hasCode(err, 'CONCURRENT_MODIFICATION')) onConflict();
    else onError(err);
  };

  const confirmStatus = async (comment: string | undefined) => {
    if (!targetStatus) return;
    setStatusError(null);
    try {
      await changeStatus.mutateAsync({ status: targetStatus, ...(comment ? { comment } : {}) });
      toast.success(`Status changed to ${STATUS_LABELS[targetStatus]}`);
      setTargetStatus(null);
    } catch (err) {
      if (isGloballyHandled(err)) {
        setTargetStatus(null);
        return;
      }
      if (hasCode(err, 'CONCURRENT_MODIFICATION')) {
        setTargetStatus(null);
        onConflict();
        return;
      }
      setStatusError(problemMessage(err));
    }
  };

  const changePriority = (priority: TicketPriority) => {
    if (priority === ticket.priority) return;
    updateTicket.mutate(
      { priority, version: ticket.version },
      {
        onSuccess: () => toast.success(`Priority set to ${PRIORITY_LABELS[priority]}`),
        onError: handleActionError,
      },
    );
  };

  const setAssignee = (assigneeId: string | null) => {
    if ((ticket.assignee?.id ?? null) === assigneeId) return;
    assign.mutate(assigneeId, {
      onSuccess: (t) =>
        toast.success(t.assignee ? `Assigned to ${t.assignee.fullName}` : 'Ticket unassigned'),
      onError: handleActionError,
    });
  };

  const agents = agentsQuery.data ?? [];
  const assigneeInList = ticket.assignee && agents.some((a) => a.id === ticket.assignee?.id);
  const assignedToMe = ticket.assignee?.id === user.id;

  return (
    <Paper variant="outlined" sx={{ p: 2.5 }}>
      <Stack spacing={2.5}>
        <Row label="Status">
          {allowedTransitions.length > 0 ? (
            <Select<TicketStatus>
              size="small"
              fullWidth
              value={ticket.status}
              inputProps={{ 'aria-label': 'Change status' }}
              onChange={(e) => {
                setStatusError(null);
                setTargetStatus(e.target.value as TicketStatus);
              }}
              renderValue={(s) => <StatusChip status={s} />}
              disabled={changeStatus.isPending}
              sx={{ '& .MuiSelect-select': { py: 0.75 } }}
            >
              <MenuItem value={ticket.status} disabled divider>
                <StatusChip status={ticket.status} sx={{ mr: 1 }} /> current
              </MenuItem>
              {allowedTransitions.map((s) => (
                <MenuItem key={s} value={s}>
                  <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                    Move to <StatusChip status={s} />
                  </Box>
                </MenuItem>
              ))}
            </Select>
          ) : (
            <StatusChip status={ticket.status} />
          )}
        </Row>

        <Row label="Priority">
          {permissions.canChangePriority ? (
            <Stack direction="row" spacing={1} alignItems="center">
              <Select<TicketPriority>
                size="small"
                fullWidth
                value={ticket.priority}
                onChange={(e) => changePriority(e.target.value as TicketPriority)}
                renderValue={(p) => <PriorityChip priority={p} />}
                inputProps={{ 'aria-label': 'Priority' }}
                disabled={updateTicket.isPending}
                sx={{ '& .MuiSelect-select': { py: 0.75 } }}
              >
                {TICKET_PRIORITIES.map((p) => (
                  <MenuItem key={p} value={p}>
                    <PriorityChip priority={p} />
                  </MenuItem>
                ))}
              </Select>
              {updateTicket.isPending && <CircularProgress size={18} />}
            </Stack>
          ) : (
            <PriorityChip priority={ticket.priority} />
          )}
        </Row>

        <Row label="Assignee">
          {permissions.canAssign ? (
            <Stack spacing={1}>
              <Stack direction="row" spacing={1} alignItems="center">
                <Select<string>
                  size="small"
                  fullWidth
                  value={ticket.assignee?.id ?? UNASSIGNED}
                  onChange={(e) =>
                    setAssignee(e.target.value === UNASSIGNED ? null : e.target.value)
                  }
                  inputProps={{ 'aria-label': 'Assignee' }}
                  disabled={assign.isPending || agentsQuery.isPending}
                >
                  <MenuItem value={UNASSIGNED}>
                    <em>Unassigned</em>
                  </MenuItem>
                  {ticket.assignee && !assigneeInList && (
                    <MenuItem value={ticket.assignee.id}>{ticket.assignee.fullName}</MenuItem>
                  )}
                  {agents.map((a) => (
                    <MenuItem key={a.id} value={a.id}>
                      {a.fullName}
                      {a.id === user.id ? ' (me)' : ''}
                    </MenuItem>
                  ))}
                </Select>
                {assign.isPending && <CircularProgress size={18} />}
              </Stack>
              <Stack direction="row" spacing={1}>
                {!assignedToMe && (
                  <Button
                    size="small"
                    variant="outlined"
                    startIcon={<PersonAddAlt1OutlinedIcon />}
                    onClick={() => setAssignee(user.id)}
                    disabled={assign.isPending}
                  >
                    Assign to me
                  </Button>
                )}
                {ticket.assignee && (
                  <Button
                    size="small"
                    color="inherit"
                    startIcon={<PersonRemoveOutlinedIcon />}
                    onClick={() => setAssignee(null)}
                    disabled={assign.isPending}
                  >
                    Unassign
                  </Button>
                )}
              </Stack>
            </Stack>
          ) : (
            <Typography variant="body2" color={ticket.assignee ? 'text.primary' : 'text.secondary'}>
              {ticket.assignee?.fullName ?? 'Not yet assigned'}
            </Typography>
          )}
        </Row>

        <Row label="Requester">
          <Typography variant="body2">{ticket.requester.fullName}</Typography>
          <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
            {ticket.requester.email}
          </Typography>
        </Row>

        <Divider />

        <Stack spacing={0.75}>
          <DateRow label="Created" value={ticket.createdAt} />
          <DateRow label="Updated" value={ticket.updatedAt} />
          <DateRow label="Resolved" value={ticket.resolvedAt} />
          <DateRow label="Closed" value={ticket.closedAt} />
        </Stack>
      </Stack>

      <ChangeStatusDialog
        open={targetStatus !== null}
        from={ticket.status}
        to={targetStatus}
        loading={changeStatus.isPending}
        error={statusError}
        onConfirm={(comment) => void confirmStatus(comment)}
        onClose={() => setTargetStatus(null)}
      />
    </Paper>
  );
}
