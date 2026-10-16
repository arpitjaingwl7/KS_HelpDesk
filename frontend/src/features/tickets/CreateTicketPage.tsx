import ArrowBackIcon from '@mui/icons-material/ArrowBack';
import { zodResolver } from '@hookform/resolvers/zod';
import { Alert, Box, Button, MenuItem, Paper, Stack, TextField } from '@mui/material';
import { useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { applyFieldErrors, hasCode, isGloballyHandled, problemMessage } from '../../api/problem';
import { TICKET_PRIORITIES, type UserOption } from '../../api/types';
import { useCurrentUser } from '../../auth/useAuth';
import { PageHeader } from '../../components/PageHeader';
import { useToast } from '../../components/useToast';
import { isStaff, PRIORITY_LABELS } from '../../utils/labels';
import { useCreateTicket } from './api';
import { CustomerAutocomplete } from './CustomerAutocomplete';
import { createTicketSchema, type CreateTicketForm } from './schemas';

const FIELDS = ['subject', 'description', 'priority', 'requesterId'] as const;

export function CreateTicketPage() {
  const user = useCurrentUser();
  const staff = isStaff(user.role);
  const navigate = useNavigate();
  const toast = useToast();
  const createTicket = useCreateTicket();
  const [requester, setRequester] = useState<UserOption | null>(null);
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    control,
    handleSubmit,
    setError,
    setValue,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CreateTicketForm>({
    resolver: zodResolver(createTicketSchema),
    defaultValues: { subject: '', description: '', priority: 'MEDIUM', requesterId: null },
    mode: 'onTouched',
  });

  const description = watch('description');

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const ticket = await createTicket.mutateAsync({
        subject: values.subject.trim(),
        description: values.description,
        priority: values.priority,
        ...(staff && values.requesterId ? { requesterId: values.requesterId } : {}),
      });
      toast.success(`Ticket #${ticket.number} created`);
      navigate(`/tickets/${ticket.id}`, { replace: true });
    } catch (err) {
      if (isGloballyHandled(err)) return;
      if (hasCode(err, 'INVALID_REQUESTER')) {
        setError('requesterId', { type: 'server', message: problemMessage(err) });
        return;
      }
      const { mapped, unmapped } = applyFieldErrors(err, setError, FIELDS);
      if (mapped === 0 || unmapped.length) setFormError(unmapped.join('\n') || problemMessage(err));
    }
  });

  return (
    <Box sx={{ maxWidth: 820, mx: 'auto' }}>
      <Button component={RouterLink} to="/tickets" startIcon={<ArrowBackIcon />} sx={{ mb: 1 }}>
        Back to tickets
      </Button>
      <PageHeader
        title="New ticket"
        subtitle="Describe the problem. The more detail you give, the faster we can help."
      />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
        <Box component="form" noValidate onSubmit={onSubmit}>
          <Stack spacing={2.5}>
            {formError && (
              <Alert severity="error" sx={{ whiteSpace: 'pre-line' }}>
                {formError}
              </Alert>
            )}

            {staff && (
              <CustomerAutocomplete
                value={requester}
                onChange={(v) => {
                  setRequester(v);
                  setValue('requesterId', v?.id ?? null, { shouldValidate: true });
                }}
                error={errors.requesterId?.message}
              />
            )}

            <TextField
              label="Subject"
              required
              autoFocus
              fullWidth
              {...register('subject')}
              error={Boolean(errors.subject)}
              helperText={errors.subject?.message ?? 'A short summary, 3–200 characters'}
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
              helperText={
                errors.description?.message ?? `${description.length.toLocaleString()} / 10,000`
              }
              slotProps={{ htmlInput: { maxLength: 10_000 } }}
            />

            <Controller
              control={control}
              name="priority"
              render={({ field }) => (
                <TextField
                  select
                  label="Priority"
                  sx={{ maxWidth: 240 }}
                  {...field}
                  error={Boolean(errors.priority)}
                  helperText={
                    errors.priority?.message ?? (staff ? undefined : 'Our team may adjust this')
                  }
                >
                  {TICKET_PRIORITIES.map((p) => (
                    <MenuItem key={p} value={p}>
                      {PRIORITY_LABELS[p]}
                    </MenuItem>
                  ))}
                </TextField>
              )}
            />

            <Stack direction="row" spacing={1} justifyContent="flex-end">
              <Button component={RouterLink} to="/tickets" color="inherit">
                Cancel
              </Button>
              <Button type="submit" variant="contained" disabled={isSubmitting}>
                {isSubmitting ? 'Creating…' : 'Create ticket'}
              </Button>
            </Stack>
          </Stack>
        </Box>
      </Paper>
    </Box>
  );
}
