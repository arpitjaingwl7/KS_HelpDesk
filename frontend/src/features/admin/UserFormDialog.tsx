import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Button,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  FormControlLabel,
  MenuItem,
  Skeleton,
  Stack,
  Switch,
  TextField,
  Typography,
} from '@mui/material';
import { useEffect, useState } from 'react';
import { Controller, useForm } from 'react-hook-form';
import { applyFieldErrors, getProblem, isGloballyHandled, problemMessage } from '../../api/problem';
import { ROLES, type UserDetail } from '../../api/types';
import { useAuth } from '../../auth/useAuth';
import { ErrorAlert } from '../../components/ErrorAlert';
import { useToast } from '../../components/useToast';
import { formatDateTime } from '../../utils/formatDate';
import { ROLE_LABELS } from '../../utils/labels';
import { useCreateUser, useUpdateUser, useUser } from './api';
import { createUserSchema, editUserSchema, type UserForm } from './schemas';

interface UserFormDialogProps {
  open: boolean;
  /** `null` → create a new user. */
  userId: string | null;
  onClose(): void;
}

const ROLE_HELP: Record<(typeof ROLES)[number], string> = {
  ORG_ADMIN: 'Full control: users, settings and all tickets',
  AGENT: 'Works on all tickets: assign, comment, internal notes',
  CUSTOMER: 'Raises tickets and sees only their own',
};

const EMPTY: UserForm = { fullName: '', email: '', password: '', role: 'AGENT', active: true };

export function UserFormDialog({ open, userId, onClose }: UserFormDialogProps) {
  const isEdit = userId !== null;
  const { user: me, updateUser: updateMe } = useAuth();
  const toast = useToast();
  const userQuery = useUser(open ? userId : null);
  const createUser = useCreateUser();
  const updateUser = useUpdateUser();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    control,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting },
  } = useForm<UserForm>({
    resolver: zodResolver(isEdit ? editUserSchema : createUserSchema),
    defaultValues: EMPTY,
  });

  const loaded: UserDetail | undefined = isEdit ? userQuery.data : undefined;

  useEffect(() => {
    if (!open) return;
    setFormError(null);
    if (!isEdit) reset(EMPTY);
    else if (loaded)
      reset({
        fullName: loaded.fullName,
        email: loaded.email,
        password: '',
        role: loaded.role,
        active: loaded.active,
      });
  }, [open, isEdit, loaded, reset]);

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      if (!isEdit) {
        const created = await createUser.mutateAsync({
          fullName: values.fullName.trim(),
          email: values.email.trim(),
          password: values.password,
          role: values.role,
        });
        toast.success(`${created.fullName} was added`);
      } else {
        if (!loaded) return;
        const unchanged =
          values.fullName.trim() === loaded.fullName &&
          values.role === loaded.role &&
          values.active === loaded.active;
        if (unchanged) {
          onClose();
          return;
        }
        const updated = await updateUser.mutateAsync({
          id: loaded.id,
          body: {
            version: loaded.version,
            ...(values.fullName.trim() !== loaded.fullName
              ? { fullName: values.fullName.trim() }
              : {}),
            ...(values.role !== loaded.role ? { role: values.role } : {}),
            ...(values.active !== loaded.active ? { active: values.active } : {}),
          },
        });
        if (me && updated.id === me.id)
          updateMe({ fullName: updated.fullName, role: updated.role });
        toast.success('User updated');
      }
      onClose();
    } catch (err) {
      if (isGloballyHandled(err)) return;
      const problem = getProblem(err);
      if (problem?.code === 'EMAIL_ALREADY_EXISTS') {
        setError('email', {
          type: 'server',
          message: problem.detail ?? 'This email is already in use',
        });
        return;
      }
      const { mapped, unmapped } = applyFieldErrors(err, setError, [
        'fullName',
        'email',
        'password',
        'role',
        'active',
      ]);
      if (problem?.code === 'CONCURRENT_MODIFICATION') {
        setFormError(
          'This user was changed by someone else. Close and reopen the dialog to load the latest version.',
        );
        void userQuery.refetch();
      } else if (mapped === 0 || unmapped.length) {
        // 409 LAST_ADMIN / SELF_DEACTIVATION etc.: show the server's detail.
        setFormError(unmapped.length ? unmapped.join('\n') : problemMessage(err));
      }
    }
  });

  return (
    <Dialog open={open} onClose={isSubmitting ? undefined : onClose} fullWidth maxWidth="sm">
      <form noValidate onSubmit={onSubmit}>
        <DialogTitle>{isEdit ? 'Edit user' : 'New user'}</DialogTitle>
        <DialogContent>
          {isEdit && userQuery.isPending ? (
            <Stack spacing={2} sx={{ pt: 1 }}>
              {[0, 1, 2].map((i) => (
                <Skeleton key={i} variant="rounded" height={56} />
              ))}
            </Stack>
          ) : isEdit && userQuery.isError ? (
            <ErrorAlert error={userQuery.error} title="Couldn't load user" />
          ) : (
            <Stack spacing={2.5} sx={{ pt: 1 }}>
              {formError && (
                <Alert severity="error" sx={{ whiteSpace: 'pre-line' }}>
                  {formError}
                </Alert>
              )}
              <TextField
                label="Full name"
                required
                autoFocus
                fullWidth
                {...register('fullName')}
                error={Boolean(errors.fullName)}
                helperText={errors.fullName?.message}
              />
              <TextField
                label="Email"
                type="email"
                required={!isEdit}
                fullWidth
                {...register('email')}
                disabled={isEdit}
                error={Boolean(errors.email)}
                helperText={
                  errors.email?.message ?? (isEdit ? 'Email cannot be changed' : undefined)
                }
                autoComplete="off"
              />
              {!isEdit && (
                <TextField
                  label="Initial password"
                  type="password"
                  required
                  fullWidth
                  {...register('password')}
                  error={Boolean(errors.password)}
                  helperText={
                    errors.password?.message ?? '8–72 characters. Share it with the user securely.'
                  }
                  autoComplete="new-password"
                />
              )}
              <Controller
                control={control}
                name="role"
                render={({ field }) => (
                  <TextField
                    select
                    label="Role"
                    fullWidth
                    {...field}
                    error={Boolean(errors.role)}
                    helperText={errors.role?.message ?? ROLE_HELP[field.value]}
                  >
                    {ROLES.map((r) => (
                      <MenuItem key={r} value={r}>
                        {ROLE_LABELS[r]}
                      </MenuItem>
                    ))}
                  </TextField>
                )}
              />
              {isEdit && (
                <Controller
                  control={control}
                  name="active"
                  render={({ field }) => (
                    <FormControlLabel
                      control={
                        <Switch
                          checked={field.value}
                          onChange={(e) => field.onChange(e.target.checked)}
                        />
                      }
                      label={
                        <span>
                          Active
                          <Typography
                            variant="caption"
                            color="text.secondary"
                            component="span"
                            sx={{ display: 'block' }}
                          >
                            Inactive users cannot sign in. Their tickets stay assigned.
                          </Typography>
                        </span>
                      }
                    />
                  )}
                />
              )}
              {loaded && (
                <Typography variant="caption" color="text.secondary">
                  Created {formatDateTime(loaded.createdAt)} · Last login{' '}
                  {formatDateTime(loaded.lastLoginAt, 'never')}
                </Typography>
              )}
            </Stack>
          )}
        </DialogContent>
        <DialogActions>
          <Button onClick={onClose} disabled={isSubmitting}>
            Cancel
          </Button>
          <Button type="submit" variant="contained" disabled={isSubmitting || (isEdit && !loaded)}>
            {isSubmitting ? 'Saving…' : isEdit ? 'Save changes' : 'Create user'}
          </Button>
        </DialogActions>
      </form>
    </Dialog>
  );
}
