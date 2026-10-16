import { Box, Chip, type ChipProps } from '@mui/material';
import type { TicketStatus } from '../../api/types';
import { STATUS_COLORS, STATUS_LABELS } from '../../utils/labels';

interface StatusChipProps extends Omit<ChipProps, 'label' | 'color'> {
  status: TicketStatus;
}

/** Colour + dot + text, so status is never conveyed by colour alone. */
export function StatusChip({ status, size = 'small', sx, ...rest }: StatusChipProps) {
  const color = STATUS_COLORS[status];
  return (
    <Chip
      size={size}
      label={STATUS_LABELS[status]}
      icon={
        <Box
          component="span"
          aria-hidden
          sx={{ width: 8, height: 8, borderRadius: '50%', bgcolor: color, ml: '8px !important' }}
        />
      }
      sx={{
        fontWeight: 600,
        color,
        bgcolor: `${color}14`,
        border: `1px solid ${color}33`,
        ...sx,
      }}
      {...rest}
    />
  );
}
