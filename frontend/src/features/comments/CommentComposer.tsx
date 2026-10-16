import LockIcon from '@mui/icons-material/Lock';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import PublicIcon from '@mui/icons-material/Public';
import SendIcon from '@mui/icons-material/Send';
import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Box,
  Button,
  Paper,
  Stack,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { applyFieldErrors, isGloballyHandled, problemMessage } from '../../api/problem';
import type { CommentVisibility } from '../../api/types';
import { useAddComment } from './api';
import { INTERNAL_NOTE_BG, INTERNAL_NOTE_BORDER } from './CommentList';

const commentSchema = z.object({
  body: z
    .string()
    .refine((v) => v.trim().length > 0, 'Write something first')
    .pipe(z.string().max(5000, 'Comments are limited to 5,000 characters')),
});
type CommentForm = z.infer<typeof commentSchema>;

interface CommentComposerProps {
  ticketId: string;
  /** From TicketDetail.permissions.canComment (false when the ticket is CLOSED). */
  canComment: boolean;
  /** From TicketDetail.permissions.canAddInternalNote (staff only). */
  canAddInternalNote: boolean;
}

export function CommentComposer({
  ticketId,
  canComment,
  canAddInternalNote,
}: CommentComposerProps) {
  const addComment = useAddComment(ticketId);
  const [visibility, setVisibility] = useState<CommentVisibility>('PUBLIC');
  const [error, setErrorMessage] = useState<string | null>(null);
  const {
    register,
    handleSubmit,
    reset,
    setError,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<CommentForm>({ resolver: zodResolver(commentSchema), defaultValues: { body: '' } });

  if (!canComment) {
    return (
      <Alert severity="info" icon={<LockOutlinedIcon />}>
        This ticket is closed. Reopen it to add a reply.
      </Alert>
    );
  }

  const internal = canAddInternalNote && visibility === 'INTERNAL';
  const length = watch('body').length;

  const onSubmit = handleSubmit(async ({ body }) => {
    setErrorMessage(null);
    try {
      await addComment.mutateAsync({
        body,
        visibility: canAddInternalNote ? visibility : 'PUBLIC',
      });
      reset({ body: '' });
    } catch (err) {
      if (isGloballyHandled(err)) return;
      const { mapped } = applyFieldErrors(err, setError, ['body']);
      if (mapped === 0) setErrorMessage(problemMessage(err));
    }
  });

  return (
    <Paper
      variant="outlined"
      component="form"
      noValidate
      onSubmit={onSubmit}
      aria-label="Reply"
      sx={{
        p: 2,
        bgcolor: internal ? INTERNAL_NOTE_BG : 'background.paper',
        borderColor: internal ? INTERNAL_NOTE_BORDER : 'divider',
        transition: 'background-color 150ms',
      }}
    >
      {error && (
        <Alert severity="error" sx={{ mb: 1.5 }} onClose={() => setErrorMessage(null)}>
          {error}
        </Alert>
      )}
      <TextField
        fullWidth
        multiline
        minRows={3}
        maxRows={14}
        label={internal ? 'Internal note (staff only)' : 'Write a reply…'}
        {...register('body')}
        error={Boolean(errors.body)}
        helperText={
          errors.body?.message ?? `${length.toLocaleString()} / 5,000 · Ctrl+Enter to send`
        }
        onKeyDown={(e) => {
          if (e.key === 'Enter' && (e.ctrlKey || e.metaKey)) {
            e.preventDefault();
            void onSubmit();
          }
        }}
        slotProps={{ htmlInput: { maxLength: 5000 } }}
        sx={{ '& .MuiInputBase-root': { bgcolor: 'background.paper' } }}
      />
      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        alignItems={{ xs: 'stretch', sm: 'center' }}
        justifyContent="space-between"
        sx={{ mt: 1.5 }}
      >
        {canAddInternalNote ? (
          <ToggleButtonGroup
            exclusive
            size="small"
            value={visibility}
            onChange={(_e, v: CommentVisibility | null) => v && setVisibility(v)}
            aria-label="Comment visibility"
          >
            <ToggleButton value="PUBLIC" aria-label="Public reply">
              <PublicIcon fontSize="small" sx={{ mr: 0.75 }} />
              Public reply
            </ToggleButton>
            <ToggleButton value="INTERNAL" aria-label="Internal note">
              <LockIcon fontSize="small" sx={{ mr: 0.75 }} />
              Internal note
            </ToggleButton>
          </ToggleButtonGroup>
        ) : (
          <Box />
        )}
        <Stack direction="row" spacing={1.5} alignItems="center" justifyContent="flex-end">
          {internal && (
            <Typography variant="caption" sx={{ color: '#7a5600' }}>
              Customers won’t see this note
            </Typography>
          )}
          <Button type="submit" variant="contained" endIcon={<SendIcon />} disabled={isSubmitting}>
            {isSubmitting ? 'Sending…' : internal ? 'Add note' : 'Send'}
          </Button>
        </Stack>
      </Stack>
    </Paper>
  );
}
