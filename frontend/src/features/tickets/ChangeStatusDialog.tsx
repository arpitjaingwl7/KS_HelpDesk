import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogContentText,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { useEffect, useState } from 'react';
import type { TicketStatus } from '../../api/types';
import { STATUS_LABELS } from '../../utils/labels';
import { StatusChip } from './StatusChip';

interface ChangeStatusDialogProps {
  open: boolean;
  from: TicketStatus;
  to: TicketStatus | null;
  loading: boolean;
  error?: string | null;
  onConfirm(comment: string | undefined): void;
  onClose(): void;
}

export function ChangeStatusDialog({
  open,
  from,
  to,
  loading,
  error,
  onConfirm,
  onClose,
}: ChangeStatusDialogProps) {
  const [comment, setComment] = useState('');
  useEffect(() => {
    if (open) setComment('');
  }, [open]);

  const tooLong = comment.length > 5000;

  return (
    <Dialog
      open={open && Boolean(to)}
      onClose={loading ? undefined : onClose}
      fullWidth
      maxWidth="sm"
    >
      <DialogTitle>Change status{to ? ` to ${STATUS_LABELS[to]}` : ''}</DialogTitle>
      <DialogContent>
        <Stack spacing={2}>
          {error && <Alert severity="error">{error}</Alert>}
          {to && (
            <DialogContentText
              component="div"
              sx={{ display: 'flex', gap: 1, alignItems: 'center' }}
            >
              <StatusChip status={from} /> → <StatusChip status={to} />
            </DialogContentText>
          )}
          {to === 'CLOSED' && (
            <Alert severity="info">A closed ticket is read-only until it is reopened.</Alert>
          )}
          <TextField
            label="Comment (optional, posted as a public reply)"
            multiline
            minRows={3}
            fullWidth
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            error={tooLong}
            helperText={tooLong ? 'Comments are limited to 5,000 characters' : undefined}
          />
        </Stack>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose} disabled={loading}>
          Cancel
        </Button>
        <Button
          variant="contained"
          disabled={loading || tooLong}
          onClick={() => onConfirm(comment.trim() ? comment : undefined)}
        >
          {loading ? 'Updating…' : 'Change status'}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
