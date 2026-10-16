import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  Stack,
  TextField,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyFieldErrors, hasCode, isGloballyHandled, problemMessage } from '../../api/problem';
import type { TicketDetail } from '../../api/types';
import { useToast } from '../../components/useToast';
import { useUpdateTicket } from './api';
import { editTicketSchema, type EditTicketForm } from './schemas';

interface EditTicketDialogProps {
  open: boolean;
  ticket: TicketDetail;
  onClose(): void;
  /** Called on 409 CONCURRENT_MODIFICATION so the page can show its Reload banner. */
  onConflict(): void;
  /** Refetches the ticket (new version) without discarding the user's edits. */
  onReload(): Promise<void>;
}

/**
 * Edits subject/description. Sends the `version` of the ticket currently in the cache,
 * so after the user hits "Reload" their edits are kept and re-sent against the new version.
 */
export function EditTicketDialog({
  open,
  ticket,
  onClose,
  onConflict,
  onReload,
}: EditTicketDialogProps) {
  const update = useUpdateTicket(ticket.id);
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);
  const [conflict, setConflict] = useState(false);
  const [reloading, setReloading] = useState(false);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<EditTicketForm>({
    resolver: zodResolver(editTicketSchema),
    defaultValues: { subject: ticket.subject, description: ticket.description },
  });

  // Fresh form every time the dialog opens.
  useEffect(() => {
    if (open) {
      reset({ subject: ticket.subject, description: ticket.description });
      setFormError(null);
      setConflict(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps -- only on open
  }, [open]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    const body: { subject?: string; description?: string; version: number } = {
      version: ticket.version,
    };
    if (values.subject.trim() !== ticket.subject) body.subject = values.subject.trim();
    if (values.description !== ticket.description) body.description = values.description;
    if (body.subject === undefined && body.description === undefined) {
      onClose();
      return;
    }
    try {
      await update.mutateAsync(body);
      toast.success('Ticket updated');
      onClose();
    } catch (err) {
      if (isGloballyHandled(err)) return;
      if (hasCode(err, 'CONCURRENT_MODIFICATION')) {
        onConflict();
        setConflict(true);
        return;
      }
      const { mapped } = applyFieldErrors(err, setError, ['subject', 'description']);
      if (mapped === 0) setFormError(problemMessage(err));
    }
  });

  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : onClose} fullWidth maxWidth="md">
      <form noValidate onSubmit={onSubmit}>
        <DialogTitle>Edit ticket #{ticket.number}</DialogTitle>
        <DialogContent>
          <Stack spacing={2.5} sx={{ pt: 1 }}>
            {conflict && (
              <Alert
                severity="warning"
                action={
                  <Button
                    color="inherit"
                    size="small"
                    variant="outlined"
                    disabled={reloading}
                    onClick={async () => {
                      setReloading(true);
                      try {
                        await onReload();
                        setConflict(false);
                      } finally {
                        setReloading(false);
                      }
                    }}
                  >
                    {reloading ? 'Reloading…' : 'Reload'}
                  </Button>
                }
              >
                This ticket was changed by someone else. Reload to get the latest version, then save
                again — your edits are kept.
              </Alert>
            )}
            {formError && <Alert severity="error">{formError}</Alert>}
            <TextField
              label="Subject"
              required
              fullWidth
              autoFocus
              {...register('subject')}
              error={Boolean(errors.subject)}
              helperText={errors.subject?.message}
              slotProps={{ htmlInput: { maxLength: 200 } }}
            />
            <TextField
              label="Description"
              required
              fullWidth
              multiline
              minRows={6}
              maxRows={20}
              {...register('description')}
              error={Boolean(errors.description)}
              helperText={errors.description?.message}
              slotProps={{ htmlInput: { maxLength: 10_000 } }}
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isSubmitting || !isDirty || conflict}>
            {isSubmitting ? 'Saving…' : 'Save changes'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
