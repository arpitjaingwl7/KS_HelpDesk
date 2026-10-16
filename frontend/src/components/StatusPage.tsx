import { Box, Button, Typography } from '@mui/material';
import type { ReactNode } from 'react';
import { Link as RouterLink } from 'react-router-dom';

interface StatusPageProps {
  code?: string;
  title: string;
  description: ReactNode;
  icon?: ReactNode;
  actionLabel?: string;
  actionTo?: string;
}

export function StatusPage({
  code,
  title,
  description,
  icon,
  actionLabel = 'Back to tickets',
  actionTo = '/tickets',
}: StatusPageProps) {
  return (
    <Box
      sx={{
        minHeight: '50vh',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        textAlign: 'center',
        gap: 2,
        py: 8,
        px: 2,
      }}
    >
      {icon && <Box sx={{ fontSize: 56, color: 'text.disabled', display: 'flex' }}>{icon}</Box>}
      {code && (
        <Typography variant="h2" component="p" color="primary" sx={{ fontWeight: 800 }}>
          {code}
        </Typography>
      )}
      <Typography variant="h5" component="h1">
        {title}
      </Typography>
      <Typography color="text.secondary" sx={{ maxWidth: 460 }}>
        {description}
      </Typography>
      <Button component={RouterLink} to={actionTo} variant="contained">
        {actionLabel}
      </Button>
    </Box>
  );
}
