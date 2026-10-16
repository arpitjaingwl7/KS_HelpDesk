import HeadsetMicOutlinedIcon from '@mui/icons-material/HeadsetMicOutlined';
import { Avatar, Box, Paper, Stack, Typography } from '@mui/material';
import type { ReactNode } from 'react';

export function AuthLayout({
  title,
  subtitle,
  children,
}: {
  title: string;
  subtitle?: string;
  children: ReactNode;
}) {
  return (
    <Box
      component="main"
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        px: 2,
        py: 6,
        background: (t) =>
          `radial-gradient(1200px 600px at 10% -10%, ${t.palette.primary.light}33, transparent),
           radial-gradient(900px 500px at 110% 110%, ${t.palette.secondary.light}2e, transparent),
           ${t.palette.background.default}`,
      }}
    >
      <Box sx={{ width: '100%', maxWidth: 440 }}>
        <Stack
          direction="row"
          spacing={1.5}
          alignItems="center"
          justifyContent="center"
          sx={{ mb: 3 }}
        >
          <Avatar variant="rounded" sx={{ bgcolor: 'primary.main', width: 40, height: 40 }}>
            <HeadsetMicOutlinedIcon />
          </Avatar>
          <Typography variant="h5" sx={{ fontWeight: 800 }}>
            Helpdesk
          </Typography>
        </Stack>
        <Paper
          elevation={0}
          variant="outlined"
          sx={{ p: { xs: 3, sm: 4 }, boxShadow: '0 10px 40px rgba(15,23,42,0.08)' }}
        >
          <Typography variant="h5" component="h1" sx={{ fontWeight: 700 }}>
            {title}
          </Typography>
          {subtitle && (
            <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5, mb: 3 }}>
              {subtitle}
            </Typography>
          )}
          {children}
        </Paper>
      </Box>
    </Box>
  );
}
