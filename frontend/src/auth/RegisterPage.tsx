import { zodResolver } from '@hookform/resolvers/zod';
import {
  Alert,
  Box,
  Button,
  Divider,
  InputAdornment,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, Link as RouterLink, useNavigate } from 'react-router-dom';
import { applyFieldErrors, getProblem, problemMessage } from '../api/problem';
import { useToast } from '../components/useToast';
import { AuthLayout } from './AuthLayout';
import { registerSchema, slugify, type RegisterForm } from './schemas';
import { useAuth } from './useAuth';

const FIELDS = [
  'organizationName',
  'organizationSlug',
  'adminFullName',
  'adminEmail',
  'adminPassword',
] as const;

export function RegisterPage() {
  const { register: registerOrg, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [formError, setFormError] = useState<string | null>(null);
  const slugTouched = useRef(false);

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<RegisterForm>({
    resolver: zodResolver(registerSchema),
    mode: 'onTouched',
    defaultValues: {
      organizationName: '',
      organizationSlug: '',
      adminFullName: '',
      adminEmail: '',
      adminPassword: '',
    },
  });

  if (isAuthenticated) return <Navigate to="/tickets" replace />;

  const nameField = register('organizationName');
  const slugField = register('organizationSlug');

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      const res = await registerOrg({
        organizationName: values.organizationName.trim(),
        organizationSlug: values.organizationSlug.trim(),
        adminFullName: values.adminFullName.trim(),
        adminEmail: values.adminEmail.trim(),
        adminPassword: values.adminPassword,
      });
      toast.success(`Welcome to Helpdesk, ${res.user.fullName}!`);
      navigate('/tickets', { replace: true });
    } catch (err) {
      const problem = getProblem(err);
      if (problem?.code === 'SLUG_ALREADY_EXISTS') {
        setError('organizationSlug', {
          type: 'server',
          message: problem.detail ?? 'This slug is already taken',
        });
        return;
      }
      const { mapped } = applyFieldErrors(err, setError, FIELDS);
      if (mapped === 0) setFormError(problemMessage(err));
    }
  });

  return (
    <AuthLayout
      title="Register your organization"
      subtitle="Create a workspace. You'll be its first admin."
    >
      <Box component="form" noValidate onSubmit={onSubmit}>
        <Stack spacing={2}>
          {formError && <Alert severity="error">{formError}</Alert>}
          <TextField
            label="Organization name"
            autoFocus
            {...nameField}
            onChange={(e) => {
              void nameField.onChange(e);
              if (!slugTouched.current) setValue('organizationSlug', slugify(e.target.value));
            }}
            error={Boolean(errors.organizationName)}
            helperText={errors.organizationName?.message}
          />
          <TextField
            label="Organization slug"
            {...slugField}
            onChange={(e) => {
              slugTouched.current = true;
              void slugField.onChange(e);
            }}
            error={Boolean(errors.organizationSlug)}
            helperText={
              errors.organizationSlug?.message ??
              'Used to sign in. Lowercase letters, digits and hyphens. Cannot be changed later.'
            }
            autoCapitalize="none"
            slotProps={{
              input: { startAdornment: <InputAdornment position="start">org/</InputAdornment> },
            }}
          />
          <Divider textAlign="left">
            <Typography variant="caption" color="text.secondary">
              Admin account
            </Typography>
          </Divider>
          <TextField
            label="Your full name"
            autoComplete="name"
            {...register('adminFullName')}
            error={Boolean(errors.adminFullName)}
            helperText={errors.adminFullName?.message}
          />
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            {...register('adminEmail')}
            error={Boolean(errors.adminEmail)}
            helperText={errors.adminEmail?.message}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="new-password"
            {...register('adminPassword')}
            error={Boolean(errors.adminPassword)}
            helperText={errors.adminPassword?.message ?? '8–72 characters'}
          />
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
            {isSubmitting ? 'Creating…' : 'Create organization'}
          </Button>
          <Typography variant="body2" color="text.secondary" textAlign="center">
            Already have an account?{' '}
            <Link component={RouterLink} to="/login">
              Sign in
            </Link>
          </Typography>
        </Stack>
      </Box>
    </AuthLayout>
  );
}
