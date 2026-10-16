import KeyboardArrowDownIcon from '@mui/icons-material/KeyboardArrowDown';
import KeyboardArrowUpIcon from '@mui/icons-material/KeyboardArrowUp';
import KeyboardDoubleArrowUpIcon from '@mui/icons-material/KeyboardDoubleArrowUp';
import RemoveIcon from '@mui/icons-material/Remove';
import { Chip, type ChipProps } from '@mui/material';
import type { ReactElement } from 'react';
import type { TicketPriority } from '../../api/types';
import { PRIORITY_LABELS } from '../../utils/labels';

const PRIORITY_STYLE: Record<TicketPriority, { color: string; icon: ReactElement }> = {
  LOW: { color: '#64748b', icon: <KeyboardArrowDownIcon /> },
  MEDIUM: { color: '#0284c7', icon: <RemoveIcon /> },
  HIGH: { color: '#ea580c', icon: <KeyboardArrowUpIcon /> },
  URGENT: { color: '#dc2626', icon: <KeyboardDoubleArrowUpIcon /> },
};

interface PriorityChipProps extends Omit<ChipProps, 'label' | 'color'> {
  priority: TicketPriority;
}

export function PriorityChip({ priority, size = 'small', sx, ...rest }: PriorityChipProps) {
  const { color, icon } = PRIORITY_STYLE[priority];
  return (
    <Chip
      size={size}
      variant="outlined"
      icon={icon}
      label={PRIORITY_LABELS[priority]}
      sx={{
        fontWeight: 600,
        color,
        borderColor: `${color}55`,
        '& .MuiChip-icon': { color },
        ...sx,
      }}
      {...rest}
    />
  );
}
