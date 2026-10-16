import { zodResolver } from '@hookform/resolvers/zod';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import {
  Alert,
  Box,
  Button,
  Chip,
  Divider,
  Link,
  Stack,
  TextField,
  Typography,
} from '@mui/material';
import { useState } from 'react';
import { useForm } from 'react-hook-form';
import { Navigate, Link as RouterLink, useNavigate, useSearchParams } from 'react-router-dom';
import { applyFieldErrors, hasCode, problemMessage } from '../api/problem';
import { AuthLayout } from './AuthLayout';
import { safeNext } from './safeNext';
import { loginSchema, type LoginForm } from './schemas';
import { useAuth } from './useAuth';

const DEMO_PASSWORD = 'Password123!';
const DEMO_ACCOUNTS = [
  { label: 'Admin', email: 'admin@acme.test' },
  { label: 'Agent', email: 'agent1@acme.test' },
  { label: 'Customer', email: 'customer1@acme.test' },
];

export function LoginPage() {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const next = safeNext(searchParams.get('next'));
  const [formError, setFormError] = useState<string | null>(null);

  const {
    register,
    handleSubmit,
    setError,
    setValue,
    formState: { errors, isSubmitting },
  } = useForm<LoginForm>({
    resolver: zodResolver(loginSchema),
    defaultValues: { organizationSlug: '', email: '', password: '' },
  });

  if (isAuthenticated) return <Navigate to={next} replace />;

  const onSubmit = handleSubmit(async (values) => {
    setFormError(null);
    try {
      await login({
        organizationSlug: values.organizationSlug.trim().toLowerCase(),
        email: values.email.trim(),
        password: values.password,
      });
      navigate(next, { replace: true });
    } catch (err) {
      if (hasCode(err, 'INVALID_CREDENTIALS') || hasCode(err, 'UNAUTHENTICATED')) {
        setFormError('Invalid organization, email or password.');
        return;
      }
      const { mapped } = applyFieldErrors(err, setError, ['organizationSlug', 'email', 'password']);
      if (mapped === 0) setFormError(problemMessage(err));
    }
  });

  const fillDemo = (org: string, email: string) => {
    const opts = { shouldValidate: true, shouldDirty: true } as const;
    setValue('organizationSlug', org, opts);
    setValue('email', email.replace('@acme.', `@${org}.`), opts);
    setValue('password', DEMO_PASSWORD, opts);
  };

  return (
    <AuthLayout title="Sign in" subtitle="Welcome back. Sign in to your organization's helpdesk.">
      <Box component="form" noValidate onSubmit={onSubmit}>
        <Stack spacing={2}>
          {formError && <Alert severity="error">{formError}</Alert>}
          <TextField
            label="Organization"
            placeholder="e.g. acme"
            autoFocus
            autoComplete="organization"
            autoCapitalize="none"
            {...register('organizationSlug')}
            error={Boolean(errors.organizationSlug)}
            helperText={errors.organizationSlug?.message ?? 'Your organization’s slug'}
          />
          <TextField
            label="Email"
            type="email"
            autoComplete="username"
            {...register('email')}
            error={Boolean(errors.email)}
            helperText={errors.email?.message}
          />
          <TextField
            label="Password"
            type="password"
            autoComplete="current-password"
            {...register('password')}
            error={Boolean(errors.password)}
            helperText={errors.password?.message}
          />
          <Button type="submit" variant="contained" size="large" disabled={isSubmitting}>
            {isSubmitting ? 'Signing in…' : 'Sign in'}
          </Button>
          <Typography variant="body2" color="text.secondary" textAlign="center">
            New here?{' '}
            <Link component={RouterLink} to="/register">
              Register your organization
            </Link>
          </Typography>
        </Stack>
      </Box>

      <Divider sx={{ my: 3 }} />

      <Alert
        severity="info"
        icon={<InfoOutlinedIcon />}
        sx={{ '& .MuiAlert-message': { width: '100%' } }}
      >
        <Typography variant="subtitle2" gutterBottom>
          Demo accounts
        </Typography>
        <Typography variant="body2">
          Organizations <code>acme</code> and <code>globex</code> · password{' '}
          <code>{DEMO_PASSWORD}</code>
        </Typography>
        <Box component="ul" sx={{ pl: 2.5, my: 1, '& li': { fontSize: 14 } }}>
          {DEMO_ACCOUNTS.map((a) => (
            <li key={a.email}>
              {a.label}: <code>{a.email}</code>
            </li>
          ))}
        </Box>
        <Typography variant="caption" color="text.secondary">
          For globex use the same names with <code>@globex.test</code>. Click to fill:
        </Typography>
        <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap" sx={{ mt: 1 }}>
          {DEMO_ACCOUNTS.map((a) => (
            <Chip
              key={a.email}
              size="small"
              label={`acme · ${a.label}`}
              onClick={() => fillDemo('acme', a.email)}
              clickable
            />
          ))}
          <Chip
            size="small"
            label="globex · Admin"
            onClick={() => fillDemo('globex', 'admin@acme.test')}
            clickable
          />
        </Stack>
      </Alert>
    </AuthLayout>
  );
}
