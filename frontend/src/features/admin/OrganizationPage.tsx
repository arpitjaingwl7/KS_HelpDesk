import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Box,
  Button,
  InputAdornment,
  Paper,
  Skeleton,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import LockOutlinedIcon from '@mui/icons-material/LockOutlined';
import { useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { applyFieldErrors, isGloballyHandled, problemMessage } from '../../api/problem';
import { useAuth } from '../../auth/useAuth';
import { ErrorAlert } from '../../components/ErrorAlert';
import { PageHeader } from '../../components/PageHeader';
import { useToast } from '../../components/useToast';
import { formatDateTime } from '../../utils/formatDate';
import { useOrganization, useUpdateOrganization } from './api';
import { organizationSchema, type OrganizationForm } from './schemas';

export function OrganizationPage() {
  const orgQuery = useOrganization();
  const update = useUpdateOrganization();
  const { user, updateUser } = useAuth();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    reset,
    setError,
    formState: { errors, isSubmitting, isDirty },
  } = useForm<OrganizationForm>({
    resolver: zodResolver(organizationSchema),
    defaultValues: { name: '' },
  });

  useEffect(() => {
    if (orgQuery.data) reset({ name: orgQuery.data.name });
  }, [orgQuery.data, reset]);

  const onSubmit = handleSubmit(async ({ name }) => {
    setFormError(null);
    try {
      const org = await update.mutateAsync({ name: name.trim() });
      if (user) updateUser({ organization: { ...user.organization, name: org.name } });
      reset({ name: org.name });
      toast.success('Organization renamed');
    } catch (err) {
      if (isGloballyHandled(err)) return;
      const { mapped } = applyFieldErrors(err, setError, ['name']);
      if (mapped === 0) setFormError(problemMessage(err));
    }
  });

  return (
    <Box sx={{ maxWidth: 720 }}>
      <PageHeader title="Organization" subtitle="Settings for your helpdesk workspace" />
      <Paper variant="outlined" sx={{ p: { xs: 2, sm: 3 } }}>
        {orgQuery.isPending ? (
          <Stack spacing={2}>
            <Skeleton variant="rounded" height={56} />
            <Skeleton variant="rounded" height={56} />
          </Stack>
        ) : orgQuery.isError ? (
          <ErrorAlert
            error={orgQuery.error}
            title="Couldn't load organization"
            onRetry={() => void orgQuery.refetch()}
          />
        ) : (
          <Box component="form" noValidate onSubmit={onSubmit}>
            <Stack spacing={2.5}>
              {formError && <Alert severity="error">{formError}</Alert>}
              <TextField
                label="Organization name"
                required
                fullWidth
                {...register('name')}
                error={Boolean(errors.name)}
                helperText={
                  errors.name?.message ??
                  '2–120 characters. Shown in the header for everyone in your org.'
                }
              />
              <TextField
                label="Slug"
                fullWidth
                value={orgQuery.data.slug}
                disabled
                helperText="Used to sign in. The slug can't be changed."
                slotProps={{
                  input: {
                    readOnly: true,
                    endAdornment: (
                      <InputAdornment position="end">
                        <LockOutlinedIcon fontSize="small" />
                      </InputAdornment>
                    ),
                  },
                }}
              />
              <Typography variant="body2" color="text.secondary">
                Created {formatDateTime(orgQuery.data.createdAt)}
              </Typography>
              <Stack direction="row" spacing={1} justifyContent="flex-end">
                <Button
                  color="inherit"
                  disabled={!isDirty || isSubmitting}
                  onClick={() => reset({ name: orgQuery.data.name })}
                >
                  Discard
                </Button>
                <Button type="submit" variant="contained" disabled={!isDirty || isSubmitting}>
                  {isSubmitting ? 'Saving…' : 'Save'}
                </Button>
              </Stack>
            </Stack>
          </Box>
        )}
      </Paper>
    </Box>
  );
}
