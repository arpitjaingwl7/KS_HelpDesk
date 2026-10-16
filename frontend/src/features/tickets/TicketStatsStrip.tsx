import { Box, Paper, Skeleton, Typography } from '@mui/material';
import type { TicketStatus } from '../../api/types';
import { STATUS_COLORS, STATUS_LABELS } from '../../utils/labels';
import { useTicketStats } from './api';

const SHOWN: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'ON_HOLD', 'RESOLVED'];

/** Small summary strip shown to staff above the ticket list (GET /tickets/stats). */
export function TicketStatsStrip() {
  const { data, isPending, isError } = useTicketStats(true);
  if (isError) return null; // purely informational – never block the list

  const tiles: { label: string; value: number | undefined; color: string }[] = [
    { label: 'Total', value: data?.total, color: '#0f172a' },
    { label: 'Unassigned', value: data?.unassigned, color: '#dc2626' },
    ...SHOWN.map((s) => ({
      label: STATUS_LABELS[s],
      value: data?.byStatus[s] ?? 0,
      color: STATUS_COLORS[s],
    })),
  ];

  return (
    <Box
      aria-label="Ticket summary"
      sx={{
        display: 'grid',
        gap: 1.5,
        mb: 2,
        gridTemplateColumns: { xs: 'repeat(2, 1fr)', sm: 'repeat(3, 1fr)', md: 'repeat(6, 1fr)' },
      }}
    >
      {tiles.map((tile) => (
        <Paper
          key={tile.label}
          variant="outlined"
          sx={{ px: 2, py: 1.5, borderLeft: `3px solid ${tile.color}` }}
        >
          <Typography
            variant="caption"
            color="text.secondary"
            sx={{ textTransform: 'uppercase', letterSpacing: 0.5 }}
          >
            {tile.label}
          </Typography>
          <Typography variant="h5" sx={{ fontWeight: 700 }}>
            {isPending ? <Skeleton width={40} /> : (tile.value ?? 0).toLocaleString()}
          </Typography>
        </Paper>
      ))}
    </Box>
  );
}
